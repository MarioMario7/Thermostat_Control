package com.example.dmd;

import android.Manifest;
import android.annotation.SuppressLint
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import kotlinx.coroutines.*;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

private const val INTERNET_PERMISSION_CODE = 1001;
private const val serverPort = 5002;
private const val PREFS_NAME = "SharedIPPrefs";
private const val KEY_IP_ADDRESS = "SavedIPAddress";
private const val CHANNEL_ID = "temperature_monitoring_channel";

var control = 0;
var first_check = true;

class MainActivity : AppCompatActivity(), InternetConnectionReceiver.InternetConnectionListener {

    private lateinit var internetReceiver: InternetConnectionReceiver;
    private lateinit var inputField: EditText;
    private var targetTemperature: Double = 0.0;
    private var isMonitoringTemperature = false;
    private var selectedTime: LocalTime? = null;

    private var temperatureService: TemperatureMonitoringService? = null;
    private var isBound = false;

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            val binder = service as TemperatureMonitoringService.LocalBinder;
            temperatureService = binder.getService();
            isBound = true;
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            temperatureService = null;
            isBound = false;
        }
    };

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        val sendDataButton = findViewById<Button>(R.id.send_data);
        inputField = findViewById(R.id.textIP);
        val textTime = findViewById<TextView>(R.id.textTime);
        val textTemp = findViewById<EditText>(R.id.textTemp);
        val homeButton = findViewById<ImageButton>(R.id.home_button);
        val stopButton = findViewById<Button>(R.id.stop_button);

        var ipAddress = inputField.text.toString().trim();

        loadSavedIPAddress();
        internetReceiver = InternetConnectionReceiver(this);
        registerReceiver(internetReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.INTERNET),
                INTERNET_PERMISSION_CODE
            );
        }

        textTime.setOnClickListener {
            showTimePickerDialog(textTime);
        };

        stopButton.setOnClickListener {
            stopTemperatureMonitoring();

            sendControlCommand(ipAddress, "fan", "off");
            sendControlCommand(ipAddress, "heater", "off");
        };

        sendDataButton.setOnClickListener {
            ipAddress = inputField.text.toString().trim();ipAddress = inputField.text.toString().trim();
            val temperatureInput = textTemp.text.toString().trim().toDoubleOrNull();

            if (ipAddress.isNotEmpty() && temperatureInput != null && selectedTime != null) {
                targetTemperature = temperatureInput;
                saveIPAddress(ipAddress);
                startMonitoringAtSelectedTime(ipAddress);
            } else {
                Toast.makeText(this, "Please enter a valid IP address, temperature, and time", Toast.LENGTH_SHORT).show();
            }
        };

        homeButton.setOnClickListener {
            val intent = Intent(this, MainMenuActivity::class.java);
            startActivity(intent);
        };

        Intent(this, TemperatureMonitoringService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        };

        createNotificationChannel();
    }

    override fun onDestroy() {
        super.onDestroy();
        unregisterReceiver(internetReceiver);
        stopTemperatureMonitoring();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    override fun onInternetAvailable() {
        findViewById<Button>(R.id.send_data).isEnabled = true;
    }

    override fun onInternetLost() {
        Toast.makeText(this, "Internet is lost", Toast.LENGTH_SHORT).show();
        findViewById<Button>(R.id.send_data).isEnabled = false;
    }

    private fun saveIPAddress(ipAddress: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit().putString(KEY_IP_ADDRESS, ipAddress).apply();
    }

    private fun loadSavedIPAddress() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        val savedIPAddress = sharedPreferences.getString(KEY_IP_ADDRESS, "");
        inputField.setText(savedIPAddress);
    }

    private fun showTimePickerDialog(textTime: TextView) {
        val calendar = Calendar.getInstance();
        val hour = calendar.get(Calendar.HOUR_OF_DAY);
        val minute = calendar.get(Calendar.MINUTE);

        val timePickerDialog = TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val time = String.format("%02d:%02d", selectedHour, selectedMinute);
                textTime.text = time;
                selectedTime = LocalTime.of(selectedHour, selectedMinute);
            },
            hour,
            minute,
            true
        );
        timePickerDialog.show();
    }

    private fun startMonitoringAtSelectedTime(ipAddress: String) {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val currentTime = LocalTime.now();
                if (selectedTime != null && currentTime >= selectedTime) {
                    withContext(Dispatchers.Main) {
                        showNotification("Temperature Monitoring", "Data sent at the selected time: $selectedTime");
                        Toast.makeText(this@MainActivity, "Data sent at the selected time: $selectedTime", Toast.LENGTH_SHORT).show();
                    }
                    startTemperatureMonitoring(ipAddress);
                    break;
                }
                delay(1000);
            }
        };
    }

    private fun startTemperatureMonitoring(ipAddress: String) {
        isMonitoringTemperature = true;
        CoroutineScope(Dispatchers.IO).launch {
            while (isMonitoringTemperature) {
                sendTempRequestAndReceiveResponse(ipAddress);
                delay(5000);
            }
        };
    }

    private fun stopTemperatureMonitoring() {
        isMonitoringTemperature = false;
    }

    private fun sendTempRequestAndReceiveResponse(ipAddress: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket();
                socket.connect(InetSocketAddress(ipAddress, serverPort), 5000);

                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()));
                val requestJson = """{"read":"send_temp"}""";
                writer.write(requestJson);
                writer.newLine();
                writer.flush();

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()));
                val response = reader.readLine();

                val jsonResponse = JSONObject(response);
                val serverTemperature = jsonResponse.getDouble("temperature");

                withContext(Dispatchers.Main) {
                    handleTemperatureResponse(ipAddress, serverTemperature);
                }

                socket.close();
            } catch (e: Exception) {
                Log.e("TempRequest", "Error: ${e.message}");
            }
        };
    }

    private fun handleTemperatureResponse(ipAddress: String, serverTemperature: Double) {
        targetTemperature.let { targetTemp ->
            when {
                serverTemperature == targetTemp -> {
                    sendControlCommand(ipAddress, "fan", "off");
                    sendControlCommand(ipAddress, "heater", "off");
                    stopTemperatureMonitoring();
                }
                serverTemperature < targetTemp -> {
                    if (first_check) {
                        sendControlCommand(ipAddress, "heater", "on");
                        first_check = false;
                        control = 1;
                    } else {
                        if (control == 2) {
                            sendControlCommand(ipAddress, "fan", "off");
                            first_check = true;
                            control = 0;
                            stopTemperatureMonitoring();
                        }
                    }
                }
                serverTemperature > targetTemp -> {
                    if (control == 1) {
                        sendControlCommand(ipAddress, "heater", "off");
                        first_check = true;
                        control = 0;
                        stopTemperatureMonitoring();
                    } else {
                        sendControlCommand(ipAddress, "fan", "on");
                        first_check = false;
                        control = 2;
                    }
                }
            }
        };
    }

    private fun sendControlCommand(ipAddress: String, device: String, state: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket();
                socket.connect(InetSocketAddress(ipAddress, serverPort), 5000);

                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()));
                val command = when (device) {
                    "fan" -> """{"gpio":"$state"}""";
                    "heater" -> """{"gpio1":"$state"}""";
                    else -> "";
                };

                writer.write(command);
                writer.newLine();
                writer.flush();

                socket.close();
            } catch (e: Exception) {
                Log.e("ControlCommand", "Error: ${e.message}");
            }
        };
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Temperature Monitoring";
            val descriptionText = "Notification for temperature monitoring start";
            val importance = NotificationManager.IMPORTANCE_DEFAULT;
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText;
            };
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
            notificationManager.createNotificationChannel(channel);
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, content: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build());
        }
    }
}

class TemperatureMonitoringService : Service() {

    private val binder = LocalBinder();

    inner class LocalBinder : Binder() {
        fun getService(): TemperatureMonitoringService = this@TemperatureMonitoringService;
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder;
    }

    fun startMonitoring(ipAddress: String) {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                Log.d("TemperatureService", "Monitoring temperature at $ipAddress");
                delay(5000);
            }
        };
    }

    fun stopMonitoring() {
        Log.d("TemperatureService", "Monitoring stopped");
    }
}