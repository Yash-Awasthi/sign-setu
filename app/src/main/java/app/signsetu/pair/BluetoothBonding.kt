package app.signsetu.pair

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Finding and bonding a second screen from inside the app, so the officer need not leave for system settings.
 * Scanning is classic discovery under BLUETOOTH_SCAN declared "never for location" (Android 12 and later); older
 * phones would need location for it, so there the app keeps to system pairing. Bonding shows the system's own
 * confirmation on both phones, and the six-digit code check of the link still follows. */
@SuppressLint("MissingPermission")
class BluetoothBonding(private val context: Context) {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    /** A bond's own cancelDiscovery ends discovery, which would otherwise be restarted into the bond. */
    @Volatile private var bondingInFlight = false

    /** Only Android 12 and later can scan without location; the build refuses location in the standard flavor. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && adapter != null

    fun permissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()

    fun permitted() = permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Unbonded phones and tablets in range, as discovery finds them; discovery stops when the flow is cancelled. */
    fun discover(): Flow<List<Peer>> = callbackFlow {
        val a = adapter ?: run { close(); return@callbackFlow }
        val seen = LinkedHashMap<String, Peer>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = device(i) ?: return
                        if (d.bondState == BluetoothDevice.BOND_BONDED) return
                        if (d.bluetoothClass?.majorDeviceClass !in SCREENS) return
                        seen[d.address] = Peer(d.address, "${d.name ?: d.address} ${d.address.takeLast(5)}")
                        trySend(seen.values.toList())
                    }
                    // Discovery ends by itself after about 12 s; it runs again for as long as the list is open.
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (!bondingInFlight) runCatching { a.startDiscovery() }
                }
            }
        }
        val filter = IntentFilter().apply { addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        trySend(emptyList())
        runCatching { a.startDiscovery() }
        awaitClose {
            runCatching { a.cancelDiscovery() }
            context.unregisterReceiver(receiver)
        }
    }

    /** Bonds with [address] and waits for the outcome; true once bonded. The system asks both phones to confirm. */
    suspend fun bond(address: String, timeoutMs: Long = 60_000): Boolean {
        val a = adapter ?: return false
        bondingInFlight = true
        try {
            runCatching { a.cancelDiscovery() }
            val d = runCatching { a.getRemoteDevice(address) }.getOrNull() ?: return false
            if (d.bondState == BluetoothDevice.BOND_BONDED) return true
            val states = callbackFlow {
                val r = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        if (device(i)?.address != address) return
                        trySend(i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE))
                    }
                }
                ContextCompat.registerReceiver(context, r, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
                when (bondStart(d.bondState)) {
                    // A bond that completed before the receiver registered sends no broadcast.
                    BondStart.Bonded -> trySend(BluetoothDevice.BOND_BONDED)
                    // Its state change still arrives; asking again would fail and report BOND_NONE.
                    BondStart.Waits -> Unit
                    BondStart.Asks -> if (!d.createBond()) trySend(BluetoothDevice.BOND_NONE)
                }
                awaitClose { context.unregisterReceiver(r) }
            }
            return withTimeoutOrNull(timeoutMs) { states.first { it != BluetoothDevice.BOND_BONDING } == BluetoothDevice.BOND_BONDED } ?: run {
                // Ends the dialog too: leaving it up would say "failed" while the bond is still coming, and a retry sees BOND_BONDING.
                runCatching { d.cancelBondProcess() }
                false
            }
        } finally {
            bondingInFlight = false
        }
    }

    private companion object {
        val SCREENS = setOf(BluetoothClass.Device.Major.PHONE, BluetoothClass.Device.Major.COMPUTER, BluetoothClass.Device.Major.UNCATEGORIZED)

        @Suppress("DEPRECATION")
        fun device(i: Intent): BluetoothDevice? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
}

/** What an attempt does first: the state the device already reports decides, since a second createBond fails. */
internal enum class BondStart { Bonded, Waits, Asks }

internal fun bondStart(state: Int): BondStart = when (state) {
    BluetoothDevice.BOND_BONDED -> BondStart.Bonded
    BluetoothDevice.BOND_BONDING -> BondStart.Waits
    else -> BondStart.Asks
}
