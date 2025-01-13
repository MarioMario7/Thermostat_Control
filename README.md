# Link to Repository

_[Mobile App](https://github.com/MarioMario7/Thermostat_Control)_

# Link to Pico Pi W code

_[Pico Code](https://github.com/NegrilaRares/tempControlPiPicoW)_


# Thermostat_Control
An IOT project that is using a Raspberry Pi Pico W  to turn on a fan or heater based on the input being sent over from an android app.

The communication is established using TCP/IP sockets, enabling live data exchange between the app and the Pico. The Pico W acts as a server , listening for incoming socket connections and executing the appropriate actions—turning the fan or heater on or off—based on the commands sent from the app. It also sends a response whenever the client requests data. The android app can also use the information it recieves from the Pico in order to calibrate the temperature in order to reach a certain value inputed by the user at a time requested by the user. The Pico W code is written using MicroPython. 


# Andorid Component List


# Foreground Service 
Once the user checks the temperature, it creates a notification that will update with the temperature read from the Pico in real time (10s delay between readings). 

# Background Service

Once the user checks the temperature, it creates a background service that checks the inputed IP and stores it in the shared preferences for the auto fill on the inputs for IPs. (in all activities). 

# Bound Service


# Broadcast Recievers

The receiver checks for the internet connection and each activity listes for it, and in the case of no internet the buttons are not enabaled.


# Shared Preferences

Used to store a used IP address for autofill on all activities, persists across closing the app.

# Notifications

Displays a notification when the app sends the command to the pico to start calibrating the temperature at the time the user has selected beforehand.



