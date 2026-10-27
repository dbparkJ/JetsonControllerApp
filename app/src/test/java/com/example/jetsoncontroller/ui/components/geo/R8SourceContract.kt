package com.example.jetsoncontroller.ui.components.geo

import java.io.File

/** Source contracts cover slot wiring that plain JVM tests cannot compose. */
internal fun r8UiSource(path: String): String {
    val root = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
        File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
    return File(root, path).readText(Charsets.UTF_8).replace("\r\n", "\n")
}
