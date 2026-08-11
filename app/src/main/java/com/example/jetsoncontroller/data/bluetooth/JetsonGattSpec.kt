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

    val DEVICE_ID_UUID: UUID =
        UUID.fromString(
            "a1000000-0000-0000-0000-000000000006"
        )

    val AUTH_CHALLENGE_UUID: UUID =
        UUID.fromString(
            "a1000000-0000-0000-0000-000000000007"
        )

    val AUTH_RESPONSE_UUID: UUID =
        UUID.fromString(
            "a1000000-0000-0000-0000-000000000008"
        )

    val AUTH_STATE_UUID: UUID =
        UUID.fromString(
            "a1000000-0000-0000-0000-000000000009"
        )
}
