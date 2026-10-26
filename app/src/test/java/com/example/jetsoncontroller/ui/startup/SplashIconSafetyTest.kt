package com.example.jetsoncontroller.ui.startup

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class SplashIconSafetyTest {
    private val resources = File("src/main/res").takeIf(File::isDirectory) ?: File("app/src/main/res")
    private val android = "http://schemas.android.com/apk/res/android"
    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(resources, path))

    @Test fun `every corner of the inset artwork fits inside the two thirds safe circle`() {
        val inset = xml("drawable/geo_splash_icon.xml").documentElement
        assertEquals("inset", inset.tagName)
        assertEquals("@drawable/geo_symbol", inset.getAttributeNS(android, "drawable"))
        fun fraction(edge: String) = inset.getAttributeNS(android, "inset$edge").removeSuffix("%").toDouble() / 100
        val xs = listOf(fraction("Left") - .5, .5 - fraction("Right"))
        val ys = listOf(fraction("Top") - .5, .5 - fraction("Bottom"))
        xs.forEach { x -> ys.forEach { y -> assertTrue(x * x + y * y <= 1.0 / 9) } }
    }

    @Test fun `all light and dark splash palettes use the safe drawable`() {
        listOf("values", "values-night").forEach { directory ->
            val styles = xml("$directory/themes.xml").getElementsByTagName("style")
            var count = 0
            for (index in 0 until styles.length) {
                val style = styles.item(index) as org.w3c.dom.Element
                if (!style.getAttribute("name").startsWith("Theme.GeoX.Splash.")) continue
                count++
                val items = style.getElementsByTagName("item")
                val icon = (0 until items.length).map { items.item(it) as org.w3c.dom.Element }
                    .single { it.getAttribute("name") == "android:windowSplashScreenAnimatedIcon" }
                assertEquals("@drawable/geo_splash_icon", icon.textContent.trim())
            }
            assertEquals(3, count)
        }
    }
}
