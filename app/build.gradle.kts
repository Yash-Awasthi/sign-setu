import com.android.build.api.artifact.SingleArtifact
import java.time.LocalDate

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
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("long", "BUILD_EPOCH_DAY", "${LocalDate.now().toEpochDay()}L")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Officers' phones are ARM; x86 libraries only serve the emulator in debug builds.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += listOf("webp", "ogg", "mp4")
    }
}

// SEC-30, SEC-42: the build fails if any library brings a forbidden permission into the merged manifest.
androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register("check${name}Permissions") {
            inputs.file(manifest)
            doLast {
                val forbidden = listOf(
                    "android.permission.INTERNET",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION",
                )
                val text = manifest.get().asFile.readText()
                val found = forbidden.filter { text.contains("\"$it\"") }
                if (found.isNotEmpty()) throw GradleException("Forbidden permissions in the merged manifest: $found")
            }
        }
        tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(check) }
    }
}

// SEC-50: every resolved version is pinned in gradle.lockfile; update with --write-locks.
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
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.biometric)
    // biometric 1.1.0 brings a fragment library too old for permission requests through ActivityResult.
    implementation(libs.androidx.fragment)
    implementation(libs.media3.exoplayer)
    implementation(libs.pdfbox.android)
    constraints {
        // PdfBox-Android brings BouncyCastle 1.72, which has known critical vulnerabilities (OSV scan); 1.86 fixes them.
        implementation(libs.bouncycastle.prov)
        implementation(libs.bouncycastle.pkix)
        implementation(libs.bouncycastle.util)
    }
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
