package com.phantom.kuchupuchi.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.phantom.kuchupuchi.service.FloatingService

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val serviceIntent = Intent(context, FloatingService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
