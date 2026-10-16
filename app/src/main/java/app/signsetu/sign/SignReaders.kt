package app.signsetu.sign

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/** Creates the bundled sign reader, or none when its model is missing or fails its check (D-25). */
object SignReaders {
    fun create(context: Context): SignReader? = runCatching {
        val assets = context.assets
        fun bytes(path: String) = assets.open("content/$path").use { it.readBytes() }
        val files = Json.parseToJsonElement(bytes("manifest.json").decodeToString()).jsonObject.getValue("files").jsonObject
        fun verified(path: String): ByteArray? = bytes(path).takeIf { b ->
            MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) } == files[path]?.jsonPrimitive?.content
        }
        val spec = verified("models/sign_reader.json") ?: return null
        val weights = verified("models/sign_reader.bin") ?: return null
        LandmarkSignReader(spec.decodeToString(), weights)
    }.getOrNull()
}
