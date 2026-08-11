package com.example.jetsoncontroller.util

enum class SignalStrength(
    val label: String
) {

    EXCELLENT("매우 강함"),
    GOOD("강함"),
    FAIR("보통"),
    WEAK("약함")
}


fun signalStrengthFromRssi(
    rssi: Int
): SignalStrength {

    return when {

        rssi >= -55 ->
            SignalStrength.EXCELLENT

        rssi >= -67 ->
            SignalStrength.GOOD

        rssi >= -75 ->
            SignalStrength.FAIR

        else ->
            SignalStrength.WEAK
    }
}
