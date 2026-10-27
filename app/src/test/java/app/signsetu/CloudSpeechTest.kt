package app.signsetu

import app.signsetu.assistant.CloudAi
import app.signsetu.net.Http
import app.signsetu.net.Reply
import app.signsetu.net.TokenVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSpeechTest {
    private class Vault(var token: String? = "tok") : TokenVault {
        override fun get() = token
        override fun put(token: String): Boolean { this.token = token; return true }
        override fun clear() { token = null }
    }

    private val mp3 = byteArrayOf(0x49, 0x44, 0x33, 0x00, -1)
    private val urls = mutableListOf<String>()
    private val server = Http { _, url, _, _, _ -> urls += url; Reply(200, "", mp3) }

    private fun ai(on: Boolean = true, online: Boolean = true, vault: Vault = Vault()) = CloudAi({ on }, "https://gw.example", server, vault) { online }

    @Test
    fun typedTextIsSpokenThroughTheGatewayWhenTheOfficerAllowedOnlineHelp() = runBlocking {
        assertArrayEquals(mp3, ai().speak("यहाँ बैठिए", "hi"))
        assertEquals(listOf("https://gw.example/v1/speak"), urls)
    }

    @Test
    fun nothingLeavesThePhoneWithoutConsentNetworkOrEnrolment() = runBlocking {
        assertNull(ai(on = false).speak("hello", "en"))
        assertNull(ai(online = false).speak("hello", "en"))
        assertNull(ai(vault = Vault(null)).speak("hello", "en"))
        assertTrue(urls.isEmpty())
    }
}
