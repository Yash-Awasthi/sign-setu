package app.signsetu

import app.signsetu.ui.avatar.AvatarAssets
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The avatar page and model, cached across process starts: a second start must not inflate the APK's entries again. */
class AvatarAssetsTest {

    private val avatar = File("src/main/assets/avatar")

    /** The shipped assets, opened the way AssetManager does it, and counted. */
    private class Apk {
        var opens = 0
            private set
        fun open(path: String, dir: File): InputStream {
            opens++
            return File(dir, path.removePrefix("avatar/")).inputStream()
        }
    }

    private fun streamOf(assets: AvatarAssets, path: String) = assets.stream(path)?.use { it.readBytes() }

    @Test
    fun aSecondStartServesTheModelFromTheDiskAndNeverOpensTheApk() {
        val tmp = Files.createTempDirectory("avatar-assets").toFile()
        try {
            val apk = Apk()
            val first = AvatarAssets(AvatarAssets.dirFor(tmp, 24), { apk.open(it, avatar) })
            val before = streamOf(first, "avatar/model.vrm")!!
            assertEquals("the first start reads the APK once", 1, apk.opens)
            assertEquals(before.size.toLong(), first.bytesFromApk)
            assertEquals(0L, first.bytesFromCache)

            // A new process: same cache directory, fresh object, nothing in memory.
            val second = AvatarAssets(AvatarAssets.dirFor(tmp, 24), { apk.open(it, avatar) })
            val after = streamOf(second, "avatar/model.vrm")!!
            assertEquals("the second start must not inflate the APK again", 1, apk.opens)
            assertEquals(0, second.apkReads)
            assertEquals(before.size.toLong(), second.bytesFromCache)
            assertArrayEquals(before, after)

            val copy = File(AvatarAssets.dirFor(tmp, 24), "avatar/model.vrm")
            assertTrue("the copy is on disk", copy.isFile)
            assertEquals("no part file is left behind", 0, copy.parentFile.listFiles()!!.count { it.name.endsWith(".part") })
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun everyFileOfThePageIsCachedNotJustTheModel() {
        val tmp = Files.createTempDirectory("avatar-page").toFile()
        try {
            val apk = Apk()
            val first = AvatarAssets(AvatarAssets.dirFor(tmp, 24), { apk.open(it, avatar) })
            val names = listOf("avatar/index.html", "avatar/avatar.js", "avatar/model.vrm")
            val shipped = names.associateWith { streamOf(first, it) }
            assertEquals(names.size, apk.opens)

            val second = AvatarAssets(AvatarAssets.dirFor(tmp, 24), { apk.open(it, avatar) })
            for (name in names) assertArrayEquals(shipped[name], streamOf(second, name))
            assertEquals("a restart reads no asset at all", names.size, apk.opens)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun aFileTheBundleDoesNotHaveIsMissingAndServesNothing() {
        val tmp = Files.createTempDirectory("avatar-missing").toFile()
        try {
            val apk = Apk()
            val assets = AvatarAssets(AvatarAssets.dirFor(tmp, 24), { apk.open(it, avatar) })
            assertNull("a 404, not a blank page", assets.stream("avatar/nope.js"))
            assertEquals("nothing is cached for a missing file", 0, assets.bytesFromCache)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun anEmptyCopyIsNotServedAndTheApkIsReadAgain() {
        val tmp = Files.createTempDirectory("avatar-empty").toFile()
        try {
            val apk = Apk()
            val dir = AvatarAssets.dirFor(tmp, 24)
            val copy = File(dir, "avatar/index.html")
            copy.parentFile.mkdirs()
            copy.writeBytes(ByteArray(0))

            val assets = AvatarAssets(dir, { apk.open(it, avatar) })
            assertTrue((streamOf(assets, "avatar/index.html")?.size ?: 0) > 0)
            assertEquals("a zero-length copy is treated as absent", 1, apk.opens)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun anUpdateReplacesThePreviousVersionsCopy() {
        val tmp = Files.createTempDirectory("avatar-update").toFile()
        try {
            val apk = Apk()
            val old = AvatarAssets(AvatarAssets.dirFor(tmp, 24), { apk.open(it, avatar) })
            streamOf(old, "avatar/index.html")
            val oldDir = AvatarAssets.dirFor(tmp, 24)
            assertTrue(oldDir.isDirectory)

            // The new version's cache: the old copy is gone and its bytes are read from the APK again.
            val newDir = AvatarAssets.dirFor(tmp, 25)
            assertTrue("the old version's copy is removed", !oldDir.exists())
            val fresh = AvatarAssets(newDir, { apk.open(it, avatar) })
            assertTrue((streamOf(fresh, "avatar/index.html")?.size ?: 0) > 0)
            assertEquals(2, apk.opens)
        } finally {
            tmp.deleteRecursively()
        }
    }

    /**
     * What the cache removes, on the JVM with the real bytes: the APK's deflate/inflate against a plain read of the
     * copy. Not a phone figure: the device read and the WebView's own transfer and three.js parse are not here.
     */
    @Test
    fun measureTheInflateTheCacheRemoves() {
        val tmp = Files.createTempDirectory("avatar-measure").toFile()
        try {
            val names = listOf("index.html", "avatar.js", "model.vrm")
            val raw = names.map { File(avatar, it).readBytes() }
            val packed = raw.map { deflate(it) }
            println("avatar-assets JVM: apk ${packed.sumOf { it.size }} B for ${raw.sumOf { it.size }} B raw")

            val apk = Apk()
            val assets = AvatarAssets(AvatarAssets.dirFor(tmp, 24), { apk.open(it, avatar) })
            for (n in names) streamOf(assets, "avatar/$n") // fill the cache once
            val hits = AvatarAssets(AvatarAssets.dirFor(tmp, 24), { apk.open(it, avatar) })

            repeat(3) { for (p in packed) inflate(p) }
            val inflateNs = time { repeat(10) { for (p in packed) inflate(p) } }
            val cacheNs = time { repeat(10) { for (n in names) hits.stream("avatar/$n")!!.use { drain(it) } } }
            println(
                String.format(
                    Locale.ROOT,
                    "avatar-assets JVM: apk path (inflate) %.1f ms, cache path (read) %.1f ms for %d B",
                    inflateNs / 1e6 / 10,
                    cacheNs / 1e6 / 10,
                    raw.sumOf { it.size },
                ),
            )

            // The numbers are a report, not a gate: a loaded machine makes any timing bound flaky.
            assertEquals(raw.sumOf { it.size }, names.sumOf { streamOf(hits, "avatar/$it")!!.size })
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val deflater = Deflater(9)
        try {
            DeflaterOutputStream(out, deflater, 64 * 1024).use { it.write(bytes) }
        } finally {
            deflater.end()
        }
        return out.toByteArray()
    }

    private fun inflate(packed: ByteArray): Int = InflaterInputStream(ByteArrayInputStream(packed)).use { drain(it).toInt() }

    /** Read to the end with the platform's transfer buffer, without holding the whole file twice. */
    private fun drain(input: InputStream): Long {
        var read = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            read += n
        }
        return read
    }

    private fun time(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }
}
