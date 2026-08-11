package com.example.jetsoncontroller

import android.app.Application
import com.example.jetsoncontroller.data.repository.JetsonRepository

class JetsonApplication :
    Application() {

    lateinit var repository:
        JetsonRepository
        private set

    override fun onCreate() {

        super.onCreate()

        repository =
            JetsonRepository(this)
    }
}
