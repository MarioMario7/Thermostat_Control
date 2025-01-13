# Thermostat_Control
An IOT project that is using a Raspberry Pi Pico W  to turn on a fan or heater based on the input being sent over from an android app.

The communication is established using TCP/IP sockets, enabling live data exchange between the app and the Pico. The Pico W acts as a server , listening for incoming socket connections and executing the appropriate actions—turning the fan or heater on or off—based on the commands sent from the app. It also sends a response whenever the client requests data. The android app can also use the information it recieves from the Pico in order to calibrate the temperature in order to reach a certain value inputed by the user at a time requested by the user.
