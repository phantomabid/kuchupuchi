package com.phantom.kuchupuchi

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:583871350940:android:31520c5104bc6c63914559")
                .setApiKey("AIzaSyD5URI5vBGMjFAD095G4ezMfhjgdQXCdTM")
                .setDatabaseUrl(BuildConfig.FIREBASE_DB_URL)
                .setProjectId("kuchupuchi")
                .setStorageBucket("kuchupuchi.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        }

        try {
            val db = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DB_URL)
            db.setPersistenceEnabled(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
