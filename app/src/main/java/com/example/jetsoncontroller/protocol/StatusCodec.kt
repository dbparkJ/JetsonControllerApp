package com.example.jetsoncontroller.protocol

import com.example.jetsoncontroller.model.JetsonStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder

object StatusCodec {

    fun decode(
        data: ByteArray
    ): JetsonStatus {

        require(
            data.size >= 14
        ) {
            "Invalid Jetson status packet."
        }

        val buffer =
            ByteBuffer
                .wrap(data)
                .order(
                    ByteOrder
                        .LITTLE_ENDIAN
                )

        val version =
            buffer.get()
                .toInt() and 0xFF

        require(version == 1) {
            "Unsupported status packet version."
        }

        val cpu =
            buffer.get()
                .toInt() and 0xFF

        val gpu =
            buffer.get()
                .toInt() and 0xFF

        val temperature =
            buffer.get()
                .toInt()

        val storage =
            buffer.get()
                .toInt() and 0xFF

        val flags =
            buffer.get()
                .toInt() and 0xFF

        val ramUsed =
            buffer.int

        val ramTotal =
            buffer.int

        var wifiConnected = false
        var wifiSsid: String? = null
        if (buffer.hasRemaining()) {
            require(buffer.remaining() >= 2) {
                "Invalid Jetson Wi-Fi status extension."
            }
            val wifiFlags = buffer.get().toInt() and 0xFF
            val ssidLength = buffer.get().toInt() and 0xFF
            require(buffer.remaining() >= ssidLength) {
                "Invalid Jetson Wi-Fi SSID length."
            }
            val ssidBytes = ByteArray(ssidLength)
            buffer.get(ssidBytes)
            wifiConnected = wifiFlags and 0x01 != 0
            wifiSsid = if (wifiConnected) {
                ssidBytes.toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
            } else {
                null
            }
        }

        return JetsonStatus(
            cpuPercent =
                cpu.coerceIn(0, 100),

            gpuPercent =
                gpu.coerceIn(0, 100),

            ramUsedMb =
                ramUsed.coerceAtLeast(0),

            ramTotalMb =
                ramTotal.coerceAtLeast(0),

            temperatureC =
                temperature.toFloat(),

            storagePercent =
                storage.coerceIn(0, 100),

            cameraRunning =
                flags and 0x01 != 0,

            lidarRunning =
                flags and 0x02 != 0,

            gnssRunning =
                flags and 0x04 != 0,

            mmsRunning =
                flags and 0x08 != 0,

            wifiConnected =
                wifiConnected,

            wifiSsid =
                wifiSsid
        )
    }
}
