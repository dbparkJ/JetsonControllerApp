package com.example.jetsoncontroller.model

data class PairingInfo(
    val version: Int,
    val deviceId: String,
    val bootstrapSecretHex: String
) {

    val shortId: String
        get() =
            deviceId
                .replace("-", "")
                .takeLast(4)
                .uppercase()

    val expectedBleName: String
        get() = "MMS-$shortId"
}
