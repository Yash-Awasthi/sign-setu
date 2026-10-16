package app.signsetu.pair

import android.content.Context

/** The standard build: bonded Bluetooth only, no Play services and no location (D-26, SEC-66). */
object StageTransports {
    fun create(context: Context): StageTransport? = BluetoothTransport.create(context)

    @Suppress("UNUSED_PARAMETER")
    fun permissions(context: Context): Array<String> = BluetoothTransport.permissions()
}
