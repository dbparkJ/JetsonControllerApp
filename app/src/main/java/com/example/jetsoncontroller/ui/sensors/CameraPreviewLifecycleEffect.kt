package com.example.jetsoncontroller.ui.sensors

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun CameraPreviewLifecycleEffect(viewModel: CameraPreviewViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val application = LocalContext.current.applicationContext
    DisposableEffect(lifecycle, application, viewModel) {
        fun sync() = viewModel.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, _ -> sync() }
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = viewModel.trimMemory()
            override fun onTrimMemory(level: Int) {
                if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) viewModel.setVisible(false)
                else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) viewModel.trimMemory()
            }
        }
        application.registerComponentCallbacks(callbacks)
        lifecycle.addObserver(observer)
        sync()
        onDispose {
            lifecycle.removeObserver(observer)
            application.unregisterComponentCallbacks(callbacks)
            viewModel.close()
        }
    }
}
