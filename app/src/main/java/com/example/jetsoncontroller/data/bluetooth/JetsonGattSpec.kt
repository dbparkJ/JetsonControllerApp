package com.example.jetsoncontroller.data.bluetooth

import java.util.UUID

object JetsonGattSpec {

    val SERVICE_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000001")

    val COMMAND_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000002")

    val STATUS_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000003")

    val SYSTEM_INFO_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000004")

    val WIFI_CONFIG_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000005")
}
