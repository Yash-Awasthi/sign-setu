import com.android.build.api.artifact.SingleArtifact
import java.time.LocalDate
import java.util.Properties

// Local settings, untracked: -P properties win, then local.properties. Empty is fine and means offline only.
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
val gatewayUrl: String = providers.gradleProperty("gatewayUrl").orNull ?: local.getProperty("gatewayUrl", "")
// The client name sent to the assistant service; local.properties may set the one a service requires.
val assistUserAgent: String = local.getProperty("assistUserAgent", "SignSetu")
// The one switch for screenshots and screen recording: false while testing, true for the final release APK.
val secureScreen: Boolean = (providers.gradleProperty("secureScreen").orNull ?: local.getProperty("secureScreen", "false")).toBoolean()
// Debug builds only, for driving the app over adb: no app pinning and no officer fingerprint. Release ignores it.
val testUnlocked: Boolean = (providers.gradleProperty("testUnlocked").orNull ?: local.getProperty("testUnlocked", "false")).toBoolean()

// Read on every build, so a configuration cached on an earlier day does not carry that day's build date.
abstract class EpochDay : ValueSource<Long, ValueSourceParameters.None> {
    override fun obtain(): Long = LocalDate.now().toEpochDay()
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    // Code namespace differs from the application ID because `in` is a Kotlin keyword.
    namespace = "app.signsetu"
    compileSdk = 37

    defaultConfig {
        applicationId = "in.signsetu"
        minSdk = 29
        targetSdk = 37
        versionCode = 18
        versionName = "0.8.2"
        buildConfigField("long", "BUILD_EPOCH_DAY", "${providers.of(EpochDay::class.java) {}.get()}L")
        buildConfigField("boolean", "SECURE_SCREEN", "$secureScreen")
        buildConfigField("boolean", "TEST_UNLOCKED", "false")
        // The department's gateway: -PgatewayUrl=https://... or gatewayUrl in local.properties.
        buildConfigField("String", "GATEWAY_URL", "\"$gatewayUrl\"")
        buildConfigField("String", "ASSIST_URL", "\"https://api.groq.com/openai/v1/chat/completions\"")
        buildConfigField("String", "ASSIST_MODEL", "\"openai/gpt-oss-20b\"")
        buildConfigField("String", "ASSIST_UA", "\"$assistUserAgent\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The on-device assistant runs only on 64-bit phones; the x86_64 build serves the emulator.
        externalNativeBuild { cmake { abiFilters += listOf("arm64-v8a", "x86_64"); arguments += "-DCMAKE_BUILD_TYPE=Release" } }
    }

    ndkVersion = "30.0.16248370"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    // The officer picks the app language in Settings, so every language must ship in every install.
    bundle { language { enableSplit = false } }

    buildTypes {
        debug { buildConfigField("boolean", "TEST_UNLOCKED", "$testUnlocked") }
        release {
            // Universal 64-bit build by default (ARM phones, x86_64 emulators and tablets); -Pabi=arm64-v8a for phones only.
            ndk { abiFilters += (providers.gradleProperty("abi").orNull?.split(",") ?: listOf("arm64-v8a", "x86_64")) }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // the second screen links over bonded Bluetooth by default; "nearby" swaps in Google Nearby Connections for
    // departments whose phones have Play services. Only "standard" is the reference build that keeps every rule.
    flavorDimensions += "transport"
    productFlavors {
        create("standard") {
            dimension = "transport"
            isDefault = true
        }
        create("nearby") {
            dimension = "transport"
            applicationIdSuffix = ".nearby"
            versionNameSuffix = "-nearby"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Security checks fail the release lint gate.
    lint { error += listOf("HardcodedDebugMode", "SetJavaScriptEnabled", "ExportedService", "AllowBackup") }

    // Android calls made in plain JVM tests return defaults instead of throwing (timing marks, log lines).
    testOptions {
        unitTests.isReturnDefaultValues = true
        // SignCaptureTest compares the app's segmentation numbers with the Python evaluator's.
        unitTests.all { it.inputs.file("../tools/ml/eval_video.py") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Native libraries and models ship uncompressed, so they load straight from the APK at full precision.
    packaging { jniLibs { useLegacyPackaging = false } }

    androidResources {
        // The detection pipeline is never compressed, so every file it reads can be memory-mapped like the models.
        noCompress += listOf(
            "webp", "ogg", "mp4", "task", "bin",
            "_reader.json", "_spell.json", "_ctc.json", "sign_words.json", "asl_words.json", "sign_confusion.json", "intents.json", "names.txt",
        )
    }
}

// INTERNET is allowed only for the department gateway (OnlineAssistant). the build fails if any library brings a forbidden permission into the merged manifest.
// The nearby flavor needs scanning and, below Android 13, location for Nearby Connections; it keeps the rest.
androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val standard = variant.flavorName != "nearby"
        val check = tasks.register("check${name}Permissions") {
            inputs.file(manifest)
            doLast {
                val always = listOf(
                    "android.permission.RECORD_AUDIO",
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                )
                val discovery = listOf(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.BLUETOOTH_ADMIN",
                    "android.permission.BLUETOOTH_ADVERTISE",
                    "android.permission.NEARBY_WIFI_DEVICES",
                )
                val forbidden = if (standard) always + discovery else always
                val text = manifest.get().asFile.readText()
                val found = forbidden.filter { text.contains("\"$it\"") }
                if (found.isNotEmpty()) throw GradleException("Forbidden permissions in the merged manifest: $found")
            }
        }
        // Every task that packages the merged manifest, so neither an APK nor an AAB nor an install can skip the check.
        val packaging = setOf("assemble$name", "bundle$name", "install$name", "package$name", "package${name}Bundle")
        tasks.matching { it.name in packaging }.configureEach { dependsOn(check) }
    }
}

// every resolved version is pinned in gradle.lockfile; update with --write-locks.
dependencyLocking {
    lockAllConfigurations()
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.datastore.preferences)
    implementation(libs.media3.exoplayer)
    implementation(libs.pdfbox.android)
    constraints {
        // PdfBox-Android brings BouncyCastle 1.72, which has known critical vulnerabilities (OSV scan); 1.86 fixes them.
        implementation(libs.bouncycastle.prov)
        implementation(libs.bouncycastle.pkix)
        implementation(libs.bouncycastle.util)
        // CameraX brings Guava 33.3.1, whose deserialization can allocate without bound (GHSA-xxph-c9ww-hj94).
        implementation(libs.guava)
    }
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.compose)
    // The format 3 sign reader; MediaPipe bundles its own runtime privately.
    implementation(libs.litert)
    implementation(libs.mediapipe.tasks.vision) {
        // This backend uploads MediaPipe usage statistics and brings INTERNET; a stub destination replaces it.
        exclude(group = "com.google.android.datatransport", module = "transport-backend-cct")
        // Only ImageEmbedder uses this 20 MB on-device LLM runtime; the holistic landmarker never loads it.
        exclude(group = "com.google.ai.edge.litertlm", module = "litertlm-android")
    }
    // MediaPipe 1.1.1 is built on the lite runtime; its own 4.26.1 pin has CVE-2024-7254.
    implementation(libs.protobuf.javalite)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Play services only in the nearby flavor; the standard build has none.
    "nearbyImplementation"(libs.play.services.nearby)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4.accessibility)
}
