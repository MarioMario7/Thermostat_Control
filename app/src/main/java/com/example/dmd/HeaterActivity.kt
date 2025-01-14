package com.example.dmd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

private const val INTERNET_PERMISSION_CODE = 1001
private const val serverPort = 5002 // Match with the server port
private const val PREFS_NAME = "SharedIPPrefs"
private const val KEY_IP_ADDRESS = "SavedIPAddress"

class HeaterActivity : AppCompatActivity(), InternetConnectionReceiver.InternetConnectionListener {

    private lateinit var internetReceiver: InternetConnectionReceiver
    private lateinit var inputField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.heater)

        val btn_on = findViewById<Button>(R.id.btn_on)
        val btn_off = findViewById<Button>(R.id.btn_off)
        inputField = findViewById(R.id.textIP)
        val btn_home = findViewById<ImageButton>(R.id.home_button)

        loadSavedIPAddress()

        internetReceiver = InternetConnectionReceiver(this)
        registerReceiver(internetReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.INTERNET),
                INTERNET_PERMISSION_CODE
            )
        }

        btn_on.setOnClickListener {
            val ip_address = inputField.text.toString().trim()
            if (ip_address.isNotEmpty()) {
                saveIPAddress(ip_address) // Save IP address
                Toast.makeText(this@HeaterActivity, "Turning On", Toast.LENGTH_LONG).show()
                send_package_heater(ip_address, "on")
            } else {
                Toast.makeText(this@HeaterActivity, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
            }
        }

        btn_off.setOnClickListener {
            val ip_address = inputField.text.toString().trim()
            if (ip_address.isNotEmpty()) {
                saveIPAddress(ip_address)
                Toast.makeText(this@HeaterActivity, "Turning Off", Toast.LENGTH_LONG).show()
                send_package_heater(ip_address, "off")
            } else {
                Toast.makeText(this@HeaterActivity, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
            }
        }

        btn_home.setOnClickListener {
            val intent = Intent(this, MainMenuActivity::class.java)
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
        findViewById<Button>(R.id.btn_on).isEnabled = isEnabled
        findViewById<Button>(R.id.btn_off).isEnabled = isEnabled
    }

    private fun saveIPAddress(ipAddress: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_IP_ADDRESS, ipAddress)
        editor.apply()
    }

    private fun loadSavedIPAddress() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIPAddress = sharedPreferences.getString(KEY_IP_ADDRESS, "")
        inputField.setText(savedIPAddress)
    }

    fun createJsonHeater(on_off: String, time: String? = null): String {
        val jsonData = JSONObject()
        jsonData.put("status", "ok")
        jsonData.put("gpio1", on_off)
        time?.let { jsonData.put("time", it) }
        return jsonData.toString()
    }

    fun send_package_heater(IP_address: String, on_off: String, time: String? = null) {
        val executor = Executors.newSingleThreadExecutor()

        executor.execute {
            val timeoutMillis = 1000 // Timeout in milliseconds
            val jsonPacket = createJsonHeater(on_off, time)

            val socket = Socket()

            try {
                val socketAddress = InetSocketAddress(IP_address, serverPort)
                socket.connect(socketAddress, timeoutMillis)

                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                writer.write(jsonPacket)
                writer.newLine()  // Important: Add new line
                writer.flush()

                println("Sent successfully: $jsonPacket")
            } catch (e: Exception) {
                println("Error during connect or transmission: ${e.message}")
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {
                    println("Error closing socket: ${e.message}")
                }
            }
        }
    }
}
