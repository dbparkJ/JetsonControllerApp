package com.example.jetsoncontroller.ui

import android.graphics.BitmapFactory
import com.example.jetsoncontroller.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Explicit device test: verifies the key embedded in the installed app against both map services. */
class VWorldMapConnectivityTest {
    @Test fun bundledConfigurationLoadsBaseAndSatelliteImages() {
        assertTrue("The deployed app must contain its VWorld map configuration", BuildConfig.VWORLD_API_KEY.isNotBlank())
        val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        // Public sample tile in central Seoul; does not read the phone's location.
        for ((layer, extension) in listOf("Base" to "png", "Satellite" to "jpeg")) {
            val url = "https://api.vworld.kr/req/wmts/1.0.0/${BuildConfig.VWORLD_API_KEY}/$layer/15/12689/27941.$extension"
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertTrue("$layer map service must return HTTP success", response.isSuccessful)
                val body = response.body
                assertNotNull("$layer map service must return image bytes", body)
                assertTrue("$layer must return an image, not an API error", body!!.contentType()?.type == "image")
                val bytes = body.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                assertNotNull("$layer tile must decode as an image", bitmap)
                assertEquals("$layer width", 256, bitmap!!.width)
                assertEquals("$layer height", 256, bitmap.height)
                bitmap.recycle()
            }
        }
    }

}
