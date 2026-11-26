package app.signsetu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.assistant.Direct
import app.signsetu.assistant.OnlineAssistant
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.settings.SettingsStore
import app.signsetu.net.BrainClient
import app.signsetu.net.TokenVault
import app.signsetu.net.UrlHttp
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** With the assistant off and no station paired, driving every online path on a real device must not even open a
 * socket. The tripwire is the platform ProxySelector: every URL connection asks it for a proxy before dialling. */
@RunWith(AndroidJUnit4::class)
class NoNetworkOfflineDeviceTest {
    private val selections = AtomicInteger()
    private var previous: ProxySelector? = null

    private val emptyVault = object : TokenVault {
        override fun get(): String? = null
        override fun put(token: String): Boolean = false
        override fun clear() {}
    }

    @Before
    fun installTripwire() {
        previous = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                selections.incrementAndGet()
                return emptyList()
            }

            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: java.io.IOException) {}
        })
    }

    @After
    fun restoreTripwire() {
        ProxySelector.setDefault(previous)
    }

    @Test
    fun offlineAndUnpairedPerformsNoNetworkRequest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // The assistant switch ships off; the empty vault is the "no station paired" side.
        assertFalse("the online assistant must be off until the officer turns it on", SettingsStore(context).current().onlineAssistant)

        selections.set(0)

        // A real gateway is configured on this machine (local.properties), so this exercises the gated path.
        // enroll() is deliberately NOT called here: it is the officer's explicit code exchange, not gated by the
        // assistant switch, and with a gateway configured it really dials.
        val gated = OnlineAssistant(
            enabled = { false },
            gatewayUrl = BuildConfig.GATEWAY_URL,
            http = UrlHttp(),
            vault = emptyVault,
            builtIn = Direct(BuildConfig.ASSIST_URL, BuildConfig.ASSIST_MODEL, ""),
            customVault = null,
            online = { true },
        )

        assertNull("assistant off: no assistant request", gated.ask("system", "user", emptyList()))
        gated.report("session log", "officer", "en", emptyList(), SignLanguage.ISL)
        gated.followUps("the last answer", false, emptyList())
        assertNull("assistant off: no speech request", gated.speak("hi", "en"))
        gated.warmUp()
        assertFalse("assistant off: session data must not be sent", gated.sendsSessionData())

        // Enrolment stays local only in a build with no department gateway: there is nowhere to send the code.
        val offline = OnlineAssistant(
            enabled = { false },
            gatewayUrl = "",
            http = UrlHttp(),
            vault = emptyVault,
            builtIn = Direct(BuildConfig.ASSIST_URL, BuildConfig.ASSIST_MODEL, ""),
            customVault = null,
            online = { true },
        )
        assertFalse("no gateway configured: enrolment has nowhere to send", offline.enroll("code"))

        // The station: nothing paired, so every call returns before opening a connection.
        val brain = BrainClient(emptyVault)
        assertNull("no station is paired", brain.pairing())
        assertNull(brain.status())
        assertNull(brain.recognise(ByteArray(0), null))
        assertNull(brain.liveStart("en"))
        assertNull(brain.sign(JsonArray(emptyList()), 1f, 1000))
        brain.networkChanged()

        val offlineSelections = selections.get()
        println("offline selections=$offlineSelections")
        assertEquals(
            "a socket-layer connection was attempted with the assistant off and no station paired: $offlineSelections",
            0,
            offlineSelections,
        )

        // Negative control: the tripwire must see a deliberate dead-port call, or the hook proves nothing.
        selections.set(0)
        assertNull(UrlHttp().call("GET", "https://127.0.0.1:1/", null, null, 1500))
        val control = selections.get()
        println("control selections=$control")
        assertTrue(
            "the tripwire saw no connection attempt for the deliberate dead-port call; measured $control",
            control >= 1,
        )
        println("offline selections=$offlineSelections control=$control")
    }
}
