package com.example.jetsoncontroller

import android.app.Application
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.data.repository.JetsonRepository

class JetsonApplication :
    Application() {

    lateinit var repository:
        JetsonRepository
        private set

    lateinit var credentialStore:
        DeviceCredentialStore
        private set

    override fun onCreate() {

        super.onCreate()

        credentialStore =
            DeviceCredentialStore(this)

        repository =
            JetsonRepository(this, credentialStore)
    }
}
