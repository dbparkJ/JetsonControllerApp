package com.example.jetsoncontroller.util

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
