# DriveSphere Guardian Hub — Hardware & Firmware Guide

This directory contains the firmware for the **ESP32 Guardian Hub**, the in-vehicle IoT unit powering DriveSphere's real-time safety display, telemetry transmission, and physical hardware emergency SOS trigger.

---

## 1. Hardware Bill of Materials (BOM)

| Component | Specification | Notes |
| :--- | :--- | :--- |
| **Microcontroller** | ESP32 Dev Module (WROOM-32 / 30-pin or 38-pin) | Built-in Wi-Fi & Bluetooth |
| **Display** | 0.96" I2C Monochrome OLED (SSD1306) | 128×64 resolution, Address `0x3C` |
| **IMU (6-DOF)** | MPU6050 (GY-521 Breakout) | 3-axis gyro + 3-axis accel, Address `0x68` |
| **GPS Module** | u-blox NEO-M8N with Ceramic Active Antenna | UART2 9600 baud, concurrent GNSS receiver |
| **Push Button** | Momentary Tactile Switch (2-pin or 4-pin) | Internal pull-up enabled (`INPUT_PULLUP`) |
| **Power Supply** | Micro-USB Cable / 5V Power Bank / 12V-to-5V step-down | Powers ESP32 via USB or VIN |
| **Jumper Wires** | Dupont wires (Female-to-Male, Female-to-Female) | For breadboard / handlebar wiring |

---

## 2. Complete Circuit Wiring & Pinout Guide (Dedicated Pins)

Each peripheral connects to its **own dedicated GPIO pins** on the ESP32-CAM:

### Pin Connections Table

| Module | Pin on Module | ESP32-CAM Pin | Function / Wire Note |
| :--- | :--- | :--- | :--- |
| **OLED Display (128x64)** | **VCC** | **3.3V** | Power |
| | **GND** | **GND** | Ground |
| | **SDA** | **GPIO 15** | Dedicated I2C Bus 0 Data |
| | **SCL** | **GPIO 14** | Dedicated I2C Bus 0 Clock |
| **MPU-6050 IMU (6-DOF)** | **VCC** | **3.3V** | Power |
| | **GND** | **GND** | Ground |
| | **SDA** | **GPIO 13** | **Dedicated I2C Bus 1 Data** (Completely separate from OLED!) |
| | **SCL** | **GPIO 2** | **Dedicated I2C Bus 1 Clock** (Completely separate from OLED!) |
| | **AD0** | **GND** (or open) | Sets I2C address to `0x68` |
| **NEO-M8N GPS Module** | **VCC** | **5V** or **3.3V** | Power (Ceramic antenna benefits from clean 5V/3.3V) |
| | **GND** | **GND** | Ground |
| | **TX** | **GPIO 16** | GPS NMEA output -> ESP32 U2RXD |
| | **RX** | Optional | Unconnected or GPIO 17 |
| **Momentary Push Button** | **Terminal 1** | **GPIO 12** | Dedicated input pin with internal `INPUT_PULLUP` |
| | **Terminal 2** | **GND** | Ground reference (button press shorts GPIO 12 to GND) |

> [!NOTE]
> **Dual Hardware I2C Controllers**: The ESP32 provides two independent hardware I2C controllers. OLED runs on `Wire` (pins 15, 14), while MPU-6050 runs on `WireMPU` (`Wire1`, pins 13, 2). There is zero crosstalk and no shared rails.



---

## 3. Firmware Flashing Instructions

### Prerequisites
1. Install [Arduino IDE](https://www.arduino.cc/en/software) (version 2.0+ recommended).
2. In Arduino IDE **Preferences**, add the ESP32 Board Manager URL:
   ```
   https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
   ```
3. Go to **Tools -> Board -> Boards Manager**, search for `esp32` by Espressif Systems, and click **Install**.

### Required Arduino Libraries
Go to **Sketch -> Include Library -> Manage Libraries...** and install:
1. **Adafruit SSD1306** by Adafruit (Select "Install All" to also install **Adafruit GFX Library** and **Adafruit BusIO**).
2. **Wire** and **WiFi** (built into the ESP32 board package).

### Uploading to ESP32

#### Method A: Automated CLI Flasher (Fastest)
From the project root, run:
```bash
python tools/wait_and_flash.py
```
Hold the **BOOT** button on your ESP32 for 2 seconds when connecting.

#### Method B: Arduino IDE
1. Connect your ESP32 to your PC via a data Micro-USB / USB-C cable.
2. Select **Tools -> Board -> ESP32 Arduino -> DOIT ESP32 DEVKIT V1** (or your specific ESP32 board).
3. Select **Tools -> Port** and choose your ESP32 COM port (e.g. `COM5` on Windows).
4. Open [`esp32_guardian_hub.ino`](file:///c:/Users/yashv/dev/work%20in%20progress/drivesphere/firmware/esp32_guardian_hub/esp32_guardian_hub.ino).
5. Click **Upload** (Arrow icon). If uploading hangs at `Connecting........_____`, press and hold the **BOOT** button on the ESP32 until the upload starts.

---

### 4. Partition Scheme Note (Critical for Dual Wi-Fi + Bluetooth)
Because both Wi-Fi and Bluetooth Classic stacks are compiled into the firmware, the binary size is ~1.68 MB.
- **In Arduino IDE**: Go to **Tools -> Partition Scheme** and select **Huge APP (3MB No OTA/1MB SPIFFS)**.
- **In Arduino CLI**: Compile and upload with `--fqbn esp32:esp32:esp32:PartitionScheme=huge_app`.

---

## 5. Dual Connectivity & In-App Pairing

The ESP32 Guardian Hub concurrently supports **both Wi-Fi and Bluetooth SPP**, allowing seamless in-app pairing without leaving the DriveSphere app:

### 1. In-App Wi-Fi SoftAP
- **SSID**: `DriveSphere-Hub`
- **Password**: `drivesphere123`
- **Default IP**: `192.168.4.1`
- **In-App Connect**: In the DriveSphere Android app under **HUB -> WI-FI REST**, tap **CONNECT IN-APP**. The app uses Android's `WifiNetworkSpecifier` to automatically bind the network process directly to the ESP32 hotspot.
- **HTTP REST Endpoints**:
  - `GET /status`: Returns JSON status (`{"connected":true,"sosTriggered":false,"speed":45,"score":92}`).
  - `POST /display`: Sends live speed, traffic score, and glyph symbols to the OLED.
  - `GET /sos`: Emergency trigger polling endpoint.
  - `POST /reset_sos`: Resets hardware SOS alert state.

### 2. In-App Bluetooth Serial (SPP)
- **Device Name**: `DriveSphere-Hub`
- **Protocol**: Bluetooth Classic Serial Port Profile (RFCOMM)
- **SPP UUID**: `00001101-0000-1000-8000-00805F9B34FB`
- **In-App Connect**: In the DriveSphere Android app under **HUB -> BLUETOOTH SPP**, tap any paired `DriveSphere-Hub` entry or tap **LINK BT IN-APP** to establish an instant RFCOMM socket connection.
- **Serial Commands (Bi-Directional)**:
  - Phone -> ESP32: `SPEED:<value>\n`, `SCORE:<value>\n`, `MODE:<text>\n`, `GLYPH:<name>\n`, `RESET_SOS\n`, `STATUS\n`
  - ESP32 -> Phone: Immediate JSON SOS broadcast `{"event":"SOS","triggered":true}\n`

---

## 6. Button Controls
- **Short Press (< 1 sec)**: Cycles between OLED screens:
  - **Screen 0**: Main Cockpit HUD (Speedometer, Traffic Score, Status Bar).
  - **Screen 1**: Telemetry & Sensors (Lean Angle, Tamper Guard status).
  - **Screen 2**: Active Glyph Matrix (Turn arrows, Nothing OS matrix animations).
- **Long Press (> 3 sec)**: **EMERGENCY S.O.S!**
  - Triggers an immediate emergency alert on the OLED screen.
  - Broadcasts SOS over both Wi-Fi HTTP polling and Bluetooth Serial socket.
  - The DriveSphere mobile app instantly triggers the 10-second countdown SOS screen with emergency SMS GPS dispatch.

---

## 7. MPU-6050 Crash & Fall Detection Calibration

To prevent false alarms caused by potholes, engine vibration, or normal motorcycle cornering, the MPU-6050 uses the following calibrated parameters:

| Parameter | Configuration / Value | Purpose & Rationale |
| :--- | :--- | :--- |
| **Full-Scale Range (AFS_SEL)** | **`±8g`** (`0x10` at Reg `0x1C`, `4096 LSB/g`) | Prevents sensor clipping/saturation on harsh bumps while capturing crash shock waves. |
| **DLPF (Low-Pass Filter)** | **`44 Hz`** (`0x03` at Reg `0x1A`) | Attenuates high-frequency engine rumble, chassis rattle, and acoustic noise spikes. |
| **Crash Impact Threshold** | **`> 5.2 G`** | Distinguishes actual vehicular collisions from road potholes/speed bumps (peak 2.5–3.8G). |
| **Crash Multi-Sample Debounce**| **2 consecutive samples (100ms window)** | Rejects single-sample electrical impulse spikes or dropped breadboard jolts. |
| **Motorcycle Fall Angle** | **`> 65°`** lean angle | Standard street riding leans up to 45°–50°; 65°+ indicates the bike is down. |
| **Fall Sustained Duration** | **2.0 seconds (`2000 ms`) continuous** | Rejects momentary deep cornering, swerving, or transient bumps; triggers only if bike stays down. |
| **Lean Angle Smoothing** | **EMA Filter (`α = 0.20`)** | Smooths out high-frequency road ripple from raw `atan2(ay, az)` computations. |

