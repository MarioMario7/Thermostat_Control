package com.example.dmd

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

private const val CHANNEL_ID = "ip_check_channel"
private const val PREFS_NAME = "SharedIPPrefs"
private const val KEY_IP_ADDRESS = "SavedIPAddress"

class MyService : Service() {

    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ipAddress = intent?.getStringExtra("IP_ADDRESS")
        if (ipAddress != null) {
            job = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    validateAndSaveIPAddress(ipAddress)
                    delay(10000) // Check every 10 seconds
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun validateAndSaveIPAddress(ipAddress: String) {
        val trimmedIp = ipAddress.trim()
        Log.d("MyService", "Checking IP: $trimmedIp")

        if (isValidIPAddress(trimmedIp)) {
            saveIPAddress(trimmedIp)
            Log.d("MyService", "Valid IP saved: $trimmedIp")
        } else {
            Log.d("MyService", "Invalid IP address: $trimmedIp")
        }
    }

    private fun isValidIPAddress(ip: String): Boolean {
        return ip.count { it == '.' } == 3
    }

    private fun saveIPAddress(ipAddress: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_IP_ADDRESS, ipAddress).apply()
        Log.d("MyService", "IP address saved in SharedPreferences.")
        }
}