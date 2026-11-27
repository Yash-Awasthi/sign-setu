package app.signsetu

import android.bluetooth.BluetoothDevice
import app.signsetu.pair.BondStart
import app.signsetu.pair.bondStart
import org.junit.Assert.assertEquals
import org.junit.Test

/** The first step of a bond, the only part of BluetoothBonding that decides on an input alone; the rest needs a phone. */
class BondStartTest {
    @Test
    fun aDeviceAlreadyBondedIsNotAskedAgain() {
        assertEquals(BondStart.Bonded, bondStart(BluetoothDevice.BOND_BONDED))
    }

    @Test
    fun aBondAlreadyRunningIsWaitedFor() {
        assertEquals(BondStart.Waits, bondStart(BluetoothDevice.BOND_BONDING))
    }

    @Test
    fun anUnbondedDeviceIsAskedToBond() {
        assertEquals(BondStart.Asks, bondStart(BluetoothDevice.BOND_NONE))
    }
}
