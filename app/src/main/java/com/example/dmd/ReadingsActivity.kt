package com.example.dmd

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

private const val INTERNET_PERMISSION_CODE = 1001
private const val SERVER_PORT = 5002
private const val PREFS_NAME = "SharedIPPrefs"
private const val KEY_IP_ADDRESS = "SavedIPAddress"
private const val NOTIFICATION_CHANNEL_ID = "temperature_channel"
private const val NOTIFICATION_ID = 1
private const val POST_NOTIFICATION_PERMISSION_CODE = 1002

class ReadingsActivity : AppCompatActivity() {

    private var serverSocket: ServerSocket? = null
    private var isListening = true
    private lateinit var inputField: EditText
    private lateinit var databaseItems: TextView
    private lateinit var temperatureDisplay: TextView
    private var ipCheckJob: Job? = null
    private var lastIpAddress: String? = null
    private val databaseHelper: DatabaseHelper by lazy { DatabaseHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.readings)

        temperatureDisplay = findViewById(R.id.temperature_text)
        inputField = findViewById(R.id.textIP_send)
        databaseItems = findViewById(R.id.databaseItems)
        val btnHome = findViewById<ImageButton>(R.id.home_button)

        loadSavedIPAddress()
        startIPCheckJob()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.INTERNET),
                INTERNET_PERMISSION_CODE
            )
        }

        btnHome.setOnClickListener {
            val intent = Intent(this, MainMenuActivity::class.java)
            startActivity(intent)
        }

        createNotificationChannel()

        val ipAddress = inputField.text.toString()
        if (ipAddress.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                startServer()
            }

            CoroutineScope(Dispatchers.IO).launch {
                while (isListening) {
                    sendDataToServer(ipAddress)
                    delay(10000)
                }
            }
        } else {
            Toast.makeText(this, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startIPCheckJob() {
        ipCheckJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                var currentIpAddress = inputField.text.toString().trim()
                if (currentIpAddress.isNotEmpty() && currentIpAddress != lastIpAddress) {
                    lastIpAddress = currentIpAddress
                    startIPCheckService(currentIpAddress)
                    Log.d("ReadingsActivity", "Service restarted with new IP: $currentIpAddress")
                }
                delay(5000)
            }
        }
    }

    private fun startIPCheckService(ipAddress: String) {
        saveIPAddress(ipAddress)
        val serviceIntent = Intent(this, MyService::class.java)
        startService(serviceIntent)
        Log.d("ReadingsActivity", "MyService started with IP: $ipAddress")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Temperature Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the latest temperature readings from the server"
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(temperature: String) {
        val serviceIntent = Intent(this, TemperatureService::class.java)
        serviceIntent.putExtra("temperature", temperature)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == POST_NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun startServer() {
        try {
            serverSocket = ServerSocket(SERVER_PORT)
            Log.d("Server", "Server started on port $SERVER_PORT")

            while (isListening) {
                val clientSocket = serverSocket?.accept()
                clientSocket?.let {
                    Log.d("Server", "Connection accepted from ${it.inetAddress.hostAddress}")
                    processClientConnection(it)
                }
            }
        } catch (e: Exception) {
            Log.e("Server", "Error: ${e.message}")
        } finally {
            serverSocket?.close()
            Log.d("Server", "Server stopped")
        }
    }

    private fun processClientConnection(clientSocket: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val receivedData = input.readLine()
            val jsonObject = JSONObject(receivedData)
            val temperature = jsonObject.getString("temperature")

            if (!receivedData.isNullOrEmpty()) {
                Log.d("Server", "Received: $receivedData")
                CoroutineScope(Dispatchers.Main).launch {
                    temperatureDisplay.text = "$temperature°C"
                    showNotification(temperature)
                }
            } else {
                Log.d("Server", "Received empty or null data")
            }

            clientSocket.close()
        } catch (e: Exception) {
            Log.e("Server", "Error processing client connection: ${e.message}")
        }
    }

    private suspend fun sendDataToServer(ipAddress: String) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, SERVER_PORT), 5000)
            Log.d("Client", "Connected to server at $ipAddress:$SERVER_PORT")

            val writer = OutputStreamWriter(socket.getOutputStream())
            val jsonPacket = """{"read":"send_temp"}"""
            writer.write(jsonPacket)
            writer.flush()
            Log.d("Client", "Sent: $jsonPacket")

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val response = reader.readLine()
            Log.d("Client", "Received: $response")

            val jsonObject = JSONObject(response)
            val temperature = jsonObject.getString("temperature")
            databaseHelper.insertTemperature(temperature)

            val lastThreeReadings = databaseHelper.getLastNReadings(3)
            val displayText = buildString {
                append("Last 3 Temperatures:\n")
                lastThreeReadings.forEach { (temperature, _) ->
                    append("           $temperature°C\n")
                }
            }

            databaseItems.text = displayText

            CoroutineScope(Dispatchers.Main).launch {
                temperatureDisplay.text = "$temperature°C"
                showNotification(temperature)
            }

            socket.close()
        } catch (e: Exception) {
            Log.e("Client", "Error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        ipCheckJob?.cancel()
        serverSocket?.close()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    private fun saveIPAddress(ipAddress: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_IP_ADDRESS, ipAddress).apply()
        Log.d("ReadingsActivity", "IP address saved: $ipAddress")
    }

    private fun loadSavedIPAddress() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIPAddress = sharedPreferences.getString(KEY_IP_ADDRESS, "")
        inputField.setText(savedIPAddress)
    }
}
