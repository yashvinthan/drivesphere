# DriveSphere AI Dashcam & Rider Safety Cam — ESP32-CAM Guide

This directory contains the firmware for the **ESP32-CAM (AI-Thinker module with OV2640 camera)**, enabling live video streaming, road observation, and AI safety inspection (helmet detection, driver distraction, and seatbelt monitoring) for the DriveSphere ecosystem.

---

## 1. Hardware Specifications

| Component | Specification | Function |
| :--- | :--- | :--- |
| **Microcontroller** | ESP32-S (32-bit dual-core Xtensa @ 240MHz, 520KB SRAM + 4MB PSRAM) | Wi-Fi streaming & camera control |
| **Image Sensor** | Omnivision OV2640 (2 Megapixels) | SVGA/VGA high-framerate video & snapshot |
| **Illumination** | On-board high-power white LED (GPIO 4) | Night assist & visibility flashlight |
| **Status LED** | Red LED (GPIO 33, active LOW) | Boot & operational diagnostics |
| **Antenna** | On-board PCB antenna / IPEX external connector | Long-range vehicle connectivity |

---

## 2. Flashing & Wiring Pinout (FTDI / USB-UART Programmer)

Standard ESP32-CAM modules do not have an on-board USB port. Use an FTDI or USB-to-UART adapter (set to **5V**):

| ESP32-CAM Pin | FTDI / Programmer Pin | Description |
| :--- | :--- | :--- |
| **5V** | **5V** | Power supply (requires clean 5V @ ≥1A during camera bursts) |
| **GND** | **GND** | Common ground |
| **U0R (RX)** | **TX** | Serial receive |
| **U0T (TX)** | **RX** | Serial transmit |
| **GPIO 0** | **GND** *(During Flashing Only)* | Jumper connected to GND to enter download mode |

> [!IMPORTANT]
> - **During Flashing**: Connect a jumper wire between **GPIO 0** and **GND**, then plug in the USB programmer.
> - **After Flashing**: **Remove the GPIO 0 to GND jumper** and press the **RESET** button on the ESP32-CAM to run the firmware.

---

## 3. Uploading Firmware

### Automated CLI Upload
Run the dedicated flasher script from the project root:
```bash
python tools/flash_cam.py
```
(Connect GPIO 0 to GND when prompted, then remove it and press RESET after flashing).

### Arduino IDE
1. Open Arduino IDE.
2. Select **Tools -> Board -> ESP32 Arduino -> AI Thinker ESP32-CAM**.
3. Select **Tools -> CPU Frequency -> 240MHz (WiFi/BT)**.
4. Select **Tools -> Flash Frequency -> 80MHz**.
5. Select **Tools -> Partition Scheme -> Huge APP (3MB No OTA / 1MB SPIFFS)**.
6. Select your COM Port and click **Upload**.

---

## 4. REST API & Video Stream Endpoints

| Endpoint | Method | Format | Description |
| :--- | :--- | :--- | :--- |
| `/stream` | `GET` | `multipart/x-mixed-replace` | Real-time MJPEG live video stream (~25 FPS, non-blocking) |
| `/capture` | `GET` | `image/jpeg` | High-res JPEG snapshot for on-device AI classification |
| `/status` | `GET` | `application/json` | Camera readiness, FPS, IP, Wi-Fi mode, memory, and telemetry |
| `/flash` | `GET/POST` | `application/json` | Toggle or set LED brightness (`?state=1&brightness=128`) |
| `/flip` | `GET/POST` | `application/json` | Invert or mirror video feed (`?vflip=1&hmirror=0`) |
| `/control` | `GET` | `application/json` | Dynamic sensor settings (`?var=framesize&val=...`) |

---

## 5. Vehicle Mounting Recommendations

- **Two-Wheeler (Motorcycle / Scooter)**:
  - Mount on the handlebar or rearview mirror facing the rider to continuously monitor **helmet wear** and **mobile phone distraction**.
  - Weatherproofing: Use a clear 3D-printed or acrylic enclosure.
- **Four-Wheeler (Car)**:
  - Mount behind the rearview mirror facing the driver to inspect **seatbelt fastening**, **drowsiness**, and **distraction**.
