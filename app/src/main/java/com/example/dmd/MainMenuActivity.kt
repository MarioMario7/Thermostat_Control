package com.example.dmd

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

private const val INTERNET_PERMISSION_CODE = 1001

class MainMenuActivity : AppCompatActivity(), InternetConnectionReceiver.InternetConnectionListener {

    private lateinit var internetReceiver: InternetConnectionReceiver

    private lateinit var butThermostat: Button
    private lateinit var butFan: Button
    private lateinit var butHeater: Button
    private lateinit var butReadings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.start_menu)

        butThermostat = findViewById(R.id.thermostat)
        butFan = findViewById(R.id.fan_control)
        butHeater = findViewById(R.id.heater_control)
        butReadings = findViewById(R.id.readings)

        internetReceiver = InternetConnectionReceiver(this)
        registerReceiver(internetReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.INTERNET),
                INTERNET_PERMISSION_CODE
            )
        }

        butThermostat.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        butFan.setOnClickListener {
            val intent = Intent(this, FanActivity::class.java)
            startActivity(intent)
        }

        butHeater.setOnClickListener {
            val intent = Intent(this, HeaterActivity::class.java)
            startActivity(intent)
        }

        butReadings.setOnClickListener {
            val intent = Intent(this, ReadingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(internetReceiver)
    }

    override fun onInternetAvailable() {
        setButtonsEnabled(true)
    }

    override fun onInternetLost() {
        Toast.makeText(this, "Internet is lost", Toast.LENGTH_SHORT).show()
        setButtonsEnabled(false)
    }

    private fun setButtonsEnabled(isEnabled: Boolean) {
        butThermostat.isEnabled = isEnabled
        butFan.isEnabled = isEnabled
        butHeater.isEnabled = isEnabled
        butReadings.isEnabled = isEnabled
    }
}
