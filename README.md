# Future-DRO-Desktop
Java based Desktop Milling DRO, Bluetooth or USB (Ongoing)

# Overview

- Auto Connect USB Serial Port (Modes: Auto, Gui Select, CMD Parameter)
- Language Configuration (CMD Paramter)
- Fullscreen Mode (CMD Parameter)
- Cross Compiled Native for all Desktop OS (MacOS, Linux-x86, linux-arm, Windows)
- Raspberry Compatible, Touchscreen Compatible
- Diameter/Radius Tool Compensation
- Load and Save Reference Lists (CSV Compatible Files)
- Reference List Features.. Fill out missing Axes with Zeros or Previous List Entry
- Fast switching between Delta or Live Values for Main View  
- Arrow Buttons to set next or Previous Step on Reference List

# Touch friendly DRO Desktop Application

Future DRO Desktop is a touch-friendly Java Swing digital readout (DRO) for a three-axis milling or drilling machine. It displays live X, Y, and Z positions from TouchDroid ESP32 hardware and provides reference-point workflows for manually guided machining.

## License and Contact

This project is free software licensed under the [GNU General Public License version 3 or later](https://www.gnu.org/licenses/gpl-3.0.html). You may fork it, modify it, and share your changes on GitHub under the terms of the GPL.

For questions, collaboration, or commercial licensing discussions, use the [GitHub Issues](https://github.com/schuppeste/Future-DRO-Desktop/issues) page. The project name and logo are not granted as a trademark license by the GPL.

See [LICENSE](LICENSE) for the project license and the [official GPL-3.0-or-later text](https://www.gnu.org/licenses/gpl-3.0.txt).

The seven-segment display uses DSEG7 Classic by Keshikan, licensed under the [SIL Open Font License 1.1](https://scripts.sil.org/OFL). See `src/main/resources/fonts/DSEG-LICENSE.txt` for the included font license.

The application includes `jSerialComm 2.11.0` for USB serial communication. jSerialComm is licensed under the [GNU Lesser General Public License version 3](https://github.com/Fazecast/jSerialComm/blob/master/LICENSE). The dependency and its platform-specific native libraries are included in the all-platforms JAR.

## Features

- Large, scalable seven-segment X/Y/Z readout.
- Switchable actual-position and delta-to-target display.
- Direct X/Y/Z actual-position entry through either a physical keyboard or the on-screen keypad.
- Touch-friendly keypad, large navigation controls, and a scalable 4 by 3 menu grid.
- Reference-point list editor with selectable active point, point editing, deletion, and navigation.
- Empty coordinate handling for new points: require all values, use `0.000`, or inherit values from the preceding point.
- Named reference lists stored as CSV files in `referenzlisten/`.
- Configurable X/Y tool-radius compensation using either a radius or diameter input; Z remains uncompensated.
- USB serial connection to TouchDroid hardware through `jSerialComm`.


## Screenshots

<img width="3814" height="1994" alt="Future_DRO_Desktop2" src="https://github.com/user-attachments/assets/8e406e0d-b2ea-4c32-ad21-49d294d2c3f0" />
<img width="3810" height="1984" alt="Future DRO Desktop" src="https://github.com/user-attachments/assets/cdf79f23-6692-4a95-a86b-1e58ff5949ed" />


## Hardware Protocol

The ESP32 firmware sends one telemetry line every 50 ms through USB serial at `115200` baud:

```text
X=<micrometres>,Y=<micrometres>,Z=<micrometres>
```

Example:

```text
X=125000,Y=-4500,Z=30000
```

The desktop application converts these values to millimetres before displaying them. Use the `SER` menu item to select and connect to the ESP32 COM port.

## Reference Lists

Reference lists are stored in the application directory under `referenzlisten/` as CSV files:

```csv
X_mm,Y_mm,Z_mm
0.000,0.000,0.000
100.000,20.000,-5.000
```

Use the `LIST` menu item to manage multiple files without a system file browser. The application creates the folder automatically the first time a list is saved.

## Requirements

- Java 17 or later.
- `jSerialComm 2.11.0` for USB serial communication.
- Optional: a TouchDroid-compatible ESP32 device connected through USB.

The dependency is declared in `pom.xml`. For direct compilation, place `jSerialComm-2.11.0.jar` in `lib/`.

## Run

### All-platforms JAR

Use the versioned all-platforms JAR from the `target/` directory. Java 17 or later is required. Start it from a terminal so that startup and serial-port errors remain visible:

Windows PowerShell:

```powershell
java --enable-native-access=ALL-UNNAMED -jar .\target\dro-java-desktop-1.0.0-beta-allplatforms.jar
```

Linux and macOS:

```bash
java --enable-native-access=ALL-UNNAMED -jar ./target/dro-java-desktop-1.0.0-beta-allplatforms.jar
```

### Program arguments

The application supports these startup arguments:

- `--fullscreen`: start the application in fullscreen mode.
- `--lang=de` or `--lang de`: select the user interface language, for example `de` or `en`.
- `--port=COM3` or `--port COM3`: connect directly to the specified serial port. Use the port name shown by the operating system, such as `COM3` on Windows or `/dev/ttyUSB0` on Linux.

Example for Windows with fullscreen mode, German language, COM3, and serial native access enabled:

```powershell
java --enable-native-access=ALL-UNNAMED -jar .\target\dro-java-desktop-1.0.0-beta-allplatforms.jar --fullscreen --lang=de --port=COM3
```

Equivalent example for Linux or macOS:

```bash
java --enable-native-access=ALL-UNNAMED -jar ./target/dro-java-desktop-1.0.0-beta-allplatforms.jar --fullscreen --lang=en --port=/dev/ttyUSB0
```

The `--enable-native-access=ALL-UNNAMED` option allows `jSerialComm` to load its native serial-port library without native-access warnings on newer Java versions. If the downloaded JAR has a different version in its filename, replace the filename in the command accordingly.

Do not extract the JAR before starting it. The all-platforms JAR contains the Java classes and the native `jSerialComm` libraries for the supported operating systems and CPU architectures.

### Serial access troubleshooting

- Close other programs that may already use the selected serial port.
- On Windows, check the assigned `COM` port in Device Manager and select the same port in the application.
- On Linux, the user needs access to the serial device, usually through the `dialout` group:

	```bash
	sudo usermod -aG dialout "$USER"
	```

	Log out and back in after changing the group membership.
- On macOS, select the `/dev/cu.*` device belonging to the ESP32. Do not select a device that is already in use by another application.
- If Java reports `UnsatisfiedLinkError` or `Could not find jSerialComm`, make sure the complete all-platforms JAR was downloaded and start it with the command above.

### Maven

```bash
mvn package
java -cp target/classes com.drodesktop.Main
```

### Direct Java compilation

Windows PowerShell:

```powershell
$driver = '.\lib\jSerialComm-2.11.0.jar'
$files = Get-ChildItem -Path '.\src\main\java' -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
javac -cp $driver -d '.\target\classes' --release 17 $files
java --enable-native-access=ALL-UNNAMED -cp ".\target\classes;$driver" com.drodesktop.Main
```

## Screenshots


## Project Layout

- `src/main/java/com/drodesktop/Main.java`: application entry point.
- `src/main/java/com/drodesktop/ui/DROFrame.java`: Swing user interface and workflows.
- `src/main/java/com/drodesktop/DRO.java`: machine and reference-list state.
- `src/main/java/com/drodesktop/service/SerialDroReceiver.java`: USB serial telemetry receiver.
- `src/main/java/com/drodesktop/ui/SevenSegmentLabel.java`: scalable seven-segment display label.
