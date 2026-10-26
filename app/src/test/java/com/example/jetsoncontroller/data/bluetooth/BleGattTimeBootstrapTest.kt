package com.example.jetsoncontroller.data.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.protocol.BleTimeSync
import com.example.jetsoncontroller.protocol.CommandCodec
import com.example.jetsoncontroller.protocol.JetsonCommand
import com.example.jetsoncontroller.protocol.UuidCodec
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
class BleGattTimeBootstrapTest {
    @Test
    fun `capable backend becomes Ready only after acknowledged clock write`() = fixture { f ->
        f.authenticate()
        assertFalse(f.client.isReady())
        Mockito.verify(f.gatt).writeCharacteristic(f.command)
        Mockito.verify(f.command).writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val frame = ArgumentCaptor.forClass(ByteArray::class.java)
        Mockito.verify(f.command).value = frame.capture()
        assertArrayEquals(BleTimeSync.command(PHONE_TIME), frame.value)
        f.ack()
        assertTrue(f.client.isReady())
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()
        assertTrue(f.client.isReady())
    }

    @Test
    fun `backend clock rejection blocks Ready and late ACK cannot restore it`() = fixture { f ->
        f.authenticate()
        f.ack(BluetoothGatt.GATT_FAILURE)
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
        f.ack()
        assertFalse(f.client.isReady())
        Mockito.verify(f.gatt).disconnect()
    }

    @Test
    fun `missing clock ACK times out and cannot authorize a late ACK`() = fixture { f ->
        f.authenticate()
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        assertTrue(f.client.connectionState.value is ConnectionState.Connected)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
        f.ack()
        assertFalse(f.client.isReady())
    }

    @Test
    fun `disconnect cancels clock timeout and ignores late ACK`() = fixture { f ->
        f.authenticate()
        runCurrent()
        f.client.disconnect()
        f.ack()
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(ConnectionState.Disconnected, f.client.connectionState.value)
    }

    @Test
    fun `remote disconnect during clock write clears pending authentication`() = fixture { f ->
        f.authenticate()
        f.callback.onConnectionStateChange(f.gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        f.ack()
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(ConnectionState.Disconnected, f.client.connectionState.value)
        assertNull(f.client.currentDeviceId())
    }

    @Test
    fun `stale GATT write and auth callbacks do not complete a replacement session`() = fixture { f ->
        val staleGatt = Mockito.mock(BluetoothGatt::class.java)
        f.authenticate()
        f.callback.onCharacteristicWrite(staleGatt, f.command, BluetoothGatt.GATT_SUCCESS)
        f.callback.onCharacteristicWrite(staleGatt, f.command, BluetoothGatt.GATT_FAILURE)
        f.callback.onCharacteristicRead(staleGatt, f.authState, byteArrayOf(1), BluetoothGatt.GATT_SUCCESS)
        assertTrue(f.client.connectionState.value is ConnectionState.Connected)
        f.ack()
        assertTrue(f.client.isReady())
    }

    @Test
    fun `other command writes cannot overtake clock ACK and duplicate auth read is harmless`() = fixture { f ->
        f.authenticate()
        assertFalse(f.client.writeCommand(CommandCodec.encode(JetsonCommand.GET_STATUS)))
        f.authenticate()
        val other = Mockito.mock(BluetoothGattCharacteristic::class.java)
        Mockito.`when`(other.uuid).thenReturn(JetsonGattSpec.AUTH_RESPONSE_UUID)
        f.callback.onCharacteristicWrite(f.gatt, other, BluetoothGatt.GATT_SUCCESS)
        assertFalse(f.client.isReady())
        Mockito.verify(f.gatt, Mockito.times(1)).writeCharacteristic(f.command)
        f.ack()
        assertTrue(f.client.writeCommand(CommandCodec.encode(JetsonCommand.GET_STATUS)))
    }

    @Test
    fun `write enqueue failure blocks Ready`() = fixture { f ->
        Mockito.`when`(f.gatt.writeCharacteristic(f.command)).thenReturn(false)
        f.authenticate()
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
        f.ack()
        assertFalse(f.client.isReady())
    }

    @Test
    fun `revoked Bluetooth permission blocks clock write`() = fixture { f ->
        Mockito.`when`(f.gatt.writeCharacteristic(f.command)).thenThrow(SecurityException())
        f.authenticate()
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
    }

    @Test
    fun `invalid phone time is rejected without a GATT command write`() = fixture(phoneTime = 0) { f ->
        f.authenticate()
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
        Mockito.verify(f.gatt, Mockito.never()).writeCharacteristic(f.command)
    }

    @Test
    fun `old single-byte backend retains normal authentication and HTTP fallback`() = fixture { f ->
        f.authenticate(byteArrayOf(1))
        assertTrue(f.client.isReady())
        Mockito.verify(f.gatt, Mockito.never()).writeCharacteristic(f.command)
    }

    @Test
    fun `backend with unknown capability retains legacy authentication`() = fixture { f ->
        f.authenticate(byteArrayOf(1, 2))
        assertTrue(f.client.isReady())
        Mockito.verify(f.gatt, Mockito.never()).writeCharacteristic(f.command)
    }

    @Test
    fun `unauthenticated auth state cannot trigger a time write`() = fixture { f ->
        f.authenticate(byteArrayOf(0, 1))
        assertFalse(f.client.isReady())
        Mockito.verify(f.gatt, Mockito.never()).writeCharacteristic(f.command)
    }

    @Test
    fun `capability without a local authenticated session key cannot authorize clock sync`() = fixture { f ->
        f.setField("pendingSessionEncryptionKey", null)
        f.authenticate()
        assertTrue(f.client.connectionState.value is ConnectionState.Error)
        Mockito.verify(f.gatt, Mockito.never()).writeCharacteristic(f.command)
    }

    @Test
    fun `queued credential lookup cannot revive a disconnected GATT session`() = fixture { f ->
        val id = Mockito.mock(BluetoothGattCharacteristic::class.java)
        Mockito.`when`(id.uuid).thenReturn(JetsonGattSpec.DEVICE_ID_UUID)
        f.callback.onCharacteristicRead(f.gatt, id, UuidCodec.toBytes(UUID.fromString(DEVICE_ID)), BluetoothGatt.GATT_SUCCESS)
        f.client.disconnect()
        runCurrent()
        assertEquals(ConnectionState.Disconnected, f.client.connectionState.value)
        Mockito.verify(f.gatt, Mockito.never()).readCharacteristic(Mockito.any())
    }

    private class Fixture(val client: BleGattClient) {
        val gatt = Mockito.mock(BluetoothGatt::class.java)
        val command = Mockito.mock(BluetoothGattCharacteristic::class.java)
        val authState = Mockito.mock(BluetoothGattCharacteristic::class.java)
        val callback: BluetoothGattCallback

        init {
            val service = Mockito.mock(BluetoothGattService::class.java)
            Mockito.`when`(gatt.getService(JetsonGattSpec.SERVICE_UUID)).thenReturn(service)
            Mockito.`when`(service.getCharacteristic(JetsonGattSpec.COMMAND_UUID)).thenReturn(command)
            Mockito.`when`(command.uuid).thenReturn(JetsonGattSpec.COMMAND_UUID)
            Mockito.`when`(authState.uuid).thenReturn(JetsonGattSpec.AUTH_STATE_UUID)
            Mockito.`when`(gatt.writeCharacteristic(command)).thenReturn(true)
            setField("bluetoothGatt", gatt)
            setField("verifiedDeviceId", DEVICE_ID)
            setField("pendingSessionEncryptionKey", ByteArray(32))
            @Suppress("UNCHECKED_CAST")
            val state = field("_connectionState").get(client) as MutableStateFlow<ConnectionState>
            state.value = ConnectionState.Connected("LAB Jetson")
            callback = field("gattCallback").get(client) as BluetoothGattCallback
        }

        fun authenticate(response: ByteArray = byteArrayOf(1, 1)) {
            callback.onCharacteristicRead(gatt, authState, response, BluetoothGatt.GATT_SUCCESS)
        }

        fun ack(status: Int = BluetoothGatt.GATT_SUCCESS) = callback.onCharacteristicWrite(gatt, command, status)
        fun setField(name: String, value: Any?) = field(name).set(client, value)
        private fun field(name: String) = BleGattClient::class.java.getDeclaredField(name).apply { isAccessible = true }
    }

    private fun fixture(phoneTime: Long = PHONE_TIME, test: suspend TestScope.(Fixture) -> Unit) = runTest {
        Mockito.mockStatic(Log::class.java).use {
            val context = Mockito.mock(Context::class.java)
            Mockito.`when`(context.applicationContext).thenReturn(context)
            val client = BleGattClient(context, Mockito.mock(DeviceCredentialStore::class.java), backgroundScope) { phoneTime }
            try {
                test(Fixture(client))
            } finally {
                client.disconnect()
            }
        }
    }

    companion object {
        private const val PHONE_TIME = 1_792_728_600_000L
        private const val DEVICE_ID = "12345678-1234-1234-1234-123456789abc"
    }
}
