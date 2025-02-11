package com.example.dmd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

class InternetConnectionReceiver(private val listener: InternetConnectionListener) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (isInternetAvailable(context)) {
            listener.onInternetAvailable()
        } else {
            listener.onInternetLost()
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    interface InternetConnectionListener {
        fun onInternetAvailable()
        fun onInternetLost()
    }
}
