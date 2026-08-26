package com.example.jetsoncontroller.protocol

enum class JetsonCommand(
    val id: Byte
) {

    START_SYSTEM(0x01),

    STOP_SYSTEM(0x02),

    RESTART_SERVICES(0x03),

    REBOOT(0x04),

    SHUTDOWN(0x05),

    GET_STATUS(0x06),

    SET_WIFI(0x07),

    REQUEST_WIFI_DIRECT(0x08)
}
