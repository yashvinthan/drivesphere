/*
 * ======================================================================================
 * DriveSphere — ESP32 Guardian Hub Firmware (v2.0 Dual-Sensor Edition)
 * ======================================================================================
 * Hardware Components:
 *   - ESP32 Development Board (WROOM-32 / 30-pin or 38-pin)
 *   - 0.96" 128x64 I2C Monochrome OLED Display (SSD1306, Address 0x3C)
 *   - MPU6050 6-DOF Accelerometer & Gyroscope (Address 0x68, shared I2C bus)
 *   - NEO-M8N GPS Module with Ceramic Active Antenna (UART2, 9600 baud)
 *   - Momentary Tactile Push Button (GPIO 13 with internal pull-up)
 *
 * Pin Connections:
 *   - I2C Bus (Shared between OLED Display & MPU6050):
 *       * SDA -> GPIO 15
 *       * SCL -> GPIO 14
 *       * VCC -> 3.3V
 *       * GND -> GND
 *   - Push Button:
 *       * Terminal 1 -> GPIO 13 (INPUT_PULLUP)
 *       * Terminal 2 -> GND
 *   - NEO-M8N GPS Module:
 *       * VCC -> 3.3V or 5V (Active antenna benefits from stable power)
 *       * GND -> GND
 *       * TX  -> GPIO 16 (ESP32 RX2)
 *       * RX  -> GPIO 17 (ESP32 TX2)
 *
 * Connectivity:
 *   - Wi-Fi SoftAP: "DriveSphere-Hub" (Password: "drivesphere123"), IP 192.168.4.1
 *   - Bluetooth Classic Serial Port Profile (SPP): "DriveSphere-Hub"
 *   - REST Endpoints: /status, /telemetry, /display, /sos, /reset_sos
 * ======================================================================================
 */

#include <WiFi.h>
#include <WebServer.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "BluetoothSerial.h"
#include <TinyGPSPlus.h>

// ---------------------- Hardware Configuration ----------------------
#define SCREEN_WIDTH    128
#define SCREEN_HEIGHT   64
#define OLED_RESET      -1
#define SCREEN_ADDRESS  0x3C // Standard I2C address for SSD1306

// OLED Display Pins (Hardware I2C Bus 0)
#define I2C_SDA_PIN     15   // OLED SDA on GPIO 15
#define I2C_SCL_PIN     14   // OLED SCL on GPIO 14

// MPU-6050 IMU Pins (Dedicated Hardware I2C Bus 1 - Separate from OLED!)
#define MPU_SDA_PIN     13   // MPU-6050 SDA on GPIO 13
#define MPU_SCL_PIN     2    // MPU-6050 SCL on GPIO 2

// Push Button Pin (Dedicated GPIO)
#define BUTTON_PIN      12   // Push button on GPIO 12 (INPUT_PULLUP)
#define LONG_PRESS_MS   3000 // 3 seconds for SOS trigger

// NEO-M8N GPS Module Pins (Hardware UART2)
#define GPS_RX_PIN      16   // NEO-M8N TX -> ESP32 GPIO 16 (U2RXD)
#define GPS_TX_PIN      17   // NEO-M8N RX -> ESP32 GPIO 17 (TX2)
#define GPS_BAUD        9600

#define MPU6050_ADDR         0x68
#define MPU6050_PWR_MGMT_1   0x6B
#define MPU6050_ACCEL_XOUT_H 0x3B

// ---------------------- Peripherals Initialization ----------------------
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
TwoWire WireMPU = TwoWire(1); // Dedicated 2nd hardware I2C bus for MPU-6050
WebServer server(80);
BluetoothSerial SerialBT;
HardwareSerial SerialGPS(2);
TinyGPSPlus gps;

// ---------------------- Network & Identity ----------------------
const char* apSSID = "DriveSphere-Hub";
const char* apPassword = "drivesphere123";

// ---------------------- State & Telemetry ----------------------
String currentLine1 = "DRIVESPHERE";
String currentLine2 = "SYSTEM READY";
String currentGlyph = "IDLE";
int currentSpeed = 0;
int currentScore = 92;
String vehicleMode = "BIKE"; // "BIKE" or "CAR"

bool isSosTriggered = false;
bool isTamperDetected = false;
bool isVehicleGuardArmed = false;
int activeDisplayMode = 0; // 0: Main HUD, 1: Idle Screen, 2: Telemetry, 3: Glyph Matrix

// MPU-6050 Telemetry
bool mpuAvailable = false;
float currentLeanAngle = 0.0; // Roll in degrees
float currentPitch = 0.0;     // Pitch in degrees
float currentAccelG = 1.0;    // Dynamic acceleration magnitude
bool isCrashDetected = false;
float peakCrashG = 0.0;       // Latched peak impact shock G
float peakTiltDeg = 0.0;      // Latched peak fall tilt angle
unsigned long lastMotionTime = 0; // Timestamp of last detected motion
unsigned long lastMpuReadTime = 0;

// NEO-M8N GPS Telemetry
double currentGpsLat = 0.0;
double currentGpsLng = 0.0;
float currentGpsSpeedKmH = 0.0;
float currentGpsAltM = 0.0;
int currentGpsSats = 0;
bool currentGpsFix = false;

// Button Debounce & Timing
unsigned long buttonPressStartTime = 0;
bool isButtonPressed = false;
bool longPressTriggered = false;
const unsigned long debounceDelay = 50;
unsigned long lastDebounceTime = 0;
int lastButtonState = HIGH;

// Periodic Update Timers
unsigned long lastDisplayRefreshTime = 0;
unsigned long lastBtBroadcastTime = 0;

// Bluetooth Pairing Security & Passkey State
const char* btFixedPin = "1234";
volatile bool isBtPairingActive = false;
volatile uint32_t btPairingCode = 0;
unsigned long btPairingStartTime = 0;
volatile bool btPairingSuccess = false;
unsigned long btAuthCompleteTime = 0;
volatile bool btPairingNeedsUpdate = false;

// ---------------------- MPU-6050 Routines (Dedicated Bus 1) ----------------------
void initMPU6050() {
  WireMPU.begin(MPU_SDA_PIN, MPU_SCL_PIN);
  WireMPU.beginTransmission(MPU6050_ADDR);
  WireMPU.write(MPU6050_PWR_MGMT_1);
  WireMPU.write(0); // Wake up MPU-6050 (clears sleep bit)
  byte error = WireMPU.endTransmission();
  if (error == 0) {
    mpuAvailable = true;
    Serial.println(F("[DriveSphere] MPU6050 IMU initialized on dedicated Wire1 (SDA:13, SCL:2)"));
  } else {
    // Try alternate address 0x69
    WireMPU.beginTransmission(0x69);
    WireMPU.write(MPU6050_PWR_MGMT_1);
    WireMPU.write(0);
    if (WireMPU.endTransmission() == 0) {
      mpuAvailable = true;
      Serial.println(F("[DriveSphere] MPU6050 IMU initialized on 0x69 on dedicated Wire1"));
    } else {
      Serial.println(F("[DriveSphere] MPU6050 not responding on dedicated Wire1 (SDA:13, SCL:2); continuing"));
    }
  }
}

void readMPU6050() {
  if (!mpuAvailable) return;
  if (millis() - lastMpuReadTime < 50) return; // 20 Hz sample rate
  lastMpuReadTime = millis();

  WireMPU.beginTransmission(MPU6050_ADDR);
  WireMPU.write(MPU6050_ACCEL_XOUT_H);
  if (WireMPU.endTransmission(false) != 0) return;

  if (WireMPU.requestFrom((uint16_t)MPU6050_ADDR, (uint8_t)14, true) == 14) {
    int16_t ax = (WireMPU.read() << 8) | WireMPU.read();
    int16_t ay = (WireMPU.read() << 8) | WireMPU.read();
    int16_t az = (WireMPU.read() << 8) | WireMPU.read();
    int16_t temp = (WireMPU.read() << 8) | WireMPU.read();
    int16_t gx = (WireMPU.read() << 8) | WireMPU.read();
    int16_t gy = (WireMPU.read() << 8) | WireMPU.read();
    int16_t gz = (WireMPU.read() << 8) | WireMPU.read();

    float ax_g = (float)ax / 16384.0f;
    float ay_g = (float)ay / 16384.0f;
    float az_g = (float)az / 16384.0f;
    currentAccelG = sqrt(ax_g * ax_g + ay_g * ay_g + az_g * az_g);

    // Roll (motorcycle lean angle in degrees)
    currentLeanAngle = atan2(ay_g, az_g) * 180.0f / 3.14159265f;
    // Pitch (elevation angle in degrees)
    currentPitch = atan2(-ax_g, sqrt(ay_g * ay_g + az_g * az_g)) * 180.0f / 3.14159265f;

    // Automatic Fall Detection: Motorcycle tilted > 55 deg
    if (vehicleMode == "BIKE" && abs(currentLeanAngle) > 55.0f) {
      if (abs(currentLeanAngle) > peakTiltDeg) peakTiltDeg = abs(currentLeanAngle);
      if (!isSosTriggered) {
        isSosTriggered = true;
        isCrashDetected = true;
        SerialBT.println("{\"event\":\"FALL\",\"sosTriggered\":true}");
      }
    }
    if (isCrashDetected && abs(currentLeanAngle) > peakTiltDeg) {
      peakTiltDeg = abs(currentLeanAngle);
    }

    // High-G Impact Crash Detection (> 3.5 G shock)
    if (currentAccelG > 3.5f) {
      if (currentAccelG > peakCrashG) peakCrashG = currentAccelG;
      if (!isSosTriggered) {
        isSosTriggered = true;
        isCrashDetected = true;
        SerialBT.println("{\"event\":\"CRASH\",\"sosTriggered\":true}");
      }
    }
    if (isCrashDetected && currentAccelG > peakCrashG) {
      peakCrashG = currentAccelG;
    }

    // Anti-Theft Tamper Motion Detection
    if (isVehicleGuardArmed && abs(currentAccelG - 1.0f) > 0.35f) {
      isTamperDetected = true;
    }
  }
}

unsigned long totalGpsChars = 0;
unsigned long lastGpsDebugTime = 0;

// ---------------------- NEO-M8N GPS Routines ----------------------
void readGPS() {
  while (SerialGPS.available() > 0) {
    char c = SerialGPS.read();
    totalGpsChars++;
    gps.encode(c);
  }

  if (gps.location.isValid()) {
    currentGpsLat = gps.location.lat();
    currentGpsLng = gps.location.lng();
    currentGpsFix = true;
  } else {
    currentGpsFix = false;
  }

  if (gps.speed.isValid()) {
    currentGpsSpeedKmH = gps.speed.kmph();
    // If valid fix and moving, synchronize speedometer with actual GPS ground speed
    if (currentGpsFix && currentGpsSpeedKmH > 1.5f) {
      currentSpeed = (int)currentGpsSpeedKmH;
      lastMotionTime = millis();
    }
  }

  if (gps.satellites.isValid()) {
    currentGpsSats = gps.satellites.value();
  }

  if (gps.altitude.isValid()) {
    currentGpsAltM = gps.altitude.meters();
  }

  // Periodic Serial Diagnostic Monitor (every 1.5s)
  if (millis() - lastGpsDebugTime > 1500) {
    lastGpsDebugTime = millis();
    Serial.print(F("[GPS CHECK] Chars: "));
    Serial.print(totalGpsChars);
    if (totalGpsChars == 0) {
      Serial.println(F(" -> NO DATA ON GPIO 16! Verify NEO-M8N TX -> ESP32 GPIO 16 & Power (5V/GND)."));
    } else {
      Serial.print(F(" | Sats: "));
      Serial.print(currentGpsSats);
      Serial.print(F(" | Fix: "));
      Serial.print(currentGpsFix ? "YES (3D LOCK)" : "SEARCHING (Put ceramic antenna outdoors/window)");
      if (currentGpsFix) {
        Serial.print(F(" | Lat: "));
        Serial.print(currentGpsLat, 6);
        Serial.print(F(" Lng: "));
        Serial.print(currentGpsLng, 6);
        Serial.print(F(" Spd: "));
        Serial.print(currentGpsSpeedKmH, 1);
        Serial.print(F(" km/h"));
      }
      Serial.println();
    }
  }
}

// ---------------------- Pixel-Art Hardware Icons ----------------------
void drawBluetoothIcon(int x, int y, bool connected) {
  if (connected) {
    // Official Bluetooth rune (7x9 px)
    display.drawLine(x + 3, y,     x + 3, y + 8, SSD1306_WHITE);
    display.drawLine(x + 3, y,     x + 6, y + 2, SSD1306_WHITE);
    display.drawLine(x + 6, y + 2, x + 1, y + 5, SSD1306_WHITE);
    display.drawLine(x + 1, y + 3, x + 6, y + 6, SSD1306_WHITE);
    display.drawLine(x + 6, y + 6, x + 3, y + 8, SSD1306_WHITE);
  } else {
    // Single ready rune
    display.drawFastVLine(x + 3, y, 9, SSD1306_WHITE);
    display.drawLine(x + 3, y + 1, x + 5, y + 3, SSD1306_WHITE);
    display.drawLine(x + 5, y + 3, x + 3, y + 5, SSD1306_WHITE);
  }
}

void drawGpsSignalBars(int x, int y, int sats, bool fix) {
  // 4-level cellular / satellite signal bars (7x8 px)
  display.drawFastVLine(x,     y + 6, 2, SSD1306_WHITE);
  if (sats >= 1 || fix) display.drawFastVLine(x + 2, y + 4, 4, SSD1306_WHITE);
  if (sats >= 4 || fix) display.drawFastVLine(x + 4, y + 2, 6, SSD1306_WHITE);
  if (sats >= 6 || fix) display.drawFastVLine(x + 6, y,     8, SSD1306_WHITE);
}

void drawBatteryIcon(int x, int y, int level) {
  // Battery frame with positive terminal (12x7 px)
  display.drawRoundRect(x, y, 11, 7, 1, SSD1306_WHITE);
  display.drawFastVLine(x + 11, y + 2, 3, SSD1306_WHITE);
  display.fillRect(x + 2, y + 2, 7, 3, SSD1306_WHITE);
}

void drawVehicleIcon(int x, int y, bool isBike) {
  if (isBike) {
    // Motorcycle silhouette (12x8 px)
    display.drawCircle(x + 2, y + 5, 2, SSD1306_WHITE);
    display.drawCircle(x + 10, y + 5, 2, SSD1306_WHITE);
    display.drawLine(x + 2, y + 5, x + 6, y + 2, SSD1306_WHITE);
    display.drawLine(x + 6, y + 2, x + 10, y + 5, SSD1306_WHITE);
    display.drawFastHLine(x + 5, y + 1, 3, SSD1306_WHITE);
  } else {
    // Car silhouette (12x8 px)
    display.drawRoundRect(x, y + 2, 12, 4, 1, SSD1306_WHITE);
    display.drawFastHLine(x + 3, y, 6, SSD1306_WHITE);
    display.drawLine(x + 1, y + 2, x + 3, y, SSD1306_WHITE);
    display.drawLine(x + 10, y + 2, x + 9, y, SSD1306_WHITE);
    display.fillCircle(x + 2, y + 6, 1, SSD1306_WHITE);
    display.fillCircle(x + 9, y + 6, 1, SSD1306_WHITE);
  }
}

// ---------------------- Premium OLED UI/UX Rendering ----------------------
void drawHeader() {
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);

  // 1. Vehicle Mode Pill & Icon (Left)
  drawVehicleIcon(2, 1, vehicleMode == "BIKE");
  display.setCursor(16, 1);
  display.print(vehicleMode == "BIKE" ? "BIKE" : "CAR");

  // 2. Connectivity Icon & Status (Center)
  drawBluetoothIcon(46, 0, SerialBT.hasClient());
  display.setCursor(55, 1);
  if (SerialBT.hasClient()) {
    display.print("LINK");
  } else if (WiFi.softAPgetStationNum() > 0) {
    display.print("WIFI");
  } else {
    display.print("RDY");
  }

  // 3. GPS Status & Signal Bars (Right)
  drawGpsSignalBars(80, 1, currentGpsSats, currentGpsFix);
  display.setCursor(88, 1);
  if (currentGpsFix) {
    display.print(currentGpsSats);
    display.print("S");
  } else if (totalGpsChars > 0) {
    display.print("ACQ");
  } else {
    display.print("--");
  }

  // 4. Hardware Battery Icon (Far Right)
  drawBatteryIcon(114, 1, 100);

  // Top Separator Bar
  display.drawFastHLine(0, 10, 128, SSD1306_WHITE);
}

void drawPagination(int activePage) {
  for (int i = 0; i < 4; i++) {
    if (i == activePage) {
      display.fillCircle(110 + (i * 4), 57, 1, SSD1306_WHITE);
    } else {
      display.drawPixel(110 + (i * 4), 57, SSD1306_WHITE);
    }
  }
}

void drawCardPagination(int activePage) {
  for (int i = 0; i < 4; i++) {
    int y = 51 + (i * 3);
    if (i == activePage) {
      display.fillRect(63, y, 2, 2, SSD1306_WHITE);
    } else {
      display.drawPixel(63, y, SSD1306_WHITE);
    }
  }
}

void clearSos() {
  isSosTriggered = false;
  isCrashDetected = false;
  peakCrashG = 0.0f;
  peakTiltDeg = 0.0f;
  updateOLED();
}

// Emergency High-Contrast Flashing S.O.S Screen
void renderSosScreen() {
  display.clearDisplay();
  bool flash = (millis() / 350) % 2 == 0;
  if (flash) {
    display.fillRect(0, 0, 128, 64, SSD1306_WHITE);
    display.setTextColor(SSD1306_BLACK);
  } else {
    display.setTextColor(SSD1306_WHITE);
    display.drawRect(0, 0, 128, 64, SSD1306_WHITE);
  }

  int triColor = flash ? SSD1306_BLACK : SSD1306_WHITE;

  // Warning triangle icons flanking S.O.S header
  display.drawTriangle(16, 4, 9, 17, 23, 17, triColor);
  display.drawFastVLine(16, 8, 5, triColor);
  display.drawPixel(16, 15, triColor);

  display.drawTriangle(112, 4, 105, 17, 119, 17, triColor);
  display.drawFastVLine(112, 8, 5, triColor);
  display.drawPixel(112, 15, triColor);

  display.setTextSize(2);
  display.setCursor(35, 4);
  display.print("S.O.S");

  display.setTextSize(1);
  display.setCursor(8, 24);
  if (isCrashDetected && peakCrashG > 0.0f) {
    display.print("CRASH SHOCK: ");
    display.print(peakCrashG, 1);
    display.print("G");
  } else if (vehicleMode == "BIKE" && peakTiltDeg > 50.0f) {
    display.print("FALL TILT: ");
    display.print((int)peakTiltDeg);
    display.print((char)247);
  } else {
    display.print("MANUAL SOS TRIGGERED");
  }

  display.setCursor(8, 36);
  if (currentGpsFix) {
    display.print("LOC: ");
    display.print(currentGpsLat, 3);
    display.print(", ");
    display.print(currentGpsLng, 3);
  } else {
    display.print("EMERGENCY SIGNAL SENT");
  }

  // Cancel Button Pill at bottom - 18 chars * 6 = 108 px -> centered at X: 10 (no wrapping 'L'!)
  display.drawRoundRect(6, 48, 116, 13, 2, triColor);
  display.setCursor(10, 51);
  display.print("HOLD BTN TO CANCEL");

  display.display();
}

// SCREEN 0: Aero Digital Cluster HUD (128x64 High-Contrast Cockpit)
void renderMainHUD() {
  display.clearDisplay();
  drawHeader();

  // 1. Dynamic Full-Width Speed Ribbon (Y: 12 - 17)
  display.drawRoundRect(2, 12, 124, 5, 1, SSD1306_WHITE);
  int ribbonW = map(constrain(currentSpeed, 0, 120), 0, 120, 0, 120);
  if (ribbonW > 0) {
    display.fillRect(4, 13, ribbonW, 3, SSD1306_WHITE);
  }
  // Subtle speed tick marks below ribbon (0, 60, 120 km/h)
  display.drawFastVLine(2, 17, 2, SSD1306_WHITE);
  display.drawFastVLine(63, 17, 2, SSD1306_WHITE);
  display.drawFastVLine(125, 17, 2, SSD1306_WHITE);

  // 2. Hero Digital Speedometer Readout (Y: 20 - 47)
  display.setTextColor(SSD1306_WHITE);
  display.setTextSize(4);
  // Calculate horizontal start position to center digits in the hero area (0 to 82)
  int speedX = 30; // default for 1 digit
  if (currentSpeed >= 100) {
    speedX = 6;
  } else if (currentSpeed >= 10) {
    speedX = 18;
  } else {
    speedX = 30;
  }
  display.setCursor(speedX, 20);
  display.print(currentSpeed);

  // Speedometer Units & Status Pill (X: 86 to 124)
  display.setTextSize(1);
  display.setCursor(86, 21);
  display.print(currentGpsFix ? "GPS" : "SPD");
  display.setCursor(86, 31);
  display.print("KM/H");

  // Dynamic driving status pill
  display.drawRoundRect(85, 40, 39, 9, 2, SSD1306_WHITE);
  display.setCursor(88, 41);
  if (currentSpeed > 80) {
    display.print("FAST");
  } else if (isVehicleGuardArmed) {
    display.print("ARM");
  } else {
    display.print("LIVE");
  }

  // 3. Bottom Telemetry Cards (Y: 50 - 63)
  if (isTamperDetected) {
    // High-visibility inverted tamper alert banner
    display.fillRoundRect(1, 50, 126, 13, 2, SSD1306_WHITE);
    display.setTextColor(SSD1306_BLACK);
    display.setCursor(10, 53);
    display.print("! TAMPER DETECTED !");
    display.setTextColor(SSD1306_WHITE);
  } else {
    // Left Card: Lean Angle & Directional Indicator (60px wide)
    display.drawRoundRect(1, 50, 60, 13, 2, SSD1306_WHITE);
    display.setTextSize(1);
    if (vehicleMode == "BIKE") {
      float absLean = abs(currentLeanAngle);
      if (absLean < 2.5f) {
        display.setCursor(4, 53);
        display.print("^ 0");
        display.print((char)247);
        display.print(" BAL");
      } else if (currentLeanAngle < -2.5f) {
        display.setCursor(4, 53);
        display.print("< ");
        display.print((int)absLean);
        display.print((char)247);
        display.print(" L");
      } else {
        display.setCursor(4, 53);
        display.print("R ");
        display.print((int)absLean);
        display.print((char)247);
        display.print(" >");
      }
    } else {
      display.setCursor(4, 53);
      display.print("PIT: ");
      display.print((int)currentPitch);
      display.print((char)247);
    }

    // Right Card: G-Force & Safety Score (60px wide)
    display.drawRoundRect(67, 50, 60, 13, 2, SSD1306_WHITE);
    display.setCursor(70, 53);
    display.print(currentAccelG, 1);
    display.print("G ");
    display.print("SC:");
    display.print(currentScore);

    // Center pagination indicator
    drawCardPagination(0);
  }

  display.display();
}

// SCREEN 1: Idle Cockpit & System Standby (Parked / Stopped)
void renderIdleScreen() {
  display.clearDisplay();
  drawHeader();

  // 1. Center Animated Cyber Visor / Face (X: 4 to 42, Y: 14 to 45)
  display.drawRoundRect(4, 14, 38, 30, 4, SSD1306_WHITE);
  // Visor eye pupils (blinks periodically every 3.5s)
  bool blink = (millis() % 3500) < 180;
  if (blink) {
    display.drawFastHLine(11, 27, 7, SSD1306_WHITE);
    display.drawFastHLine(25, 27, 7, SSD1306_WHITE);
  } else {
    display.fillCircle(14, 27, 3, SSD1306_WHITE);
    display.fillCircle(28, 27, 3, SSD1306_WHITE);
  }
  // Subtle smile / breath accent
  display.drawFastHLine(18, 36, 10, SSD1306_WHITE);

  // 2. Right Side Standby Badges (X: 48 to 124)
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(48, 14);
  display.print("DRIVESPHERE");

  // Inverted standby pill (X: 48 to 118, W: 70)
  display.fillRoundRect(48, 24, 70, 10, 2, SSD1306_WHITE);
  display.setTextColor(SSD1306_BLACK);
  display.setCursor(56, 25);
  if (isVehicleGuardArmed) {
    display.print("GUARD: ON");
  } else {
    display.print("STANDBY");
  }
  display.setTextColor(SSD1306_WHITE);

  // Live Score & Status Line
  display.setCursor(48, 37);
  display.print("SCORE ");
  display.print(currentScore);
  display.print(" PTS");

  // 3. Bottom Dual Cards (Y: 50 to 63, H: 13) - Single clean line per card
  // Left Card: GPS Lock Status (60px wide)
  display.drawRoundRect(1, 50, 60, 13, 2, SSD1306_WHITE);
  display.setCursor(4, 53);
  if (currentGpsFix) {
    display.print("3D FIX ");
    display.print(currentGpsSats);
    display.print("S");
  } else if (totalGpsChars > 0) {
    display.print("GPS: ACQ");
  } else {
    display.print("GPS: IDLE");
  }

  // Right Card: Guard / Motion Readiness (60px wide)
  display.drawRoundRect(67, 50, 60, 13, 2, SSD1306_WHITE);
  display.setCursor(70, 53);
  if (isVehicleGuardArmed) {
    display.print(isTamperDetected ? "! ALERT !" : "GUARD: ON");
  } else {
    display.print("0KM/H PARK");
  }

  // Center vertical pagination
  drawCardPagination(1);
  display.display();
}

// SCREEN 2: Tactical Gyro Horizon & Live GPS Radar
void renderTelemetryScreen() {
  display.clearDisplay();
  drawHeader();

  // Left Half: Artificial Gyro Horizon (Center at X: 28, Y: 33, R: 18)
  display.drawCircle(28, 33, 18, SSD1306_WHITE);
  display.drawFastHLine(25, 33, 7, SSD1306_WHITE); // Center crosshair
  display.drawFastVLine(28, 30, 7, SSD1306_WHITE);

  // Tilted Artificial Horizon Line
  float rad = currentLeanAngle * 3.14159265f / 180.0f;
  int dx = (int)(cos(rad) * 16.0f);
  int dy = (int)(sin(rad) * 16.0f);
  display.drawLine(28 - dx, 33 - dy, 28 + dx, 33 + dy, SSD1306_WHITE);

  // Horizon Metrics Below Circle
  display.setCursor(2, 54);
  display.print(abs((int)currentLeanAngle));
  display.print((char)247);
  display.print(currentLeanAngle < -2.0f ? "L" : (currentLeanAngle > 2.0f ? "R" : "-"));
  display.setCursor(30, 54);
  display.print(currentAccelG, 1);
  display.print("G");

  // Vertical Divider
  display.drawFastVLine(52, 12, 39, SSD1306_WHITE);

  // Right Half: High-Density GPS Telemetry (Clean bounded text)
  display.setCursor(56, 13);
  display.print(currentGpsFix ? "3D GPS LOCK" : "SEARCHING");

  display.setCursor(56, 23);
  display.print("SATS:");
  display.print(currentGpsSats);
  display.print(" ");
  display.print((int)currentGpsAltM);
  display.print("M");

  display.setCursor(56, 33);
  display.print("LA:");
  display.print(currentGpsLat, 3);

  display.setCursor(56, 43);
  display.print("LO:");
  display.print(currentGpsLng, 3);

  // Bottom Status
  display.drawFastHLine(0, 51, 128, SSD1306_WHITE);
  display.setCursor(56, 54);
  display.print("HORIZON/GPS");
  drawPagination(2);
  display.display();
}

// SCREEN 3: Cyber Glyph Matrix & Bus Diagnostics
void renderGlyphScreen() {
  display.clearDisplay();
  drawHeader();

  // Left Half: Stylized Nothing-style Glyph Geometry
  display.drawRoundRect(6, 13, 44, 36, 4, SSD1306_WHITE);
  display.drawCircle(28, 24, 6, SSD1306_WHITE);       // Camera module halo
  display.drawFastHLine(12, 34, 32, SSD1306_WHITE);    // Horizontal blade
  display.drawFastVLine(28, 34, 12, SSD1306_WHITE);    // Vertical spine

  // Dynamic light strobe animation
  int strobe = (millis() / 180) % 4;
  if (strobe == 0) display.fillCircle(12, 19, 2, SSD1306_WHITE);
  if (strobe == 1) display.fillCircle(44, 19, 2, SSD1306_WHITE);
  if (strobe == 2) display.fillCircle(28, 43, 2, SSD1306_WHITE);
  if (strobe == 3) display.fillCircle(28, 24, 3, SSD1306_WHITE);

  // Vertical Divider
  display.drawFastVLine(54, 12, 39, SSD1306_WHITE);

  // Right Half: System Hardware Diagnostics (Bounded 10-char lines)
  display.setCursor(58, 13);
  display.print("OLED: OK");

  display.setCursor(58, 23);
  display.print(mpuAvailable ? "MPU : OK" : "MPU : NO");

  display.setCursor(58, 33);
  display.print(totalGpsChars > 0 ? "GPS : OK" : "GPS : NO");

  display.setCursor(58, 43);
  display.print(SerialBT.hasClient() ? "BT  : LINK" : "BT  : RDY");

  // Bottom Strip
  display.drawFastHLine(0, 51, 128, SSD1306_WHITE);
  display.setCursor(2, 54);
  display.print("PIN:1234");
  display.setCursor(62, 54);
  display.print("G:");
  display.print(currentGlyph);
  drawPagination(3);
  display.display();
}

// ---------------------- Bluetooth Pairing Screen ----------------------
void renderBtPairingScreen() {
  display.clearDisplay();

  if (isBtPairingActive) {
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(8, 2);
    display.print(F("* BT PAIRING CODE *"));
    display.drawFastHLine(0, 11, 128, SSD1306_WHITE);

    display.setCursor(14, 14);
    display.print(F("CONFIRM ON PHONE:"));

    display.drawRoundRect(10, 25, 108, 24, 4, SSD1306_WHITE);
    display.setTextSize(2);
    display.setCursor(28, 29);
    char pinBuf[12];
    if (btPairingCode > 0) {
      snprintf(pinBuf, sizeof(pinBuf), "%06u", btPairingCode);
    } else {
      snprintf(pinBuf, sizeof(pinBuf), "  1234  ");
    }
    display.print(pinBuf);

    display.setTextSize(1);
    display.setCursor(14, 53);
    display.print(F("DEFAULT PIN: 1234"));
  } else if (millis() - btAuthCompleteTime < 2500 && btAuthCompleteTime > 0) {
    display.drawRoundRect(8, 8, 112, 48, 6, SSD1306_WHITE);
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    if (btPairingSuccess) {
      display.setCursor(14, 16);
      display.print(F("[*] BLUETOOTH PAIRED"));
      display.drawFastHLine(14, 26, 100, SSD1306_WHITE);
      display.setCursor(16, 33);
      display.print(F("PHONE LINKED READY!"));
      display.setCursor(18, 44);
      display.print(F("STARTING TELEMETRY"));
    } else {
      display.setCursor(16, 18);
      display.print(F("[!] PAIRING FAILED"));
      display.drawFastHLine(16, 28, 96, SSD1306_WHITE);
      display.setCursor(18, 36);
      display.print(F("TRY PIN: 1234"));
    }
  }
  display.display();
}

void updateOLED() {
  if (isSosTriggered) {
    renderSosScreen();
    return;
  }

  // Bluetooth Pairing prompt takes priority!
  if (isBtPairingActive || (millis() - btAuthCompleteTime < 2500 && btAuthCompleteTime > 0)) {
    renderBtPairingScreen();
    return;
  }

  // Auto-idle behavior: If on Screen 0 (Aero HUD), vehicle is stopped, and stationary for > 4s
  if (activeDisplayMode == 0 && currentSpeed == 0 && (millis() - lastMotionTime > 4000)) {
    renderIdleScreen();
    return;
  }

  switch (activeDisplayMode) {
    case 0:
      renderMainHUD();
      break;
    case 1:
      renderIdleScreen();
      break;
    case 2:
      renderTelemetryScreen();
      break;
    case 3:
      renderGlyphScreen();
      break;
    default:
      renderMainHUD();
      break;
  }
}

// ---------------------- JSON Telemetry Builder ----------------------
String buildTelemetryJson() {
  String json = "{";
  json += "\"connected\":true,";
  json += "\"sosTriggered\":" + String(isSosTriggered ? "true" : "false") + ",";
  json += "\"tamperDetected\":" + String(isTamperDetected ? "true" : "false") + ",";
  json += "\"guardArmed\":" + String(isVehicleGuardArmed ? "true" : "false") + ",";
  json += "\"crashDetected\":" + String(isCrashDetected ? "true" : "false") + ",";
  json += "\"speed\":" + String(currentSpeed) + ",";
  json += "\"score\":" + String(currentScore) + ",";
  json += "\"vehicleMode\":\"" + vehicleMode + "\",";
  json += "\"glyph\":\"" + currentGlyph + "\",";

  // GPS sub-object
  json += "\"gps\":{";
  json += "\"fix\":" + String(currentGpsFix ? "true" : "false") + ",";
  json += "\"lat\":" + String(currentGpsLat, 6) + ",";
  json += "\"lng\":" + String(currentGpsLng, 6) + ",";
  json += "\"speed\":" + String(currentGpsSpeedKmH, 1) + ",";
  json += "\"sats\":" + String(currentGpsSats) + ",";
  json += "\"alt\":" + String(currentGpsAltM, 1);
  json += "},";

  // IMU sub-object
  json += "\"imu\":{";
  json += "\"leanAngle\":" + String(currentLeanAngle, 1) + ",";
  json += "\"pitch\":" + String(currentPitch, 1) + ",";
  json += "\"accelG\":" + String(currentAccelG, 2) + ",";
  json += "\"available\":" + String(mpuAvailable ? "true" : "false");
  json += "}";

  json += "}";
  return json;
}

// ---------------------- Web Server Handlers ----------------------
void handleStatus() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", buildTelemetryJson());
}

void handleTelemetry() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", buildTelemetryJson());
}

void handleDisplay() {
  if (server.hasArg("plain")) {
    String body = server.arg("plain");

    int line1Idx = body.indexOf("\"line1\":");
    if (line1Idx != -1) {
      int start = body.indexOf("\"", line1Idx + 8) + 1;
      int end = body.indexOf("\"", start);
      if (start > 0 && end > start) currentLine1 = body.substring(start, end);
    }
    int speedIdx = body.indexOf("\"speed\":");
    if (speedIdx != -1) {
      int spd = body.substring(speedIdx + 8).toInt();
      if (!currentGpsFix || spd > 0) currentSpeed = spd;
      if (currentSpeed > 0) lastMotionTime = millis();
    }
    int scoreIdx = body.indexOf("\"score\":");
    if (scoreIdx != -1) {
      currentScore = body.substring(scoreIdx + 8).toInt();
    }
    int modeIdx = body.indexOf("\"vehicleMode\":");
    if (modeIdx != -1) {
      int start = body.indexOf("\"", modeIdx + 14) + 1;
      int end = body.indexOf("\"", start);
      if (start > 0 && end > start) vehicleMode = body.substring(start, end);
    }
    int glyphIdx = body.indexOf("\"glyph\":");
    if (glyphIdx != -1) {
      int start = body.indexOf("\"", glyphIdx + 8) + 1;
      int end = body.indexOf("\"", start);
      if (start > 0 && end > start) currentGlyph = body.substring(start, end);
    }
    int guardIdx = body.indexOf("\"guardArmed\":");
    if (guardIdx != -1) {
      isVehicleGuardArmed = body.substring(guardIdx + 13).startsWith("true");
    }
    updateOLED();
  }
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"status\":\"success\"}");
}

void handleSOS() {
  String json = "{\"sos\":" + String(isSosTriggered ? "true" : "false") + "}";
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", json);
}

void handleResetSOS() {
  clearSos();
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"status\":\"reset_ok\"}");
}

void handleNotFound() {
  server.send(404, "text/plain", "DriveSphere Hub - Not Found");
}

// ---------------------- Push Button Handler ----------------------
void checkButton() {
  int reading = digitalRead(BUTTON_PIN);

  if (reading != lastButtonState) {
    lastDebounceTime = millis();
  }

  if ((millis() - lastDebounceTime) > debounceDelay) {
    if (reading == LOW && !isButtonPressed) {
      isButtonPressed = true;
      buttonPressStartTime = millis();
      longPressTriggered = false;
    } 
    else if (reading == LOW && isButtonPressed) {
      if (!longPressTriggered && (millis() - buttonPressStartTime >= LONG_PRESS_MS)) {
        longPressTriggered = true;
        if (isSosTriggered) {
          clearSos();
        } else {
          isSosTriggered = true;
          isCrashDetected = false;
          peakCrashG = 0.0f;
          peakTiltDeg = 0.0f;
          updateOLED();
        }
        SerialBT.println("{\"sosTriggered\":" + String(isSosTriggered ? "true" : "false") + "}");
      }
    } 
    else if (reading == HIGH && isButtonPressed) {
      isButtonPressed = false;
      if (isBtPairingActive) {
        isBtPairingActive = false;
        updateOLED();
      } else if (!longPressTriggered) {
        activeDisplayMode = (activeDisplayMode + 1) % 4;
        updateOLED();
      }
    }
  }

  lastButtonState = reading;
}

// ---------------------- Bluetooth Callbacks & Handler ----------------------
void BTConfirmRequestCallback(uint32_t numVal) {
  btPairingCode = numVal;
  isBtPairingActive = true;
  btPairingStartTime = millis();
  btPairingNeedsUpdate = true;
  Serial.printf("[Bluetooth] Phone Pairing Request! Passkey PIN: %06u\n", numVal);
  SerialBT.confirmReply(true); // Auto-confirm handshake so phone can complete pairing
}

void BTAuthCompleteCallback(boolean success) {
  if (success) {
    Serial.println(F("[Bluetooth] Authentication Success! Device Paired."));
    btPairingSuccess = true;
  } else {
    Serial.println(F("[Bluetooth] Authentication Failed or Cancelled."));
    btPairingSuccess = false;
  }
  btAuthCompleteTime = millis();
  isBtPairingActive = false;
  btPairingNeedsUpdate = true;
}

void handleBluetooth() {
  // Auto-timeout pairing prompt after 25s
  if (isBtPairingActive && (millis() - btPairingStartTime > 25000)) {
    isBtPairingActive = false;
    updateOLED();
  }
  if (SerialBT.available()) {
    String line = SerialBT.readStringUntil('\n');
    line.trim();
    if (line.length() > 0) {
      if (line.startsWith("SPEED:")) {
        int spd = line.substring(6).toInt();
        if (!currentGpsFix || spd > 0) currentSpeed = spd;
        if (currentSpeed > 0) lastMotionTime = millis();
        updateOLED();
      } else if (line.startsWith("SCORE:")) {
        currentScore = line.substring(6).toInt();
        updateOLED();
      } else if (line.startsWith("MODE:")) {
        vehicleMode = line.substring(5);
        updateOLED();
      } else if (line.startsWith("GLYPH:")) {
        currentGlyph = line.substring(6);
        updateOLED();
      } else if (line.startsWith("GUARD:")) {
        isVehicleGuardArmed = (line.substring(6) == "ARMED");
        if (!isVehicleGuardArmed) isTamperDetected = false;
        updateOLED();
      } else if (line == "RESET_SOS") {
        clearSos();
        SerialBT.println("{\"status\":\"reset_ok\"}");
      } else if (line == "STATUS" || line == "TELEMETRY") {
        SerialBT.println(buildTelemetryJson());
      }
    }
  }

  // Periodic BT telemetry broadcast every 1.5s
  if (millis() - lastBtBroadcastTime > 1500) {
    lastBtBroadcastTime = millis();
    SerialBT.println(buildTelemetryJson());
  }
}

// ---------------------- DriveSphere Brand Logo & Splash Screen ----------------------
void drawDriveSphereLogo(int cx, int cy) {
  // Futuristic Automotive Cyber-Radar / Steering Sphere Logo
  display.drawCircle(cx, cy, 13, SSD1306_WHITE);
  display.drawCircle(cx, cy, 9, SSD1306_WHITE);
  display.fillCircle(cx, cy, 3, SSD1306_WHITE); // Glowing optical core

  // Aerodynamic Cockpit Wings (Left & Right)
  display.drawFastHLine(cx - 24, cy, 8, SSD1306_WHITE);
  display.drawFastHLine(cx + 17, cy, 8, SSD1306_WHITE);
  display.drawPixel(cx - 20, cy - 2, SSD1306_WHITE);
  display.drawPixel(cx - 20, cy + 2, SSD1306_WHITE);
  display.drawPixel(cx + 20, cy - 2, SSD1306_WHITE);
  display.drawPixel(cx + 20, cy + 2, SSD1306_WHITE);

  // Top & Bottom Antenna Ticks
  display.drawFastVLine(cx, cy - 17, 3, SSD1306_WHITE);
  display.drawFastVLine(cx, cy + 14, 3, SSD1306_WHITE);
}

void showLoadingSplash(int progress, const char* label) {
  display.clearDisplay();
  drawDriveSphereLogo(64, 18);

  // Centered Brand Title (Y: 35)
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(31, 35);
  display.print("DRIVESPHERE");

  // Modern Capsule Progress Bar (X: 20 to 108, W: 88, Y: 46)
  display.drawRoundRect(20, 46, 88, 5, 2, SSD1306_WHITE);
  int fillW = map(constrain(progress, 0, 100), 0, 100, 0, 84);
  if (fillW > 0) {
    display.fillRect(22, 47, fillW, 3, SSD1306_WHITE);
  }

  // Centered Status Subtitle (Y: 54)
  int len = strlen(label);
  int x = (128 - (len * 6)) / 2;
  display.setCursor(x, 54);
  display.print(label);

  display.display();
}

// ---------------------- Setup & Main Loop ----------------------
void setup() {
  Serial.begin(115200);
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  // Initialize Hardware UART2 for NEO-M8N GPS
  SerialGPS.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
  Serial.println(F("[DriveSphere] NEO-M8N GPS UART2 started on RX=16, TX=17 @ 9600 baud"));

  // Initialize I2C Bus on GPIO 15 (SDA) and GPIO 14 (SCL)
  Serial.println(F("[DriveSphere] Initializing OLED I2C Bus 0 (SDA=15, SCL=14)..."));
  Wire.setPins(I2C_SDA_PIN, I2C_SCL_PIN);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  Wire.setClock(100000); // 100 kHz for reliable jumper wire signals

  // Scan and detect OLED address (0x3C or 0x3D)
  byte oledAddr = 0;
  for (byte addr = 0x3C; addr <= 0x3D; addr++) {
    Wire.beginTransmission(addr);
    if (Wire.endTransmission() == 0) {
      oledAddr = addr;
      break;
    }
  }

  // If not found, auto-test reversed pin configuration (SDA=14, SCL=15)
  if (oledAddr == 0) {
    Wire.end();
    Wire.setPins(I2C_SCL_PIN, I2C_SDA_PIN);
    Wire.begin(I2C_SCL_PIN, I2C_SDA_PIN);
    Wire.setClock(100000);
    for (byte addr = 0x3C; addr <= 0x3D; addr++) {
      Wire.beginTransmission(addr);
      if (Wire.endTransmission() == 0) {
        oledAddr = addr;
        Serial.println(F("[DriveSphere] Auto-detected OLED with reversed wiring (SDA=14, SCL=15)!"));
        break;
      }
    }
    if (oledAddr == 0) {
      // Revert to configured pins (15, 14)
      Wire.end();
      Wire.setPins(I2C_SDA_PIN, I2C_SCL_PIN);
      Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
      Wire.setClock(100000);
      Serial.println(F("[DriveSphere] WARNING: No OLED found on I2C bus! Verify VCC (3.3V/5V), GND, SDA=15, SCL=14"));
    }
  }

  // Initialize OLED Display (reset=false, periphBegin=false to preserve Wire pins)
  bool oledFound = false;
  byte targetAddr = (oledAddr != 0) ? oledAddr : 0x3C;
  if (display.begin(SSD1306_SWITCHCAPVCC, targetAddr, false, false)) {
    oledFound = true;
    Serial.print(F("[DriveSphere] OLED SSD1306 Initialized at 0x"));
    Serial.println(targetAddr, HEX);
    display.ssd1306_command(SSD1306_SETCONTRAST);
    display.ssd1306_command(0xFF); // Maximum display brightness
  } else {
    // Retry with 0x3D if 0x3C failed
    if (display.begin(SSD1306_SWITCHCAPVCC, 0x3D, false, false)) {
      oledFound = true;
      Serial.println(F("[DriveSphere] OLED SSD1306 Initialized at 0x3D"));
      display.ssd1306_command(SSD1306_SETCONTRAST);
      display.ssd1306_command(0xFF);
    }
  }

  if (oledFound) {
    showLoadingSplash(25, "BOOTING SYSTEM");
  }

  // Initialize MPU-6050 IMU on dedicated I2C bus
  initMPU6050();
  if (oledFound) showLoadingSplash(50, "CALIBRATING IMU");

  // Start Wi-Fi Access Point
  WiFi.softAP(apSSID, apPassword);
  IPAddress IP = WiFi.softAPIP();
  Serial.print(F("[DriveSphere] Wi-Fi SoftAP Started: "));
  Serial.println(IP);
  if (oledFound) showLoadingSplash(75, "STARTING COMMS");

  // Start Bluetooth Classic SPP with Secure Simple Pairing & PIN Display
  SerialBT.enableSSP();
  SerialBT.onConfirmRequest(BTConfirmRequestCallback);
  SerialBT.onAuthComplete(BTAuthCompleteCallback);
  SerialBT.begin("DriveSphere-Hub");
  SerialBT.setPin(btFixedPin, 4);
  Serial.println(F("[DriveSphere] Bluetooth SPP Active as 'DriveSphere-Hub' (PIN: 1234 / SSP Enabled)"));
  if (oledFound) {
    showLoadingSplash(100, "SYSTEM READY");
    delay(400);
  }

  // Register Web Server Endpoints
  server.on("/status", HTTP_GET, handleStatus);
  server.on("/telemetry", HTTP_GET, handleTelemetry);
  server.on("/display", HTTP_POST, handleDisplay);
  server.on("/sos", HTTP_GET, handleSOS);
  server.on("/reset_sos", HTTP_POST, handleResetSOS);
  server.onNotFound(handleNotFound);

  server.begin();
  Serial.println(F("[DriveSphere] HTTP Server listening on port 80"));

  updateOLED();
}

void loop() {
  server.handleClient();
  checkButton();
  readGPS();
  readMPU6050();
  handleBluetooth();

  if (btPairingNeedsUpdate) {
    btPairingNeedsUpdate = false;
    updateOLED();
  }

  // Refresh OLED periodically (every 150ms)
  if (millis() - lastDisplayRefreshTime > 150) {
    lastDisplayRefreshTime = millis();
    updateOLED();
  }
}
