# 🏍️ DriveSphere • Next-Gen Connected Vehicle Safety & Cockpit HUD

<div align="center">

![DriveSphere Banner](https://img.shields.io/badge/DriveSphere-Automotive%20IoT%20Platform-00E676?style=for-the-badge&logo=android)
![Platform](https://img.shields.io/badge/Hardware-ESP32%20%7C%20ESP32--CAM-blue?style=for-the-badge&logo=espressif)
![License](https://img.shields.io/badge/Security-2FA%20Physical%20Auth-success?style=for-the-badge)

**Next-Generation Connected Vehicle Safety System, Real-Time Aero Digital Cluster HUD, ESP32 IoT Guardian Hub, AI Dashcam, and Android Jetpack Compose Client.**

[View on GitHub](https://github.com/yashvinthan/drivesphere) • [Architecture](#-system-architecture) • [Hardware Pinout](#-hardware-pinout--wiring) • [OLED Cluster UI](#-aero-digital-cluster-oled-hud) • [Anti-Hijack Security](#-bluetooth-anti-hijack-security)

</div>

---

## 🌟 Overview

**DriveSphere** bridges high-speed embedded microcontrollers with a sleek Nothing OS dot-matrix Android app to provide intelligent rider safety, live telemetry, and vehicle security:

1. **Physical IoT Cockpit Hub (ESP32 + 128×64 SSD1306 OLED)**:
   - **Aero Digital Cluster**: Live digital ribbon speedometer (0–120 km/h), dynamic motorcycle lean angle, G-force shock rating, and live score.
   - **Vector Logo Loading Screen**: Concentric Cyber Steering Core boot screen with smooth capsule loading progress bar.
   - **NEO-M8N GPS Integration**: Synchronized NMEA satellite tracking, speed calibration, and altitude telemetry.
   - **MPU-6050 6-DOF IMU**: Dual-bus I2C lean angle calculation (Roll/Pitch) and high-G crash shock latching.
   - **Physical Emergency SOS Button**: 3-second long-press tactile broadcast dispatching emergency GPS coordinates.
2. **Automotive Anti-Hijack Bluetooth Security**:
   - **2-Factor Physical Authorization**: Inbound pairing requests must be approved by clicking the physical button on the vehicle. Auto-rejects strangers after 15 seconds.
   - **Dynamic Rolling Cryptographic PIN**: Eliminates static PINs (`1234`), generating a unique random PIN visible only on the physical OLED screen.
   - **Stealth Non-Discoverable Mode**: Automatically turns invisible to Bluetooth scans once the owner connects.
3. **ESP32-CAM AI Rider Safety Dashcam**:
   - Real-time MJPEG video stream (~25 FPS) with remote flashlight control.
   - AI compliance badges for helmet detection, seatbelt status, and driver phone distraction alerts.
4. **Android App (Jetpack Compose & Room SQLite Database)**:
   - Real-time GPS navigation with turn-by-turn simulation and live sensor filtering.
   - Government e-Challan compliance & dispute tracking.
   - Interactive DriveCoins rewards store with coupon redemption.
   - Vehicle Anti-Theft Guard with 20-meter geofence perimeter alerts.

---

## 📐 System Architecture

```
+-------------------------------------------------------------------------+
|                           PHYSICAL HARDWARE                             |
|                                                                         |
|   +-----------------------------------------------------------------+   |
|   |                       ESP32 Guardian Hub                        |   |
|   |   - 128x64 SSD1306 OLED (I2C0: SDA=15, SCL=14)                  |   |
|   |   - MPU-6050 Gyro/Accel (I2C1: SDA=13, SCL=2)                   |   |
|   |   - NEO-M8N GPS Engine  (UART2: RX=16, TX=17)                   |   |
|   |   - Tactile Push Button (GPIO 12 with INPUT_PULLUP)             |   |
|   |   - Concurrent SoftAP Wi-Fi (192.168.4.1) & Bluetooth SPP       |   |
|   +-----------------------------------------------------------------+   |
|                                    ^                                    |
|                                    | (Dual Wi-Fi / Bluetooth SPP)       |
|                                    v                                    |
|   +-----------------------------------------------------------------+   |
|   |                     DriveSphere Android App                     |   |
|   |   - Nothing OS Dot-Matrix UI & Aero Dashboard                   |   |
|   |   - Dynamic Transport Switcher (Wi-Fi REST <-> Bluetooth SPP)   |   |
|   |   - Offline-First Room SQLite Database (e-Challans, Rewards)    |   |
|   |   - Multi-Guardian SOS Dispatch with Live GPS SMS Map Links     |   |
|   +-----------------------------------------------------------------+   |
|                                    ^                                    |
|                                    | (Local HTTP Video Stream)          |
|                                    v                                    |
|   +-----------------------------------------------------------------+   |
|   |                     ESP32-CAM AI Dashcam                        |   |
|   |   - OV2640 Lens, Night Safety LED, MJPEG Streamer               |   |
|   +-----------------------------------------------------------------+   |
+-------------------------------------------------------------------------+
```

---

## 🔌 Hardware Pinout & Wiring

| Component | ESP32 Pin | Function | Notes |
| :--- | :--- | :--- | :--- |
| **SSD1306 OLED (I2C0)** | `GPIO 15` | `SDA` | Primary Hardware I2C (Address `0x3C`) |
| | `GPIO 14` | `SCL` | 400 kHz Fast I2C Clock |
| | `VCC` | `3.3V / 5V` | Regulated Power |
| | `GND` | `GND` | Ground |
| **MPU-6050 IMU (I2C1)** | `GPIO 13` | `SDA` | Dedicated 2nd Hardware I2C (Address `0x68`) |
| | `GPIO 2` | `SCL` | Independent bus prevents OLED bus contention |
| **NEO-M8N GPS (UART2)** | `GPIO 16 (U2RXD)` | `TX` | Receives 9600 baud NMEA satellite sentences |
| | `5V` | `VCC` | Powers onboard LDO and active ceramic antenna |
| | `GND` | `GND` | Common Ground |
| **Push Button** | `GPIO 12` | `INPUT_PULLUP` | Active LOW. Short press cycles screens; Long press (3s) triggers SOS |
| **Download / Flash** | `GPIO 0` | `GND` (Bridge) | Bridge to GND only while flashing firmware |

---

## 📟 Aero Digital Cluster OLED HUD

The OLED display features 4 specialized screens:

### Screen 0: Aero HUD & Dynamic Speed Ribbon
```
+-------------------------------------------------------------+
| 🏍️ BIKE       ᛒ LINK                8S             [####|]   |  <- Status Header
|-------------------------------------------------------------|
| [========================......]                            |  <- Dynamic Speed Ribbon (0-120 km/h)
|                                                             |
|           ███  ███             GPS                          |
|             █  █ █             KM/H                         |  <- Giant Hero Digits
|           ███  ███             +------+                     |
|           █      █             | LIVE |                     |  <- Dynamic Status Pill
|           ███  ███             +------+                     |
|                                                             |
| +-------------------------+ . +---------------------------+ |  <- Symmetrical Cards
| |  < 18° L                | . | 1.0G  SC:95               | |
| +-------------------------+ . +---------------------------+ |
+-------------------------------------------------------------+
```

### Screen 1: Standby Idle Cockpit
- Automatically activates after 4 seconds of being stationary at 0 km/h.
- Features an animated cyber visor that blinks every 3.5 seconds with live battery, guard, and GPS satellite fix status.
- Instantly snaps back to Screen 0 the millisecond speed rises above 0 km/h.

### Screen 2: Tactical Gyro Horizon & Radar
- Visual crosshair gyro horizon rendering real-time roll (lean angle) and pitch angles at 20 Hz.
- Latitude and Longitude coordinate readouts.

### Screen 3: Cyber Glyph Matrix & Diagnostics
- 3×3 LED dot-matrix visual turn arrows, hazard symbols, and dynamic rolling Bluetooth PIN (`PIN: XXXXXX`).

---

## 🛡️ Bluetooth Anti-Hijack Security

DriveSphere implements automotive-grade 2-factor authorization:

```
+-------------------------------------------------------------+
|                    [!] PAIRING REQUEST                      |
|-------------------------------------------------------------|
| CODE: 582910                                                |
|                                                             |
|                   >> PRESS PUSH-BTN <<                      |  <- Physical Action Required!
|                    TO AUTHORIZE PHONE                       |
|                                                             |
| AUTO-REJECT: 15S                                            |  <- Auto-Blocks Strangers!
+-------------------------------------------------------------+
```

1. **Physical Button Authorization**: When a phone requests pairing, the ESP32 alerts the rider on the OLED. Only when the rider clicks the physical button on the vehicle is the connection approved.
2. **Auto-Rejection of Strangers**: If anyone nearby tries to pair, the ESP32 automatically times out and refuses access after 15 seconds.
3. **Dynamic Rolling PIN**: Cryptographically generated 6-digit random PIN shown only on the vehicle display.
4. **Stealth Mode**: Hides `DriveSphere-Hub` from Bluetooth discovery scans while the owner is connected.

---

## 🚀 Getting Started

### 1. Flash the ESP32 Firmware
```powershell
# Using the built-in automated flasher
python -u tools/flash_guardian.py
```
1. Bridge `GPIO 0` to `GND`.
2. Press the `RST` button on the ESP32 to flash.
3. Disconnect the bridge and press `RST` to boot.

### 2. Run the Android App
1. Open the project in **Android Studio**.
2. Sync Gradle dependencies.
3. Deploy to a physical Android device (Android 8.0+ recommended).
4. Tap **CONNECT HUB** on the dashboard and pair with `DriveSphere-Hub`!

---

## 📄 License & Credits
Developed with ❤️ by [yashvinthan](https://github.com/yashvinthan). Licensed under the MIT License.
