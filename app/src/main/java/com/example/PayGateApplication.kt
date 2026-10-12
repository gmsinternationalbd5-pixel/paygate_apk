package com.example

import android.app.Application
import com.example.data.local.db.AppDatabase
import com.example.data.prefs.PreferenceManager
import com.example.data.security.KeystoreSecretManager
import com.example.service.PayGateForwarderService

class PayGateApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var prefs: PreferenceManager
        private set

    lateinit var secretManager: KeystoreSecretManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        prefs = PreferenceManager(this)
        secretManager = KeystoreSecretManager(this)

        if (prefs.isPaired && prefs.isServiceEnabled && !prefs.isAuthRevoked) {
            PayGateForwarderService.start(this)
        }
    }
}
