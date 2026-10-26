package com.example.jetsoncontroller.data.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.model.BlePairingState
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.PairingInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class BleGattConnectionTest {
    @Test
    fun `Android 11 waits 500 milliseconds before opening the first GATT`() = fixture { f ->
        f.start()
        runCurrent()
        advanceTimeBy(499)
        runCurrent()
        f.assertAttempts(0)
        advanceTimeBy(1)
        runCurrent()
        f.assertAttempts(1)
    }

    @Test
    fun `Android 12 opens GATT immediately`() = fixture(sdkInt = 31) { f ->
        f.start()
        f.assertAttempts(1)
    }

    @Test
    fun `133 closes before each delayed retry and stops after three attempts`() = fixture { f ->
        f.start(pairing = true)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        repeat(2) { index ->
            f.fail(index, 133)
            Mockito.verify(f.gatts[index]).close()
            assertEquals(BlePairingState.Retrying(index + 2, 3), f.client.pairingState.value)
            advanceTimeBy(499)
            runCurrent()
            f.assertAttempts(index + 1)
            advanceTimeBy(1)
            runCurrent()
            f.assertAttempts(index + 2)
        }
        f.fail(2, 133)
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
        assertEquals("GATT_133", (f.client.pairingState.value as BlePairingState.Error).technicalCode)
        advanceTimeBy(20_000)
        runCurrent()
        f.assertAttempts(3)
    }

    @Test
    fun `reconnect preserves target identity across a transient error`() = fixture(sdkInt = 31) { f ->
        f.start()
        f.fail(0, 62)
        val connecting = f.client.connectionState.value as ConnectionState.Connecting
        assertEquals(2, connecting.attempt)
        assertEquals(DEVICE_ID, connecting.deviceId)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        f.connected(1)
        val service = Mockito.mock(android.bluetooth.BluetoothGattService::class.java)
        val id = Mockito.mock(BluetoothGattCharacteristic::class.java)
        Mockito.`when`(f.gatts[1].getService(JetsonGattSpec.SERVICE_UUID)).thenReturn(service)
        Mockito.`when`(service.getCharacteristic(JetsonGattSpec.DEVICE_ID_UUID)).thenReturn(id)
        Mockito.`when`(id.uuid).thenReturn(JetsonGattSpec.DEVICE_ID_UUID)
        f.callback.onServicesDiscovered(f.gatts[1], 0)
        val otherId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")
        f.callback.onCharacteristicRead(f.gatts[1], id,
            com.example.jetsoncontroller.protocol.UuidCodec.toBytes(otherId), 0)
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
        assertEquals(DEVICE_ID, (f.client.connectionState.value as ConnectionState.Error).deviceId)
        advanceTimeBy(20_000)
        runCurrent()
        f.assertAttempts(2)
    }

    @Test
    fun `disconnect cancels both initial delay and retry delay`() = fixture { f ->
        f.start()
        runCurrent()
        f.client.disconnect()
        advanceTimeBy(500)
        runCurrent()
        f.assertAttempts(0)
        f.start()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        f.fail(0, 133)
        runCurrent()
        f.client.disconnect()
        advanceTimeBy(20_000)
        runCurrent()
        f.assertAttempts(1)
        assertEquals(ConnectionState.Disconnected, f.client.connectionState.value)
    }

    @Test
    fun `replacement connection cancels retry and ignores callbacks from old GATT`() = fixture(sdkInt = 31) { f ->
        f.start()
        f.fail(0, 133)
        runCurrent()
        f.start()
        f.connected(0)
        f.fail(0, 133)
        assertEquals(1, (f.client.connectionState.value as ConnectionState.Connecting).attempt)
        f.connected(1)
        advanceTimeBy(20_000)
        runCurrent()
        f.assertAttempts(2)
        assertTrue(f.client.connectionState.value is ConnectionState.Connected)
    }

    @Test
    fun `connection budget spans all retries and rejects late success`() = fixture(sdkInt = 31) { f ->
        f.start(pairing = true)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        f.fail(0, 133)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        advanceTimeBy(14_500)
        runCurrent()
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
        assertEquals("GATT_CONNECT_TIMEOUT", (f.client.pairingState.value as BlePairingState.Error).technicalCode)
        f.connected(1)
        f.assertAttempts(2)
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
    }

    @Test
    fun `connection authentication failure does not retry`() = fixture(sdkInt = 31) { f ->
        f.start(pairing = true)
        f.fail(0, 5)
        advanceTimeBy(20_000)
        runCurrent()
        f.assertAttempts(1)
        assertTrue((f.client.connectionState.value as ConnectionState.Error).message.contains("인증"))
    }

    @Test
    fun `missing service and ATT authorization error never reconnect`() = fixture(sdkInt = 31) { f ->
        f.start(pairing = true)
        f.connected(0)
        f.callback.onServicesDiscovered(f.gatts[0], 0)
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
        f.start(pairing = true)
        f.connected(1)
        f.callback.onCharacteristicRead(f.gatts[1], Mockito.mock(BluetoothGattCharacteristic::class.java), byteArrayOf(), 8)
        advanceTimeBy(20_000)
        runCurrent()
        f.assertAttempts(2)
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
    }

    private class Fixture(val client: BleGattClient, val context: Context) {
        val device = Mockito.mock(BluetoothDevice::class.java)
        val gatts = List(3) { Mockito.mock(BluetoothGatt::class.java) }
        val callback = BleGattClient::class.java.getDeclaredField("gattCallback").apply {
            isAccessible = true
        }.get(client) as BluetoothGattCallback

        init {
            Mockito.`when`(device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE))
                .thenReturn(gatts[0], gatts[1], gatts[2])
        }

        fun start(pairing: Boolean = false) {
            if (pairing) client.connectForPairing(device, "GEO-X", PairingInfo(1, DEVICE_ID, "01".repeat(32)))
            else client.connect(device, "GEO-X", DEVICE_ID)
        }

        fun fail(index: Int, status: Int) = callback.onConnectionStateChange(gatts[index], status, BluetoothProfile.STATE_DISCONNECTED)
        fun connected(index: Int) = callback.onConnectionStateChange(gatts[index], 0, BluetoothProfile.STATE_CONNECTED)
        fun assertAttempts(count: Int) {
            Mockito.verify(device, Mockito.times(count)).connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private fun fixture(sdkInt: Int = 30, test: suspend TestScope.(Fixture) -> Unit) = runTest {
        Mockito.mockStatic(Log::class.java).use {
            val context = Mockito.mock(Context::class.java)
            Mockito.`when`(context.applicationContext).thenReturn(context)
            val client = BleGattClient(context, Mockito.mock(DeviceCredentialStore::class.java), backgroundScope, sdkInt)
            try {
                test(Fixture(client, context))
            } finally {
                client.disconnect()
            }
        }
    }

    companion object {
        private const val DEVICE_ID = "12345678-1234-1234-1234-123456789abc"
    }
}
