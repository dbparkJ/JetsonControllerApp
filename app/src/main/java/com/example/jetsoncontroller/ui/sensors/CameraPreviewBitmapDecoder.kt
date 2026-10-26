package com.example.jetsoncontroller.ui.sensors

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object CameraPreviewBitmapDecoder {
    suspend fun decode(bytes: ByteArray): Bitmap = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid camera preview image." }
        val sample = cameraPreviewSampleSize(bounds.outWidth, bounds.outHeight)
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
        checkNotNull(decoded) { "Failed to decode camera preview frame." }
    }
}
