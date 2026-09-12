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
#define ENABLE_BLUETOOTH false

#if ENABLE_BLUETOOTH
#include "BluetoothSerial.h"
#include "esp_gap_bt_api.h"
#endif
#include <TinyGPSPlus.h>
#include "esp_camera.h"
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ---------------------- AI-Thinker Camera Hardware Pins ----------------------
#define CAM_PWDN_GPIO_NUM     32
#define CAM_RESET_GPIO_NUM    -1
#define CAM_XCLK_GPIO_NUM      0
#define CAM_SIOD_GPIO_NUM     26
#define CAM_SIOC_GPIO_NUM     27
#define CAM_Y9_GPIO_NUM       35
#define CAM_Y8_GPIO_NUM       34
#define CAM_Y7_GPIO_NUM       39
#define CAM_Y6_GPIO_NUM       36
#define CAM_Y5_GPIO_NUM       21
#define CAM_Y4_GPIO_NUM       19
#define CAM_Y3_GPIO_NUM       18
#define CAM_Y2_GPIO_NUM        5
#define CAM_VSYNC_GPIO_NUM    25
#define CAM_HREF_GPIO_NUM     23
#define CAM_PCLK_GPIO_NUM     22

#define FLASH_LED_PIN          4
#define FLASH_PWM_FREQ      5000
#define FLASH_PWM_RES          8

bool cameraFound = false;
uint8_t camSensorPid = 0;
int flashBrightness = 0;

// ---------------------- Hardware Configuration ----------------------
#define SCREEN_WIDTH    128
#define SCREEN_HEIGHT   64
#define OLED_RESET      -1
#define SCREEN_ADDRESS  0x3C // Standard I2C address for SSD1306

// OLED Display Pins (Hardware Wire Bus 0)
#define OLED_SDA_PIN    15   // SSD1306 OLED SDA on GPIO 15
#define OLED_SCL_PIN    14   // SSD1306 OLED SCL on GPIO 14
#define I2C_SDA_PIN     15
#define I2C_SCL_PIN     14

// MPU-6050 Motion Sensor Pins (Hardware Wire1 Bus 1)
#define MPU_SDA_PIN     13   // MPU6050 SDA on GPIO 13
#define MPU_SCL_PIN      2   // MPU6050 SCL on GPIO 2

// Push Button Pin (Dedicated GPIO)
#define BUTTON_PIN      12   // Push button on GPIO 12 (INPUT_PULLUP)
#define LONG_PRESS_MS   3000 // 3 seconds for SOS trigger

// NEO-M8N GPS Module Pins (Hardware UART2)
#define GPS_RX_PIN      16   // NEO-M8N TX -> ESP32 GPIO 16 (U2RXD)
#define GPS_TX_PIN      -1   // NEO-M8N RX -> Unused
#define GPS_BAUD        9600

#define MPU6050_ADDR         0x68
#define MPU6050_CONFIG       0x1A // DLPF (Digital Low Pass Filter) register
#define MPU6050_ACCEL_CONFIG 0x1C // Accelerometer full-scale range register
#define MPU6050_PWR_MGMT_1   0x6B
#define MPU6050_ACCEL_XOUT_H 0x3B

// Calibrated Thresholds & Filters for Vehicle & Motorcycle Safety
#define MPU_ACCEL_SCALE         4096.0f  // ±8g sensitivity: 4096 LSB/g (covers road impacts without saturation)
#define CRASH_THRESHOLD_G       6.5f     // Genuine vehicular crash impact threshold (>6.5G)
#define CRASH_SAMPLES_REQ       3        // Multi-sample debounce (3 consecutive 50ms reads > threshold)
#define FALL_ANGLE_DEG          65.0f    // Motorcycle fall tilt threshold (>65° lean angle; normal riding max ~45°-50°)
#define FALL_SUSTAIN_MS         2500     // Must remain fallen for 2.5 seconds (prevents false trigger on quick bumps/turns)
#define TAMPER_THRESHOLD_G      0.45f    // Guard armed motion threshold

// ---------------------- Peripherals Initialization ----------------------
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
WebServer server(80);
#if ENABLE_BLUETOOTH
BluetoothSerial SerialBT;
#endif
HardwareSerial SerialGPS(2);
TinyGPSPlus gps;

// ---------------------- Network & Identity ----------------------
const char* apSSID = "DriveSphere-Hub";
const char* apPassword = "drivesphere123";

// ---------------------- State & Telemetry ----------------------
String currentLine1 = "DRIVESPHERE";
String currentLine2 = "SYSTEM READY";
String currentGlyph = "IDLE_FACE";
int currentSpeed = 0;
int currentScore = 92;
String vehicleMode = "BIKE"; // "BIKE" or "CAR"

bool isSosTriggered = false;
bool isTamperDetected = false;
bool isVehicleGuardArmed = false;
int activeDisplayMode = 0; // 0: Aero HUD, 1: Map Nav, 2: Guard Visor, 3: Telemetry, 4: Full Glyph

// OLED Hardware Configuration
int chosenSda = 15;
int chosenScl = 14;
byte activeOledAddr = 0x3C;
bool oledFound = false;

// Turn-by-Turn GPS Map Navigation State (Matches 128x64 OLED Layout)
String navManeuver = "STRAIGHT"; // "STRAIGHT", "TURN_LEFT", "TURN_RIGHT", "UTURN", "DESTINATION"
String navDistance = "0 m";
String navEta = "5 min - 2.2km - 11:52";
String navStreet = "Trung Lap 7";
bool isNavActive = false;

// MPU-6050 Telemetry
uint8_t activeMpuAddr = 0x68;
bool mpuAvailable = false;
float currentLeanAngle = 0.0; // Roll in degrees
float currentPitch = 0.0;     // Pitch in degrees
float currentAccelG = 1.0;    // Dynamic acceleration magnitude
bool isCrashDetected = false;
float peakCrashG = 0.0;       // Latched peak impact shock G
float peakTiltDeg = 0.0;      // Latched peak fall tilt angle
unsigned long lastMotionTime = 0; // Timestamp of last detected motion
unsigned long lastMpuReadTime = 0;
unsigned long fallStartTime = 0;  // Debounce timer for sustained fall angle
int crashHitCount = 0;            // Consecutive high-G samples counter

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
char btDynamicPin[8] = "842195"; // 6-digit dynamic rolling PIN
volatile bool isBtPairingActive = false;
volatile bool btPairingPendingConfirm = false;
volatile uint32_t btPairingCode = 0;
unsigned long btPairingStartTime = 0;
volatile bool btPairingSuccess = false;
unsigned long btAuthCompleteTime = 0;
volatile bool btPairingNeedsUpdate = false;

// ---------------------- Hardware Flashlight & Camera ----------------------
void setFlashBrightness(int duty) {
  duty = constrain(duty, 0, 255);
  flashBrightness = duty;
  ledcWrite(FLASH_LED_PIN, duty);
}

bool initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0       = CAM_Y2_GPIO_NUM;
  config.pin_d1       = CAM_Y3_GPIO_NUM;
  config.pin_d2       = CAM_Y4_GPIO_NUM;
  config.pin_d3       = CAM_Y5_GPIO_NUM;
  config.pin_d4       = CAM_Y6_GPIO_NUM;
  config.pin_d5       = CAM_Y7_GPIO_NUM;
  config.pin_d6       = CAM_Y8_GPIO_NUM;
  config.pin_d7       = CAM_Y9_GPIO_NUM;
  config.pin_xclk     = CAM_XCLK_GPIO_NUM;
  config.pin_pclk     = CAM_PCLK_GPIO_NUM;
  config.pin_vsync    = CAM_VSYNC_GPIO_NUM;
  config.pin_href     = CAM_HREF_GPIO_NUM;
  config.pin_sccb_sda = CAM_SIOD_GPIO_NUM;
  config.pin_sccb_scl = CAM_SIOC_GPIO_NUM;
  config.pin_pwdn     = CAM_PWDN_GPIO_NUM;
  config.pin_reset    = CAM_RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  if (psramFound()) {
    Serial.println(F("[DriveSphere Cam] PSRAM detected. Configuring VGA high-speed buffers."));
    config.frame_size = FRAMESIZE_VGA;
    config.jpeg_quality = 12;
    config.fb_count = 2;
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.grab_mode = CAMERA_GRAB_LATEST;
  } else {
    Serial.println(F("[DriveSphere Cam] DRAM mode (No PSRAM). Using QVGA resolution."));
    config.frame_size = FRAMESIZE_QVGA;
    config.jpeg_quality = 14;
    config.fb_count = 1;
    config.fb_location = CAMERA_FB_IN_DRAM;
    config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[DriveSphere Cam] Camera init failed: 0x%x\n", err);
    cameraFound = false;
    return false;
  }

  sensor_t * s = esp_camera_sensor_get();
  if (s != NULL) {
    camSensorPid = s->id.PID;
    s->set_brightness(s, 1);
    s->set_contrast(s, 0);
    s->set_saturation(s, 0);
    s->set_whitebal(s, 1);
    s->set_awb_gain(s, 1);
    s->set_exposure_ctrl(s, 1);
    s->set_vflip(s, 0);
    s->set_hmirror(s, 0);
  }

  cameraFound = true;
  Serial.printf("[DriveSphere Cam] OV2640 Initialized! Sensor PID: 0x%02X\n", camSensorPid);
  return true;
}

// ---------------------- MPU-6050 Routines (Software I2C on SDA=13, SCL=2) ----------------------
// Avoids hardware I2C port 1 conflict with the OV2640 camera driver
static void bb_i2c_delay() {
  delayMicroseconds(4);
}

static void bb_i2c_start() {
  pinMode(MPU_SDA_PIN, INPUT_PULLUP);
  pinMode(MPU_SCL_PIN, INPUT_PULLUP);
  bb_i2c_delay();
  pinMode(MPU_SDA_PIN, OUTPUT);
  digitalWrite(MPU_SDA_PIN, LOW);
  bb_i2c_delay();
  pinMode(MPU_SCL_PIN, OUTPUT);
  digitalWrite(MPU_SCL_PIN, LOW);
  bb_i2c_delay();
}

static void bb_i2c_stop() {
  pinMode(MPU_SDA_PIN, OUTPUT);
  digitalWrite(MPU_SDA_PIN, LOW);
  bb_i2c_delay();
  pinMode(MPU_SCL_PIN, INPUT_PULLUP);
  bb_i2c_delay();
  pinMode(MPU_SDA_PIN, INPUT_PULLUP);
  bb_i2c_delay();
}

static bool bb_i2c_write(uint8_t data) {
  for (int i = 0; i < 8; i++) {
    if (data & 0x80) {
      pinMode(MPU_SDA_PIN, INPUT_PULLUP);
    } else {
      pinMode(MPU_SDA_PIN, OUTPUT);
      digitalWrite(MPU_SDA_PIN, LOW);
    }
    data <<= 1;
    bb_i2c_delay();
    pinMode(MPU_SCL_PIN, INPUT_PULLUP);
    bb_i2c_delay();
    pinMode(MPU_SCL_PIN, OUTPUT);
    digitalWrite(MPU_SCL_PIN, LOW);
    bb_i2c_delay();
  }
  // Read ACK
  pinMode(MPU_SDA_PIN, INPUT_PULLUP);
  bb_i2c_delay();
  pinMode(MPU_SCL_PIN, INPUT_PULLUP);
  bb_i2c_delay();
  bool ack = (digitalRead(MPU_SDA_PIN) == LOW);
  pinMode(MPU_SCL_PIN, OUTPUT);
  digitalWrite(MPU_SCL_PIN, LOW);
  bb_i2c_delay();
  return ack;
}

static uint8_t bb_i2c_read(bool ack) {
  uint8_t data = 0;
  pinMode(MPU_SDA_PIN, INPUT_PULLUP);
  for (int i = 0; i < 8; i++) {
    data <<= 1;
    pinMode(MPU_SCL_PIN, INPUT_PULLUP);
    bb_i2c_delay();
    if (digitalRead(MPU_SDA_PIN) == HIGH) data |= 1;
    pinMode(MPU_SCL_PIN, OUTPUT);
    digitalWrite(MPU_SCL_PIN, LOW);
    bb_i2c_delay();
  }
  if (ack) {
    pinMode(MPU_SDA_PIN, OUTPUT);
    digitalWrite(MPU_SDA_PIN, LOW);
  } else {
    pinMode(MPU_SDA_PIN, INPUT_PULLUP);
  }
  bb_i2c_delay();
  pinMode(MPU_SCL_PIN, INPUT_PULLUP);
  bb_i2c_delay();
  pinMode(MPU_SCL_PIN, OUTPUT);
  digitalWrite(MPU_SCL_PIN, LOW);
  pinMode(MPU_SDA_PIN, INPUT_PULLUP);
  bb_i2c_delay();
  return data;
}

static bool bb_mpu_write_reg(uint8_t addr, uint8_t reg, uint8_t val) {
  bb_i2c_start();
  if (!bb_i2c_write((addr << 1) | 0)) { bb_i2c_stop(); return false; }
  if (!bb_i2c_write(reg)) { bb_i2c_stop(); return false; }
  if (!bb_i2c_write(val)) { bb_i2c_stop(); return false; }
  bb_i2c_stop();
  return true;
}

static bool bb_mpu_read_bytes(uint8_t addr, uint8_t reg, uint8_t* buf, int len) {
  bb_i2c_start();
  if (!bb_i2c_write((addr << 1) | 0)) { bb_i2c_stop(); return false; }
  if (!bb_i2c_write(reg)) { bb_i2c_stop(); return false; }
  bb_i2c_start(); // Repeated start
  if (!bb_i2c_write((addr << 1) | 1)) { bb_i2c_stop(); return false; }
  for (int i = 0; i < len; i++) {
    buf[i] = bb_i2c_read(i < len - 1);
  }
  bb_i2c_stop();
  return true;
}

void initMPU6050() {
  pinMode(MPU_SDA_PIN, INPUT_PULLUP);
  pinMode(MPU_SCL_PIN, INPUT_PULLUP);

  auto configSensor = [](uint8_t addr) -> bool {
    // 1. Wake up MPU-6050 (0x6B = 0x01)
    if (!bb_mpu_write_reg(addr, MPU6050_PWR_MGMT_1, 0x01)) return false;
    // 2. Configure DLPF to 44Hz (0x1A = 0x03)
    if (!bb_mpu_write_reg(addr, MPU6050_CONFIG, 0x03)) return false;
    // 3. Configure Accelerometer Full-Scale Range to ±8g (0x1C = 0x10)
    if (!bb_mpu_write_reg(addr, MPU6050_ACCEL_CONFIG, 0x10)) return false;
    return true;
  };

  if (configSensor(0x68)) {
    activeMpuAddr = 0x68;
    mpuAvailable = true;
    Serial.printf("[DriveSphere] MPU6050 IMU initialized at 0x68 on SDA=%d, SCL=%d!\n", MPU_SDA_PIN, MPU_SCL_PIN);
  } else if (configSensor(0x69)) {
    activeMpuAddr = 0x69;
    mpuAvailable = true;
    Serial.printf("[DriveSphere] MPU6050 IMU initialized at alternate 0x69 on SDA=%d, SCL=%d!\n", MPU_SDA_PIN, MPU_SCL_PIN);
  } else {
    Serial.printf("[DriveSphere] Scanning I2C on SDA=%d, SCL=%d: ", MPU_SDA_PIN, MPU_SCL_PIN);
    int detected = 0;
    for (byte a = 1; a < 127; a++) {
      bb_i2c_start();
      bool ack = bb_i2c_write((a << 1) | 0);
      bb_i2c_stop();
      if (ack) {
        Serial.printf("0x%02X ", a);
        detected++;
        if (a == 0x68 || a == 0x69) {
          activeMpuAddr = a;
          mpuAvailable = configSensor(a);
        }
      }
    }
    if (detected == 0) Serial.print("NONE (Check SDA=13, SCL=2, VCC, GND)");
    Serial.println();
  }
}

void readMPU6050() {
  if (!mpuAvailable) return;
  if (millis() - lastMpuReadTime < 50) return; // 20 Hz sample rate
  lastMpuReadTime = millis();

  uint8_t raw[14];
  if (!bb_mpu_read_bytes(activeMpuAddr, MPU6050_ACCEL_XOUT_H, raw, 14)) return;

  int16_t ax = (raw[0] << 8) | raw[1];
  int16_t ay = (raw[2] << 8) | raw[3];
  int16_t az = (raw[4] << 8) | raw[5];
  int16_t temp = (raw[6] << 8) | raw[7];
  int16_t gx = (raw[8] << 8) | raw[9];
  int16_t gy = (raw[10] << 8) | raw[11];
  int16_t gz = (raw[12] << 8) | raw[13];

    // Scale raw values using ±8g factor (4096 LSB/g)
    float ax_g = (float)ax / MPU_ACCEL_SCALE;
    float ay_g = (float)ay / MPU_ACCEL_SCALE;
    float az_g = (float)az / MPU_ACCEL_SCALE;
    currentAccelG = sqrt(ax_g * ax_g + ay_g * ay_g + az_g * az_g);

    // Roll (motorcycle lean angle in degrees) with exponential moving average smoothing
    float rawLeanAngle = atan2(ay_g, az_g) * 180.0f / 3.14159265f;
    currentLeanAngle = 0.80f * currentLeanAngle + 0.20f * rawLeanAngle;

    // Pitch (elevation angle in degrees)
    currentPitch = atan2(-ax_g, sqrt(ay_g * ay_g + az_g * az_g)) * 180.0f / 3.14159265f;

    // Automatic Fall Detection: Motorcycle tilted > 65 deg SUSTAINED for 2.5 seconds
    // CRITICAL FIX: Only armed during active riding motion (speed >= 5 km/h or GPS moving).
    // Prevents false alarms when picking up device, handling it in hand, on desk, or on kickstand.
    bool isRidingMotion = (currentSpeed >= 5) || (currentGpsSpeedKmH >= 5.0f);

    if (vehicleMode == "BIKE" && isRidingMotion && abs(currentLeanAngle) > FALL_ANGLE_DEG) {
      if (fallStartTime == 0) {
        fallStartTime = millis();
      } else if (millis() - fallStartTime >= FALL_SUSTAIN_MS) {
        if (abs(currentLeanAngle) > peakTiltDeg) peakTiltDeg = abs(currentLeanAngle);
        if (!isSosTriggered) {
          isSosTriggered = true;
          isCrashDetected = true;
#if ENABLE_BLUETOOTH
          SerialBT.println("{\"event\":\"FALL\",\"sosTriggered\":true}");
#endif
          Serial.printf("[DriveSphere Safety] DYNAMIC RIDE FALL DETECTED! Lean=%.1f deg, Speed=%d km/h. S.O.S TRIGGERED.\n", currentLeanAngle, currentSpeed);
        }
      }
    } else {
      fallStartTime = 0; // Bike is upright, recovered, or stationary/parked; cancel timer
    }
    if (isCrashDetected && abs(currentLeanAngle) > peakTiltDeg) {
      peakTiltDeg = abs(currentLeanAngle);
    }

    // High-G Impact Crash Detection (> 6.5 G shock sustained across 3 consecutive samples)
    // Requires either active vehicle motion or extreme impact shock (> 8.0G) to eliminate handling noise
    if (currentAccelG > CRASH_THRESHOLD_G && (isRidingMotion || currentAccelG > 8.0f)) {
      crashHitCount++;
      if (crashHitCount >= CRASH_SAMPLES_REQ) {
        if (currentAccelG > peakCrashG) peakCrashG = currentAccelG;
        if (!isSosTriggered) {
          isSosTriggered = true;
          isCrashDetected = true;
#if ENABLE_BLUETOOTH
          SerialBT.println("{\"event\":\"CRASH\",\"sosTriggered\":true}");
#endif
          Serial.printf("[DriveSphere Safety] CRASH IMPACT DETECTED! G=%.2f. S.O.S TRIGGERED.\n", currentAccelG);
        }
      }
    } else {
      crashHitCount = 0;
    }
    if (isCrashDetected && currentAccelG > peakCrashG) {
      peakCrashG = currentAccelG;
    }

    // Anti-Theft Tamper Motion Detection
    if (isVehicleGuardArmed && abs(currentAccelG - 1.0f) > TAMPER_THRESHOLD_G) {
      isTamperDetected = true;
    }
}

unsigned long totalGpsChars = 0;
unsigned long lastGpsDebugTime = 0;

// ---------------------- NEO-M8N GPS Routines ----------------------
void readGPS() {
#if GPS_RX_PIN >= 0
  while (SerialGPS.available() > 0) {
    char c = SerialGPS.read();
    totalGpsChars++;
    gps.encode(c);
  }
#endif

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
      Serial.printf(" -> NO DATA ON GPIO %d (PSRAM-Safe Pin). Connect NEO-M8N TX -> ESP32 GPIO %d & 5V/GND.\n", GPS_RX_PIN, GPS_RX_PIN);
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
#if ENABLE_BLUETOOTH
  drawBluetoothIcon(46, 0, SerialBT.hasClient());
  display.setCursor(55, 1);
  if (SerialBT.hasClient()) {
    display.print("LINK");
  } else
#else
  drawBluetoothIcon(46, 0, false);
  display.setCursor(55, 1);
#endif
  if (WiFi.softAPgetStationNum() > 0) {
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
  int dotY = (activePage == 4) ? 62 : 57;
  for (int i = 0; i < 5; i++) {
    if (i == activePage) {
      display.fillCircle(103 + (i * 5), dotY, 1, SSD1306_WHITE);
    } else {
      display.drawPixel(103 + (i * 5), dotY, SSD1306_WHITE);
    }
  }
}

void drawCardPagination(int activePage) {
  for (int i = 0; i < 3; i++) {
    int y = 52 + (i * 4);
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
  fallStartTime = 0;
  crashHitCount = 0;
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

// ---------------------- Turn-by-Turn GPS Map Navigation HUD ----------------------
void drawNavArrow(int x, int y, const String& maneuver) {
  String m = maneuver;
  m.toUpperCase();
  m.trim();

  // Crisp, thick-line vector arrows tailored for 128x64 high contrast readability
  if (m == "TURN_LEFT" || m == "LEFT") {
    // 90-degree left turn arrow
    display.fillTriangle(x + 2, y + 15, x + 13, y + 6, x + 13, y + 24, SSD1306_WHITE);
    display.fillRect(x + 13, y + 11, 10, 8, SSD1306_WHITE);
    display.fillRect(x + 17, y + 19, 6, 9, SSD1306_WHITE);
  } else if (m == "SLIGHT_LEFT" || m == "FORK_LEFT") {
    // 45-degree slight left turn arrow
    display.fillTriangle(x + 4, y + 6, x + 15, y + 4, x + 7, y + 17, SSD1306_WHITE);
    display.drawLine(x + 8, y + 13, x + 19, y + 24, SSD1306_WHITE);
    display.drawLine(x + 9, y + 13, x + 20, y + 24, SSD1306_WHITE);
    display.drawLine(x + 10, y + 13, x + 21, y + 24, SSD1306_WHITE);
    display.drawLine(x + 11, y + 13, x + 22, y + 24, SSD1306_WHITE);
  } else if (m == "SHARP_LEFT") {
    // Acute 135-degree hairpin left turn arrow
    display.fillTriangle(x + 4, y + 22, x + 14, y + 14, x + 14, y + 28, SSD1306_WHITE);
    display.drawRoundRect(x + 12, y + 4, 14, 18, 5, SSD1306_WHITE);
    display.drawRoundRect(x + 13, y + 5, 12, 16, 4, SSD1306_WHITE);
  } else if (m == "TURN_RIGHT" || m == "RIGHT") {
    // 90-degree right turn arrow
    display.fillTriangle(x + 28, y + 15, x + 17, y + 6, x + 17, y + 24, SSD1306_WHITE);
    display.fillRect(x + 7, y + 11, 10, 8, SSD1306_WHITE);
    display.fillRect(x + 7, y + 19, 6, 9, SSD1306_WHITE);
  } else if (m == "SLIGHT_RIGHT" || m == "FORK_RIGHT") {
    // 45-degree slight right turn arrow
    display.fillTriangle(x + 26, y + 6, x + 15, y + 4, x + 23, y + 17, SSD1306_WHITE);
    display.drawLine(x + 22, y + 13, x + 11, y + 24, SSD1306_WHITE);
    display.drawLine(x + 21, y + 13, x + 10, y + 24, SSD1306_WHITE);
    display.drawLine(x + 20, y + 13, x + 9, y + 24, SSD1306_WHITE);
    display.drawLine(x + 19, y + 13, x + 8, y + 24, SSD1306_WHITE);
  } else if (m == "SHARP_RIGHT") {
    // Acute 135-degree hairpin right turn arrow
    display.fillTriangle(x + 26, y + 22, x + 16, y + 14, x + 16, y + 28, SSD1306_WHITE);
    display.drawRoundRect(x + 4, y + 4, 14, 18, 5, SSD1306_WHITE);
    display.drawRoundRect(x + 5, y + 5, 12, 16, 4, SSD1306_WHITE);
  } else if (m == "UTURN") {
    // Sweeping U-turn arc
    display.drawRoundRect(x + 6, y + 4, 18, 16, 8, SSD1306_WHITE);
    display.drawRoundRect(x + 7, y + 5, 16, 14, 7, SSD1306_WHITE);
    display.fillTriangle(x + 6, y + 26, x + 1, y + 17, x + 11, y + 17, SSD1306_WHITE);
  } else if (m == "ROUNDABOUT" || m == "ROTARY") {
    // Rotary circle with arrow
    display.drawCircle(x + 15, y + 15, 10, SSD1306_WHITE);
    display.drawCircle(x + 15, y + 15, 7, SSD1306_WHITE);
    display.fillTriangle(x + 25, y + 10, x + 18, y + 6, x + 20, y + 16, SSD1306_WHITE);
  } else if (m == "DESTINATION" || m == "ARRIVE") {
    // Checkered destination target pin
    display.fillCircle(x + 15, y + 9, 8, SSD1306_WHITE);
    display.fillCircle(x + 15, y + 9, 3, SSD1306_BLACK);
    display.fillTriangle(x + 8, y + 12, x + 22, y + 12, x + 15, y + 27, SSD1306_WHITE);
  } else {
    // STRAIGHT / FORWARD: Highway surge chevron arrow
    display.fillTriangle(x + 15, y + 2, x + 4, y + 13, x + 26, y + 13, SSD1306_WHITE);
    display.fillRect(x + 11, y + 15, 8, 4, SSD1306_WHITE);
    display.fillRect(x + 11, y + 21, 8, 4, SSD1306_WHITE);
    display.fillRect(x + 11, y + 27, 8, 3, SSD1306_WHITE);
  }
}

// SCREEN 1: Turn-by-Turn GPS Map Navigation HUD (128x64 OLED)
void renderNavigationScreen() {
  display.clearDisplay();

  // 1. Top Integrated HUD Header (Y: 0 to 9)
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.fillRoundRect(2, 0, 32, 9, 2, SSD1306_WHITE);
  display.setTextColor(SSD1306_BLACK);
  display.setCursor(4, 1);
  display.print("NAV");
  display.setTextColor(SSD1306_WHITE);

  // Speedometer Mini-Badge at Top Right (Rider always knows speed!)
  display.setCursor(68, 1);
  display.print("SPD: ");
  display.print(currentSpeed);
  display.print(" KPH");
  display.drawFastHLine(0, 10, 128, SSD1306_WHITE);

  // 2. Left Section: 30x30 Bold Vector Maneuver Arrow (X: 3, Y: 12)
  drawNavArrow(3, 12, navManeuver);

  // Vertical Dotted Divider at X: 36 (Y: 11 to 45)
  for (int y = 11; y <= 45; y += 2) {
    display.drawPixel(36, y, SSD1306_WHITE);
  }

  // 3. Right Section: Distance Countdown & Turn Proximity
  display.setTextSize(2);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(41, 13);
  display.print(navDistance);

  // Proximity Countdown Bar (Y: 31, X: 41 to 123, 82px)
  display.drawRoundRect(41, 31, 83, 4, 1, SSD1306_WHITE);
  int distMeters = 300;
  if (navDistance.indexOf("km") != -1) distMeters = 1500;
  else distMeters = navDistance.toInt();
  int fillW = map(constrain(distMeters, 0, 500), 500, 0, 0, 79);
  if (fillW > 0) display.fillRect(43, 32, fillW, 2, SSD1306_WHITE);

  // ETA & Trip Remaining (Y: 37, X: 41)
  display.setTextSize(1);
  display.setCursor(41, 37);
  display.print(navEta);

  // Horizontal Separator Bar at Y: 46
  display.drawFastHLine(0, 46, 128, SSD1306_WHITE);

  // 4. Bottom Section: Next Turn Street Name (Smooth Auto-Scrolling Marquee)
  String street = navStreet;
  int streetLen = street.length();
  if (streetLen <= 14) {
    int startX = (128 - (streetLen * 6)) / 2;
    if (startX < 2) startX = 2;
    display.setCursor(startX, 52);
    display.print(street);
  } else {
    int maxScroll = streetLen - 12;
    int scrollOffset = ((millis() / 280) % (maxScroll + 4));
    if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    String sub = street.substring(scrollOffset, min(streetLen, scrollOffset + 14));
    display.setCursor(4, 52);
    display.print(sub);
  }

  // Discrete Page Indicator (Screen 1)
  drawPagination(1);
  display.display();
}

// SCREEN 0: Aero Digital Cluster HUD (128x64 High-Contrast Cockpit)
void renderMainHUD() {
  display.clearDisplay();
  drawHeader();

  // 1. Dynamic Full-Width Segmented Speed Ribbon (Y: 12 - 17)
  display.drawRoundRect(2, 12, 124, 5, 1, SSD1306_WHITE);
  int ribbonW = map(constrain(currentSpeed, 0, 120), 0, 120, 0, 120);
  if (ribbonW > 0) {
    bool flashRibbon = (currentSpeed > 75 && ((millis() / 150) % 2 == 0));
    if (!flashRibbon) {
      display.fillRect(4, 13, ribbonW, 3, SSD1306_WHITE);
    }
  }
  // Speed tick marks (0, 30, 60, 90, 120 km/h)
  display.drawFastVLine(2,   17, 2, SSD1306_WHITE);
  display.drawFastVLine(33,  17, 2, SSD1306_WHITE);
  display.drawFastVLine(63,  17, 2, SSD1306_WHITE);
  display.drawFastVLine(94,  17, 2, SSD1306_WHITE);
  display.drawFastVLine(125, 17, 2, SSD1306_WHITE);

  // 2. Hero Digital Speedometer Readout (Y: 20 - 47)
  display.setTextColor(SSD1306_WHITE);
  display.setTextSize(4);
  int speedX = (currentSpeed >= 100) ? 6 : (currentSpeed >= 10 ? 18 : 30);
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
  if (currentSpeed > 75) {
    display.print("FAST");
  } else if (isVehicleGuardArmed) {
    display.print("ARM");
  } else {
    display.print(vehicleMode == "BIKE" ? "RIDE" : "DRV");
  }

  // 3. Bottom Telemetry Split Dashboard (Y: 50 - 63)
  if (isTamperDetected) {
    display.fillRoundRect(1, 50, 126, 13, 2, SSD1306_WHITE);
    display.setTextColor(SSD1306_BLACK);
    display.setCursor(10, 53);
    display.print("! TAMPER DETECTED !");
    display.setTextColor(SSD1306_WHITE);
  } else {
    // Left Card: Visual Spirit-Level Lean Angle Meter (60px wide)
    display.drawRoundRect(1, 50, 60, 13, 2, SSD1306_WHITE);
    display.setTextSize(1);
    if (vehicleMode == "BIKE") {
      float absLean = abs(currentLeanAngle);
      display.setCursor(4, 53);
      if (absLean < 2.5f) {
        display.print("0");
        display.print((char)247);
        display.print(" [|] BAL");
      } else if (currentLeanAngle < -2.5f) {
        display.print("<");
        display.print((int)absLean);
        display.print((char)247);
        display.print(" L [.");
      } else {
        display.print("R.");
        display.print("] ");
        display.print((int)absLean);
        display.print((char)247);
        display.print(">");
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
    display.print("G SC:");
    display.print(currentScore);

    // Center pagination indicator
    drawCardPagination(0);
  }

  display.display();
}

// ---------------------- 14x12 Cyber Glyph Bitmaps (PROGMEM) ----------------------
// Each uint16_t holds one 14-bit horizontal row (bit 13 is X=0, bit 0 is X=13)
const uint16_t GLYPH_TURN_LEFT[12] PROGMEM = { 0x0380, 0x07C0, 0x0FE0, 0x1FF0, 0x0380, 0x0380, 0x0380, 0x03FC, 0x001C, 0x001C, 0x001C, 0x0000 };
const uint16_t GLYPH_TURN_RIGHT[12] PROGMEM = { 0x0070, 0x00F8, 0x01FC, 0x03FE, 0x0070, 0x0070, 0x0070, 0x0FF0, 0x0E00, 0x0E00, 0x0E00, 0x0000 };
const uint16_t GLYPH_STRAIGHT[12] PROGMEM = { 0x00E0, 0x01F0, 0x03F8, 0x07FC, 0x00E0, 0x00E0, 0x00E0, 0x00E0, 0x00E0, 0x00E0, 0x00E0, 0x0000 };
const uint16_t GLYPH_IDLE_FACE[12] PROGMEM = { 0x0000, 0x0000, 0x0E1C, 0x1F3E, 0x1F3E, 0x0E1C, 0x0000, 0x0000, 0x0408, 0x03F0, 0x0000, 0x0000 };
const uint16_t GLYPH_MAC[12] PROGMEM = { 0x0DB6, 0x0EDC, 0x0DB6, 0x0DB6, 0x0C06, 0x0DB6, 0x0EDC, 0x0DB6, 0x0000, 0x0000, 0x0000, 0x0000 };
const uint16_t GLYPH_ARCO[12] PROGMEM = { 0x00C0, 0x03F0, 0x0FFC, 0x0C0C, 0x0C0C, 0x0FFC, 0x07F8, 0x03F0, 0x01E0, 0x00C0, 0x0000, 0x0000 };
const uint16_t GLYPH_PC[12] PROGMEM = { 0x0FFC, 0x0804, 0x0804, 0x0804, 0x0FFC, 0x00C0, 0x03F0, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000 };
const uint16_t GLYPH_CUP[12] PROGMEM = { 0x0000, 0x01E0, 0x0210, 0x0408, 0x0408, 0x0210, 0x01E0, 0x0000, 0x0000, 0x0380, 0x01C0, 0x0000 };
const uint16_t GLYPH_ROCKET[12] PROGMEM = { 0x00C0, 0x01E0, 0x03F0, 0x03F0, 0x07F8, 0x06D8, 0x06D8, 0x0F3C, 0x0C0C, 0x0408, 0x0000, 0x0000 };
const uint16_t GLYPH_SUN[12] PROGMEM = { 0x0248, 0x01E0, 0x0BF4, 0x07F8, 0x0BF4, 0x07F8, 0x0BF4, 0x01E0, 0x0248, 0x0000, 0x0000, 0x0000 };
const uint16_t GLYPH_CLOUD[12] PROGMEM = { 0x0000, 0x0000, 0x00F0, 0x03FC, 0x07FE, 0x0FFF, 0x0FFF, 0x07FE, 0x0000, 0x0000, 0x0000, 0x0000 };
const uint16_t GLYPH_BATTERY_FULL[12] PROGMEM = { 0x0000, 0x0000, 0x0FFC, 0x0806, 0x0BF6, 0x0BF6, 0x0BF6, 0x0806, 0x0FFC, 0x0000, 0x0000, 0x0000 };
const uint16_t GLYPH_BATTERY_HALF[12] PROGMEM = { 0x0000, 0x0000, 0x0FFC, 0x0806, 0x0B86, 0x0B86, 0x0B86, 0x0806, 0x0FFC, 0x0000, 0x0000, 0x0000 };
const uint16_t GLYPH_BATTERY_EMPTY[12] PROGMEM = { 0x0000, 0x0000, 0x0FFC, 0x0806, 0x0806, 0x0806, 0x0806, 0x0806, 0x0FFC, 0x0000, 0x0000, 0x0000 };
const uint16_t GLYPH_SHIELD[12] PROGMEM = { 0x0FFC, 0x1FFE, 0x1E7A, 0x1CE6, 0x1CE6, 0x1E7A, 0x0E70, 0x07E0, 0x03C0, 0x0180, 0x0000, 0x0000 };
const uint16_t GLYPH_ALERT[12] PROGMEM = { 0x00C0, 0x01E0, 0x0330, 0x0668, 0x0668, 0x0C4C, 0x0C4C, 0x1806, 0x1806, 0x1806, 0x3FFE, 0x1FFC };

const uint16_t* getGlyphBitmap(const String& name) {
  String upper = name;
  upper.toUpperCase();
  upper.trim();

  if (upper == "TURN_LEFT" || upper == "LEFT") return GLYPH_TURN_LEFT;
  if (upper == "TURN_RIGHT" || upper == "RIGHT") return GLYPH_TURN_RIGHT;
  if (upper == "STRAIGHT" || upper == "NAV" || upper == "FORWARD") return GLYPH_STRAIGHT;
  if (upper == "IDLE_FACE" || upper == "IDLE" || upper == "FACE") return GLYPH_IDLE_FACE;
  if (upper == "MAC" || upper == "APPLE") return GLYPH_MAC;
  if (upper == "ARCO" || upper == "CIRCLE") return GLYPH_ARCO;
  if (upper == "PC" || upper == "COMPUTER") return GLYPH_PC;
  if (upper == "CUP" || upper == "COFFEE") return GLYPH_CUP;
  if (upper == "ROCKET") return GLYPH_ROCKET;
  if (upper == "SUN") return GLYPH_SUN;
  if (upper == "CLOUD") return GLYPH_CLOUD;
  if (upper == "BATTERY_FULL") return GLYPH_BATTERY_FULL;
  if (upper == "BATTERY_HALF") return GLYPH_BATTERY_HALF;
  if (upper == "BATTERY_EMPTY") return GLYPH_BATTERY_EMPTY;
  if (upper == "SHIELD" || upper == "GUARD") return GLYPH_SHIELD;
  if (upper == "ALERT" || upper == "WARNING" || upper == "HAZARD") return GLYPH_ALERT;

  return GLYPH_IDLE_FACE;
}

void drawGlyphMatrix(int originX, int originY, const uint16_t* bitmap, bool animate, bool fullScreen = false) {
  if (!bitmap) return;

  unsigned long now = millis();
  String upper = currentGlyph;
  upper.toUpperCase();
  upper.trim();

  // Animation states for all individual glyphs
  bool isIdleFace = (upper == "IDLE_FACE" || upper == "IDLE" || upper == "FACE");
  bool isRocket   = (upper == "ROCKET");
  bool isLeft     = (upper == "TURN_LEFT" || upper == "LEFT");
  bool isRight    = (upper == "TURN_RIGHT" || upper == "RIGHT");
  bool isStraight = (upper == "STRAIGHT" || upper == "NAV" || upper == "FORWARD");
  bool isShield   = (upper == "SHIELD" || upper == "GUARD");
  bool isAlert    = (upper == "ALERT" || upper == "WARNING");
  bool isSun      = (upper == "SUN");
  bool isCloud    = (upper == "CLOUD" || upper == "RAIN");
  bool isCup      = (upper == "CUP" || upper == "COFFEE");
  bool isBattery  = upper.startsWith("BATTERY");
  bool isArco     = (upper == "ARCO" || upper == "CIRCLE");
  bool isPC       = (upper == "MAC" || upper == "PC" || upper == "COMPUTER");

  // Dynamic animation clock triggers
  bool blink = animate && isIdleFace && ((now % 3400) < 180);
  bool wink  = animate && isIdleFace && ((now % 6800) > 3400 && (now % 6800) < 3600);
  int rocketFlameStep = (now / 70) % 3;
  int leftChevronPhase = (now / 130) % 4;
  int rightChevronPhase = (now / 130) % 4;
  int straightPhase = (now / 110) % 4;
  int shieldScanRow = (now / 90) % 12;
  bool alertPhase = ((now / 200) % 2) == 0;
  bool sunFlare = ((now / 250) % 2) == 0;
  int rainStep = (now / 140) % 4;
  int steamStep = (now / 160) % 4;
  int chargeStep = (now / 300) % 5;
  int arcoSweep = (now / 120) % 8;
  bool pcCursor = ((now / 350) % 2) == 0;

  // Layout metrics (Full-screen fills 128x64 edge-to-edge; compact centered HUD mode fits 70x48)
  int colPitch = fullScreen ? 9 : 5;
  int rowPitch = fullScreen ? 5 : 4;
  int ledW     = fullScreen ? 7 : 4;
  int ledH     = fullScreen ? 4 : 3;

  for (int r = 0; r < 12; r++) {
    uint16_t rowBits = pgm_read_word(&bitmap[r]);
    int y = originY + (r * rowPitch);

    for (int c = 0; c < 14; c++) {
      bool bitOn = (rowBits >> (13 - c)) & 1;
      int x = originX + (c * colPitch);

      if (animate) {
        // 1. IDLE_FACE: Eyes blink naturally, wink periodically
        if (isIdleFace) {
          if ((blink && (r == 3 || r == 4) && ((c >= 1 && c <= 5) || (c >= 8 && c <= 12))) ||
              (wink  && (r == 3 || r == 4) && (c >= 1 && c <= 5))) {
            if (r == 3) display.drawFastHLine(x, y + 1, ledW, SSD1306_WHITE);
            continue;
          }
        }
        // 2. ROCKET: Dynamic multi-stage flickering booster flames
        else if (isRocket && (r >= 8)) {
          if (r == 8 && (c == 6 || c == 7)) bitOn = true;
          if (r == 9 && (c >= 5 && c <= 8)) bitOn = (rocketFlameStep != 0);
          if (r >= 10 && (c == 6 || c == 7)) bitOn = (rocketFlameStep == 2);
        }
        // 3. TURN_LEFT: Running sequential chevron wave marching left
        else if (isLeft && bitOn) {
          int wave = (c + leftChevronPhase) % 3;
          if (wave == 0) bitOn = false; // Flow gap
        }
        // 4. TURN_RIGHT: Running sequential chevron wave marching right
        else if (isRight && bitOn) {
          int wave = (13 - c + rightChevronPhase) % 3;
          if (wave == 0) bitOn = false;
        }
        // 5. STRAIGHT: Surging forward wave
        else if (isStraight && bitOn) {
          int wave = (11 - r + straightPhase) % 4;
          if (wave == 0) bitOn = false;
        }
        // 6. SHIELD: Cyber defense vertical scanning laser beam
        else if (isShield && bitOn) {
          if (r == shieldScanRow) {
            display.fillRect(x, y, ledW, ledH, SSD1306_WHITE);
            continue;
          }
        }
        // 7. ALERT: Hazard strobe pulse
        else if (isAlert) {
          if (alertPhase && (r >= 2 && r <= 7) && (c == 6 || c == 7)) {
            // Invert exclamation mark on strobe
            bitOn = !bitOn;
          }
        }
        // 8. SUN: Solar flare corona ray pulse
        else if (isSun) {
          if (sunFlare && (r == 0 || r == 11 || c == 0 || c == 13)) {
            bitOn = !bitOn;
          }
        }
        // 9. CLOUD: Animated rainfall droplets falling beneath cloud
        else if (isCloud) {
          if (r >= 8) {
            bitOn = ((r + rainStep) % 3 == 0) && (c % 3 == 1);
          }
        }
        // 10. CUP: Undulating hot steam trails rising from cup
        else if (isCup && r <= 3) {
          int steamCol1 = 4 + ((r + steamStep) % 3);
          int steamCol2 = 8 + ((r + steamStep + 1) % 3);
          bitOn = (c == steamCol1 || c == steamCol2);
        }
        // 11. BATTERY: Sequential charging flow filling battery cells
        else if (isBattery && (r >= 3 && r <= 8) && (c >= 2 && c <= 11)) {
          int fillCol = 2 + (chargeStep * 2);
          if (c <= fillCol) bitOn = true;
        }
        // 12. ARCO: Rotating circular radar scanner highlight
        else if (isArco && bitOn) {
          int sector = (r * 2 + c) % 8;
          if (sector == arcoSweep) {
            display.fillRect(x, y, ledW, ledH, SSD1306_WHITE);
            continue;
          }
        }
        // 13. PC/MAC: Blinking terminal command cursor
        else if (isPC) {
          if (r == 4 && c == 7) bitOn = pcCursor;
        }
      }

      if (bitOn) {
        // Crisp Nothing OS rounded micro-LED dot
        display.fillRoundRect(x, y, ledW, ledH, 1, SSD1306_WHITE);
      } else {
        // Subtle background matrix pinhole texture
        if ((r % 2 == 0) && (c % 2 == 0)) {
          display.drawPixel(x + (ledW / 2), y + (ledH / 2), SSD1306_WHITE);
        }
      }
    }
  }

  // Floating background space particles for ROCKET in full screen mode
  if (animate && isRocket && fullScreen) {
    int starY1 = (now / 40) % 64;
    int starY2 = (now / 30 + 32) % 64;
    int starY3 = (now / 50 + 16) % 64;
    display.drawPixel(10, starY1, SSD1306_WHITE);
    display.drawPixel(118, starY2, SSD1306_WHITE);
    display.drawPixel(20, starY3, SSD1306_WHITE);
  }
}

// SCREEN 2: DriveSphere Guard & System Overview (Cyber Visor + Standby Badges)
void renderIdleScreen() {
  display.clearDisplay();
  drawHeader();

  // 1. Center Animated Cyber Visor / Face (X: 4 to 44, Y: 14 to 46)
  display.drawRoundRect(4, 14, 40, 32, 4, SSD1306_WHITE);
  
  // Dynamic eye expressions
  unsigned long t = millis();
  bool blink = (t % 3500) < 180;
  int glance = (t / 3000) % 4; // 0: center, 1: left, 2: center, 3: right
  int eyeOffset = (glance == 1) ? -2 : ((glance == 3) ? 2 : 0);

  if (isVehicleGuardArmed) {
    // Red-alert scanning visor beam sweeps back and forth
    int sweepX = 8 + ((t / 70) % 24);
    display.drawFastHLine(7, 27, 26, SSD1306_WHITE);
    display.fillRect(sweepX, 25, 6, 5, SSD1306_WHITE);
  } else if (blink) {
    // Closed blinking eyelids
    display.drawFastHLine(11, 27, 8, SSD1306_WHITE);
    display.drawFastHLine(27, 27, 8, SSD1306_WHITE);
  } else {
    // Expressive open eyes with pupils
    display.drawCircle(15, 27, 4, SSD1306_WHITE);
    display.drawCircle(31, 27, 4, SSD1306_WHITE);
    display.fillCircle(15 + eyeOffset, 27, 2, SSD1306_WHITE);
    display.fillCircle(31 + eyeOffset, 27, 2, SSD1306_WHITE);
  }
  // Subtle cyber mouth ventilation grille
  display.drawFastHLine(18, 38, 12, SSD1306_WHITE);
  display.drawPixel(21, 39, SSD1306_WHITE);
  display.drawPixel(27, 39, SSD1306_WHITE);

  // 2. Right Side Standby Badges (X: 48 to 126)
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(48, 14);
  display.print("DRIVESPHERE");

  // Inverted standby pill
  display.fillRoundRect(48, 24, 74, 10, 2, SSD1306_WHITE);
  display.setTextColor(SSD1306_BLACK);
  display.setCursor(52, 25);
  if (isVehicleGuardArmed) {
    display.print("GUARD: ON");
  } else {
    display.print("STANDBY: OK");
  }
  display.setTextColor(SSD1306_WHITE);

  // System status metrics
  display.setCursor(48, 37);
  display.print("IP:192.168.4.1");

  // Bottom Footer Ribbon
  display.drawFastHLine(0, 49, 128, SSD1306_WHITE);
  display.setCursor(4, 53);
  display.print("DOUBLE-TAP TO RIDE");
  display.setCursor(114, 53);
  display.print(currentScore);

  drawPagination(2);
  display.display();
}

// SCREEN 3: Full-Screen Cyber Glyph Matrix (Edge-to-Edge Reactive Animations)
void renderGlyphScreen() {
  display.clearDisplay();

  // Full-screen edge-to-edge glyph matrix (14 cols x 12 rows spanning 126x60 px)
  const uint16_t* bitmap = getGlyphBitmap(currentGlyph);
  drawGlyphMatrix(1, 2, bitmap, true, true);

  // Discrete corner pagination dots
  drawPagination(4);

  display.display();
}

// SCREEN 3: Tactical Gyro Horizon & Live GPS Radar
void renderTelemetryScreen() {
  display.clearDisplay();
  drawHeader();

  // Left Half: Aircraft PFD Gyro Horizon (Center at X: 26, Y: 33, R: 18)
  display.drawCircle(26, 33, 18, SSD1306_WHITE);
  // Roll degree index ticks at top arc (0, ±30 deg)
  display.drawFastVLine(26, 13, 2, SSD1306_WHITE); // 0 deg center index
  display.drawPixel(13, 18, SSD1306_WHITE);        // -30 deg mark
  display.drawPixel(39, 18, SSD1306_WHITE);        // +30 deg mark

  // Center Aircraft Symbol: miniature wings and center dot
  display.drawFastHLine(20, 33, 4, SSD1306_WHITE);
  display.drawFastHLine(28, 33, 4, SSD1306_WHITE);
  display.drawPixel(26, 33, SSD1306_WHITE);

  // Tilted Artificial Horizon Line (Clamped to radius 16)
  float rad = currentLeanAngle * 3.14159265f / 180.0f;
  int dx = (int)(cos(rad) * 16.0f);
  int dy = (int)(sin(rad) * 16.0f);
  display.drawLine(26 - dx, 33 - dy, 26 + dx, 33 + dy, SSD1306_WHITE);

  // Horizon Metrics Below Circle
  display.setCursor(2, 54);
  display.print(abs((int)currentLeanAngle));
  display.print((char)247);
  display.print(currentLeanAngle < -2.0f ? "L" : (currentLeanAngle > 2.0f ? "R" : "B"));
  display.setCursor(28, 54);
  display.print(currentAccelG, 1);
  display.print("G");

  // Vertical Divider at X: 48
  display.drawFastVLine(48, 11, 40, SSD1306_WHITE);

  // Right Half: Live GPS Telemetry & Satellite Constellation
  display.setCursor(52, 13);
  display.print(currentGpsFix ? "3D GPS LOCK" : "SEARCHING");

  display.setCursor(52, 23);
  display.print("SATS: ");
  display.print(currentGpsSats);
  display.print(" | ");
  display.print((int)currentGpsAltM);
  display.print("M");

  display.setCursor(52, 33);
  display.print("LA: ");
  display.print(currentGpsLat, 4);

  display.setCursor(52, 42);
  display.print("LO: ");
  display.print(currentGpsLng, 4);

  // Bottom Status
  display.drawFastHLine(0, 51, 128, SSD1306_WHITE);
  display.setCursor(52, 54);
  display.print("PFD/GPS RADAR");
  drawPagination(3);
  display.display();
}



// ---------------------- Bluetooth Pairing Security Screen ----------------------
void renderBtPairingScreen() {
  display.clearDisplay();

  if (isBtPairingActive && btPairingPendingConfirm) {
    int remainingSec = 15 - (millis() - btPairingStartTime) / 1000;
    if (remainingSec < 0) remainingSec = 0;

    // Header
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(6, 2);
    display.print(F("[!] PAIRING REQUEST"));
    display.drawFastHLine(0, 11, 128, SSD1306_WHITE);

    // Passkey Code
    display.setCursor(6, 14);
    display.print(F("CODE:"));
    display.setCursor(42, 14);
    char pinBuf[10];
    if (btPairingCode > 0) {
      snprintf(pinBuf, sizeof(pinBuf), "%06u", btPairingCode);
    } else {
      snprintf(pinBuf, sizeof(pinBuf), "%s", btDynamicPin);
    }
    display.print(pinBuf);

    // Action button prompt
    display.drawRoundRect(2, 25, 124, 24, 4, SSD1306_WHITE);
    display.setCursor(8, 29);
    display.print(F(">> PRESS PUSH-BTN <<"));
    display.setCursor(14, 38);
    display.print(F("TO AUTHORIZE PHONE"));

    // Auto-reject timer
    display.setCursor(6, 53);
    display.print(F("AUTO-REJECT: "));
    display.print(remainingSec);
    display.print(F("S"));
  } else if (millis() - btAuthCompleteTime < 2500 && btAuthCompleteTime > 0) {
    display.drawRoundRect(6, 6, 116, 52, 6, SSD1306_WHITE);
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    if (btPairingSuccess) {
      display.setCursor(12, 16);
      display.print(F("[*] PAIRING APPROVED"));
      display.drawFastHLine(12, 26, 104, SSD1306_WHITE);
      display.setCursor(16, 33);
      display.print(F("PHONE LINKED READY!"));
      display.setCursor(18, 44);
      display.print(F("VEHICLE GUARD ACTIVE"));
    } else {
      display.setCursor(12, 16);
      display.print(F("[X] REJECTED (BLOCK)"));
      display.drawFastHLine(12, 26, 104, SSD1306_WHITE);
      display.setCursor(14, 33);
      display.print(F("UNAUTHORIZED DEVICE"));
      display.setCursor(16, 44);
      display.print(F("ACCESS WAS REFUSED"));
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

  // Render active OLED screen
  switch (activeDisplayMode) {
    case 0:
      renderMainHUD();
      break;
    case 1:
      renderNavigationScreen();
      break;
    case 2:
      renderIdleScreen();
      break;
    case 3:
      renderTelemetryScreen();
      break;
    case 4:
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
  json += "\"device\":\"DriveSphere-AllInOne\",";
  json += "\"hubIp\":\"192.168.4.1\",";
  json += "\"camIp\":\"192.168.4.1\",";
  json += "\"cameraReady\":" + String(cameraFound ? "true" : "false") + ",";
  json += "\"cameraPid\":\"0x" + String(camSensorPid, HEX) + "\",";
  json += "\"flashBrightness\":" + String(flashBrightness) + ",";
  json += "\"stationsConnected\":" + String(WiFi.softAPgetStationNum()) + ",";
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

// ---------------------- Native Camera Handlers ----------------------
void handleCamCapture() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  if (!cameraFound) {
    server.send(503, "application/json", "{\"error\":\"Camera sensor offline\"}");
    return;
  }
  camera_fb_t * fb = esp_camera_fb_get();
  if (!fb) {
    server.send(500, "application/json", "{\"error\":\"Frame capture timed out\"}");
    return;
  }
  server.setContentLength(fb->len);
  server.send(200, "image/jpeg", "");
  WiFiClient client = server.client();
  client.write(fb->buf, fb->len);
  esp_camera_fb_return(fb);
}

void handleCamStream() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  if (!cameraFound) {
    server.send(503, "text/plain", "Camera sensor offline");
    return;
  }

  WiFiClient client = server.client();
  String boundary = "123456789000000000000987654321";
  String head = "HTTP/1.1 200 OK\r\n"
                "Access-Control-Allow-Origin: *\r\n"
                "Content-Type: multipart/x-mixed-replace; boundary=" + boundary + "\r\n\r\n";
  client.print(head);

  unsigned long startTime = millis();
  while (client.connected() && (millis() - startTime < 60000)) {
    camera_fb_t * fb = esp_camera_fb_get();
    if (!fb) break;

    client.print("--" + boundary + "\r\nContent-Type: image/jpeg\r\nContent-Length: " + String(fb->len) + "\r\n\r\n");
    client.write(fb->buf, fb->len);
    client.print("\r\n");
    esp_camera_fb_return(fb);
    delay(40); // ~25 FPS
  }
}

void handleFlash() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  if (server.hasArg("brightness")) {
    flashBrightness = constrain(server.arg("brightness").toInt(), 0, 255);
  } else if (server.hasArg("state")) {
    flashBrightness = (server.arg("state") == "1" || server.arg("state") == "true") ? 150 : 0;
  } else {
    flashBrightness = (flashBrightness > 0) ? 0 : 150;
  }
  setFlashBrightness(flashBrightness);
  server.send(200, "application/json", "{\"flashBrightness\":" + String(flashBrightness) + "}");
}

void handleDisplay() {
  String body = server.hasArg("plain") ? server.arg("plain") : "";
  bool explicitScreen = false;

  // 1. Parse screen parameter
  if (server.hasArg("screen")) {
    int scr = server.arg("screen").toInt();
    if (scr >= 0 && scr <= 4) {
      activeDisplayMode = scr;
      explicitScreen = true;
      lastMotionTime = millis();
    }
  } else if (body.length() > 0) {
    int screenIdx = body.indexOf("\"screen\":");
    if (screenIdx != -1) {
      int scr = body.substring(screenIdx + 9).toInt();
      if (scr >= 0 && scr <= 4) {
        activeDisplayMode = scr;
        explicitScreen = true;
        lastMotionTime = millis();
      }
    }
  }

  // 2. Parse navActive parameter
  if (server.hasArg("navActive")) {
    String val = server.arg("navActive");
    val.toLowerCase();
    isNavActive = (val == "true" || val == "1");
    if (isNavActive) {
      activeDisplayMode = 1;
      explicitScreen = true;
      lastMotionTime = millis();
    }
  } else if (body.length() > 0) {
    int navActiveIdx = body.indexOf("\"navActive\":");
    if (navActiveIdx != -1) {
      String sub = body.substring(navActiveIdx + 11, min((int)body.length(), navActiveIdx + 25));
      sub.toLowerCase();
      if (sub.indexOf("true") != -1 || sub.indexOf("1") != -1) {
        isNavActive = true;
        activeDisplayMode = 1;
        explicitScreen = true;
        lastMotionTime = millis();
      } else if (sub.indexOf("false") != -1 || sub.indexOf("0") != -1) {
        isNavActive = false;
      }
    }
  }

  // 3. Parse navManeuver
  if (server.hasArg("navManeuver")) {
    navManeuver = server.arg("navManeuver");
    navManeuver.trim();
  } else if (body.length() > 0) {
    int manIdx = body.indexOf("\"navManeuver\":");
    if (manIdx != -1) {
      int start = body.indexOf("\"", manIdx + 14) + 1;
      int end = body.indexOf("\"", start);
      if (start > 0 && end > start) {
        navManeuver = body.substring(start, end);
        navManeuver.trim();
      }
    }
  }

  // 4. Parse navDistance
  if (server.hasArg("navDistance")) {
    navDistance = server.arg("navDistance");
    navDistance.trim();
  } else if (body.length() > 0) {
    int distIdx = body.indexOf("\"navDistance\":");
    if (distIdx != -1) {
      int start = body.indexOf("\"", distIdx + 14) + 1;
      int end = body.indexOf("\"", start);
      if (start > 0 && end > start) {
        navDistance = body.substring(start, end);
        navDistance.trim();
      }
    }
  }

  // 5. Parse navEta
  if (server.hasArg("navEta")) {
    navEta = server.arg("navEta");
    navEta.trim();
  } else if (body.length() > 0) {
    int etaIdx = body.indexOf("\"navEta\":");
    if (etaIdx != -1) {
      int start = body.indexOf("\"", etaIdx + 9) + 1;
      int end = body.indexOf("\"", start);
      if (start > 0 && end > start) {
        navEta = body.substring(start, end);
        navEta.trim();
      }
    }
  }

  // 6. Parse navStreet
  if (server.hasArg("navStreet")) {
    navStreet = server.arg("navStreet");
    navStreet.trim();
  } else if (body.length() > 0) {
    int streetIdx = body.indexOf("\"navStreet\":");
    if (streetIdx != -1) {
      int start = body.indexOf("\"", streetIdx + 12) + 1;
      int end = body.indexOf("\"", start);
      if (start > 0 && end > start) {
        navStreet = body.substring(start, end);
        navStreet.trim();
      }
    }
  }

  // 7. Parse Glyph
  if (server.hasArg("glyph")) {
    currentGlyph = server.arg("glyph");
    currentGlyph.trim();
    if (!explicitScreen) activeDisplayMode = 4;
    lastMotionTime = millis();
  } else if (body.length() > 0) {
    int glyphIdx = body.indexOf("\"glyph\":");
    if (glyphIdx != -1) {
      int start = body.indexOf("\"", glyphIdx + 8) + 1;
      int end = body.indexOf("\"", start);
      if (start > 0 && end > start) {
        currentGlyph = body.substring(start, end);
        currentGlyph.trim();
        if (!explicitScreen) activeDisplayMode = 4;
        lastMotionTime = millis();
      }
    }
  }

  // 8. Parse Speed & Score & Guard
  if (body.length() > 0) {
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
    int guardIdx = body.indexOf("\"guardArmed\":");
    if (guardIdx != -1) {
      isVehicleGuardArmed = body.substring(guardIdx + 13).startsWith("true");
    }
  }

  Serial.printf("[Display Engine] Screen=%d | NavActive=%d | Maneuver=%s | Dist=%s | Street=%s\n",
                activeDisplayMode, isNavActive ? 1 : 0, navManeuver.c_str(), navDistance.c_str(), navStreet.c_str());

  updateOLED();
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"status\":\"success\",\"screen\":" + String(activeDisplayMode) + "}");
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

void handleRoot() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
    "<title>DriveSphere Guardian Hub</title>"
    "<style>"
    "body{background:#0d0f12;color:#fff;font-family:system-ui,-apple-system,sans-serif;margin:0;padding:20px;text-align:center;}"
    ".card{background:#1a1d24;border:1px solid #2d3340;border-radius:12px;max-width:440px;margin:20px auto;padding:24px;box-shadow:0 8px 24px rgba(0,0,0,0.5);}"
    "h1{color:#00e5ff;font-size:22px;letter-spacing:2px;margin-top:0;}"
    ".stat{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #232834;font-size:15px;}"
    ".val{font-weight:bold;color:#a0aec0;}"
    ".val.active{color:#00e676;}"
    ".val.sos{color:#ff1744;}"
    "a.btn{display:inline-block;background:#00e5ff;color:#000;padding:10px 20px;border-radius:8px;text-decoration:none;font-weight:bold;margin:8px 4px;}"
    "</style></head><body>"
    "<div class='card'>"
    "<h1>DRIVESPHERE HUB</h1>"
    "<div class='stat'><span>Hardware Mode</span><span class='val active'>ESP32 Guardian</span></div>"
    "<div class='stat'><span>Active Screen</span><span class='val'>" + String(activeDisplayMode) + "</span></div>"
    "<div class='stat'><span>GPS Fix</span><span class='val " + String(currentGpsFix ? "active" : "") + "'>" + (currentGpsFix ? ("3D LOCK (" + String(currentGpsSats) + " Sats)") : "SEARCHING...") + "</span></div>"
    "<div class='stat'><span>Standby Glyph</span><span class='val active'>" + currentGlyph + "</span></div>"
    "<div class='stat'><span>Speed</span><span class='val'>" + String(currentSpeed) + " km/h</span></div>"
    "<div class='stat'><span>Emergency SOS</span><span class='val " + String(isSosTriggered ? "sos" : "active") + "'>" + (isSosTriggered ? "TRIGGERED!" : "NORMAL") + "</span></div>"
    "<br>"
    "<div style='font-size:13px;color:#a0aec0;margin-bottom:8px;'>TEST OLED GLYPH PATTERNS</div>"
    "<a class='btn' style='font-size:12px;padding:6px 10px;' href='/set_glyph?name=IDLE_FACE'>IDLE</a>"
    "<a class='btn' style='font-size:12px;padding:6px 10px;' href='/set_glyph?name=SHIELD'>SHIELD</a>"
    "<a class='btn' style='font-size:12px;padding:6px 10px;' href='/set_glyph?name=ROCKET'>ROCKET</a>"
    "<a class='btn' style='font-size:12px;padding:6px 10px;' href='/set_glyph?name=ALERT'>ALERT</a>"
    "<a class='btn' style='font-size:12px;padding:6px 10px;' href='/set_glyph?name=TURN_LEFT'>LEFT</a>"
    "<a class='btn' style='font-size:12px;padding:6px 10px;' href='/set_glyph?name=TURN_RIGHT'>RIGHT</a>"
    "<br><br>"
    "<a class='btn' href='/status'>JSON Status</a>"
    "<a class='btn' href='/telemetry'>Live Telemetry</a>"
    "</div><script>setTimeout(function(){location.reload();},2500);</script></body></html>";
  server.send(200, "text/html", html);
}

void handleSetGlyph() {
  if (server.hasArg("name")) {
    currentGlyph = server.arg("name");
    currentGlyph.trim();
    Serial.printf("[Glyph Engine] Set glyph to: %s\n", currentGlyph.c_str());
    updateOLED();
  }
  server.sendHeader("Location", "/");
  server.send(303, "text/plain", "Redirecting...");
}

void handleNotFound() {
  if (server.uri() == "/generate_204") {
    server.send(204, "text/plain", "");
  } else {
    server.send(404, "text/plain", "DriveSphere Hub - Not Found");
  }
}

// ---------------------- Push Button Handler ----------------------
void checkButton() {
  int rawReading = digitalRead(BUTTON_PIN);
  unsigned long now = millis();

  // 1. Debounce state machine
  static int debouncedState = HIGH;
  static int prevRaw = HIGH;
  static unsigned long stateChangeTime = 0;
  static unsigned long pendingTapReleaseTime = 0;
  static int tapCount = 0;

  if (rawReading != prevRaw) {
    prevRaw = rawReading;
    stateChangeTime = now;
  }

  // Require raw reading to remain rock-solid stable for 60ms
  if ((now - stateChangeTime) >= 60) {
    if (rawReading != debouncedState) {
      debouncedState = rawReading;

      if (debouncedState == LOW) {
        // Genuine button pressed down
        isButtonPressed = true;
        buttonPressStartTime = now;
        longPressTriggered = false;
      } else {
        // Genuine button released up
        if (isButtonPressed) {
          isButtonPressed = false;
          unsigned long pressDuration = now - buttonPressStartTime;

          // Check if it was during Bluetooth authorization
          if (isBtPairingActive && btPairingPendingConfirm) {
            btPairingPendingConfirm = false;
            isBtPairingActive = false;
            btPairingSuccess = true;
            btAuthCompleteTime = now;
#if ENABLE_BLUETOOTH
            SerialBT.confirmReply(true);
#endif
            Serial.println(F("[Bluetooth Security] Button Pressed -> Pairing Approved!"));
            updateOLED();
          } else if (!longPressTriggered && pressDuration >= 60) {
            tapCount++;
            pendingTapReleaseTime = now;
          }
        }
      }
    }
  }

  // 2. Multi-Tap Arbiter (fires after 300ms window)
  if (tapCount > 0 && !isButtonPressed && (now - pendingTapReleaseTime > 300)) {
    if (tapCount == 1) {
      // Single Tap: Cycle through all 5 screens (0, 1, 2, 3, 4)
      activeDisplayMode = (activeDisplayMode + 1) % 5;
      Serial.printf("[Button] Single Tap -> Switched to Screen %d\n", activeDisplayMode);
      lastMotionTime = now;
      updateOLED();
    } else if (tapCount >= 2) {
      // Double Tap: Direct shortcut to Idle Screen (Screen 2) or Cockpit HUD (Screen 0)
      activeDisplayMode = (activeDisplayMode == 2) ? 0 : 2;
      Serial.printf("[Button] Double Tap -> Switched to Screen %d\n", activeDisplayMode);
      lastMotionTime = now;
      updateOLED();
    }
    tapCount = 0;
    pendingTapReleaseTime = 0;
  }

  // 3. Long Press check (held LOW for >= 3000ms)
  if (isButtonPressed && !longPressTriggered && (now - buttonPressStartTime >= LONG_PRESS_MS)) {
    longPressTriggered = true;
    tapCount = 0; // Clear pending taps
    if (isSosTriggered) {
      clearSos();
    } else {
      isSosTriggered = true;
      isCrashDetected = false;
      peakCrashG = 0.0f;
      peakTiltDeg = 0.0f;
      updateOLED();
    }
#if ENABLE_BLUETOOTH
    SerialBT.println("{\"sosTriggered\":" + String(isSosTriggered ? "true" : "false") + "}");
#endif
    Serial.println(F("[Button] 3-Second Long Press -> SOS Toggled!"));
  }
}

// ---------------------- Bluetooth Callbacks & Handler ----------------------
#if ENABLE_BLUETOOTH
void BTConfirmRequestCallback(uint32_t numVal) {
  btPairingCode = numVal;
  isBtPairingActive = true;
  btPairingPendingConfirm = true;
  btPairingStartTime = millis();
  btPairingNeedsUpdate = true;
  Serial.printf("[Bluetooth Security] Phone Pairing Request! Passkey: %06u. Awaiting rider button press...\n", numVal);
  // Intentionally NO auto-reply! Rider MUST physically click the button on the vehicle to authorize!
}

void BTAuthCompleteCallback(boolean success) {
  if (success) {
    Serial.println(F("[Bluetooth Security] Authentication Success! Device Paired."));
    btPairingSuccess = true;
  } else {
    Serial.println(F("[Bluetooth Security] Authentication Failed or Cancelled."));
    btPairingSuccess = false;
  }
  btAuthCompleteTime = millis();
  isBtPairingActive = false;
  btPairingPendingConfirm = false;
  btPairingNeedsUpdate = true;
}

void handleBluetooth() {
  // Auto-reject unauthorized pairing attempts after 15s if physical button was not pressed
  if (isBtPairingActive && btPairingPendingConfirm && (millis() - btPairingStartTime > 15000)) {
    btPairingPendingConfirm = false;
    isBtPairingActive = false;
    btPairingSuccess = false;
    btAuthCompleteTime = millis();
    SerialBT.confirmReply(false); // Reject unauthorized attempt!
    Serial.println(F("[Bluetooth Security] Timeout! Button NOT pressed -> Inbound pairing REJECTED!"));
    updateOLED();
  }

  // Stealth mode: When owner's phone is linked, hide Hub from Bluetooth discovery scans
  static bool btWasConnected = false;
  if (SerialBT.hasClient() && !btWasConnected) {
    btWasConnected = true;
    esp_bt_gap_set_scan_mode(ESP_BT_CONNECTABLE, ESP_BT_NON_DISCOVERABLE);
    Serial.println(F("[Bluetooth Security] Phone connected. Hub set to STEALTH / NON-DISCOVERABLE."));
  } else if (!SerialBT.hasClient() && btWasConnected) {
    btWasConnected = false;
    esp_bt_gap_set_scan_mode(ESP_BT_CONNECTABLE, ESP_BT_GENERAL_DISCOVERABLE);
    Serial.println(F("[Bluetooth Security] Phone disconnected. Hub discoverable for owner reconnect."));
  }

  // Process Bluetooth Serial input
  if (SerialBT.available()) {
    String line = SerialBT.readStringUntil('\n');
    processCommand(line, true);
  }

  // Periodic BT telemetry broadcast every 1.5s
  if (millis() - lastBtBroadcastTime > 1500) {
    lastBtBroadcastTime = millis();
    SerialBT.println(buildTelemetryJson());
  }
}
#endif

void sendReply(const String& str, bool isBT) {
#if ENABLE_BLUETOOTH
  if (isBT) SerialBT.print(str);
  else Serial.print(str);
#else
  Serial.print(str);
#endif
}

void sendReplyLine(const String& str, bool isBT) {
#if ENABLE_BLUETOOTH
  if (isBT) SerialBT.println(str);
  else Serial.println(str);
#else
  Serial.println(str);
#endif
}

void processCommand(String line, bool isBT) {
  line.trim();
  if (line.length() == 0) return;

  if (line.startsWith("SPEED:")) {
    int spd = line.substring(6).toInt();
    if (!currentGpsFix || spd > 0) currentSpeed = spd;
    if (currentSpeed > 0) lastMotionTime = millis();
    updateOLED();
    if (!isBT) Serial.printf("[Serial] Speed set to %d km/h\n", currentSpeed);
  } else if (line.startsWith("SCORE:")) {
    currentScore = line.substring(6).toInt();
    updateOLED();
    if (!isBT) Serial.printf("[Serial] Score set to %d\n", currentScore);
  } else if (line.startsWith("MODE:")) {
    vehicleMode = line.substring(5);
    vehicleMode.trim();
    updateOLED();
    if (!isBT) Serial.printf("[Serial] Mode set to %s\n", vehicleMode.c_str());
  } else if (line.startsWith("GLYPH:")) {
    currentGlyph = line.substring(6);
    currentGlyph.trim();
    activeDisplayMode = 4; // Switch to Screen 4: Full-Screen Cyber Glyph Matrix!
    lastMotionTime = millis();
    updateOLED();
    if (!isBT) Serial.printf("[Serial] Glyph set to %s -> Screen 4\n", currentGlyph.c_str());
  } else if (line.startsWith("NAV:")) {
    String navPayload = line.substring(4);
    navPayload.trim();
    if (navPayload == "STOP") {
      isNavActive = false;
      activeDisplayMode = 0;
    } else {
      isNavActive = true;
      activeDisplayMode = 1; // Navigation Screen!
      int c1 = navPayload.indexOf(',');
      int c2 = navPayload.indexOf(',', c1 + 1);
      int c3 = navPayload.indexOf(',', c2 + 1);
      if (c1 != -1) navManeuver = navPayload.substring(0, c1);
      if (c2 != -1) navDistance = navPayload.substring(c1 + 1, c2);
      if (c3 != -1) {
        navEta = navPayload.substring(c2 + 1, c3);
        navStreet = navPayload.substring(c3 + 1);
      } else if (c2 != -1) {
        navStreet = navPayload.substring(c2 + 1);
      }
    }
    lastMotionTime = millis();
    updateOLED();
    if (!isBT) Serial.printf("[Serial] Navigation: Screen 1, %s, %s, %s, %s\n", navManeuver.c_str(), navDistance.c_str(), navEta.c_str(), navStreet.c_str());
  } else if (line.startsWith("SCREEN:")) {
    int scr = line.substring(7).toInt();
    if (scr >= 0 && scr <= 4) activeDisplayMode = scr;
    lastMotionTime = millis();
    updateOLED();
    if (!isBT) Serial.printf("[Serial] Active Screen switched to %d\n", activeDisplayMode);
  } else if (line.startsWith("GUARD:")) {
    isVehicleGuardArmed = (line.substring(6) == "ARMED");
    if (!isVehicleGuardArmed) isTamperDetected = false;
    updateOLED();
  } else if (line == "RESET_SOS") {
    clearSos();
    sendReplyLine("{\"status\":\"reset_ok\"}", isBT);
  } else if (line == "STATUS" || line == "TELEMETRY") {
    String json = buildTelemetryJson();
    sendReplyLine(json, isBT);
  } else if (line == "CAM" || line == "CHECK_CAM") {
    if (cameraFound) {
      camera_fb_t * fb = esp_camera_fb_get();
      if (fb) {
        Serial.printf("[HARDWARE SENSOR CONFIRMED] Sensor PID: 0x%02X | Frame: %u bytes JPEG captured!\n", camSensorPid, fb->len);
        char cbuf[96];
        snprintf(cbuf, sizeof(cbuf), "{\"camera\":\"OK\",\"pid\":\"0x%02X\",\"bytes\":%u}\n", camSensorPid, fb->len);
        sendReply(String(cbuf), isBT);
        esp_camera_fb_return(fb);
      } else {
        Serial.println(F("[HARDWARE SENSOR ERROR] Sensor initialized but frame capture timed out."));
      }
    } else {
      Serial.println(F("[HARDWARE SENSOR NOTICE] Camera sensor offline. Retrying init..."));
      if (initCamera()) {
        Serial.println(F("[HARDWARE SENSOR CONFIRMED] Camera successfully initialized on retry!"));
      } else {
        Serial.println(F("[HARDWARE SENSOR RESULT] No camera sensor response. Check ribbon cable."));
      }
    }
  } else if (line == "SCAN") {
    String out = "[I2C SCAN START]\n";
    struct PinPair { int sda; int scl; const char* name; };
    PinPair pairs[] = {
      { 15, 14, "SDA=15, SCL=14" },
      { 13, 2,  "SDA=13, SCL=2" },
      { 14, 15, "SDA=14, SCL=15" },
      { 2,  13, "SDA=2, SCL=13" }
    };
    for (auto& p : pairs) {
      Wire.end();
      pinMode(p.sda, INPUT_PULLUP);
      pinMode(p.scl, INPUT_PULLUP);
      Wire.setPins(p.sda, p.scl);
      Wire.begin(p.sda, p.scl);
      Wire.setClock(100000);
      Wire.setTimeOut(25);
      out += "Pins (" + String(p.name) + "): ";
      int foundCount = 0;
      for (byte addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        if (Wire.endTransmission() == 0) {
          char hexBuf[10];
          snprintf(hexBuf, sizeof(hexBuf), "0x%02X ", addr);
          out += hexBuf;
          foundCount++;
        }
      }
      if (foundCount == 0) out += "NONE";
      out += "\n";
    }
    // Restore chosen pins
    Wire.end();
    Wire.begin(chosenSda, chosenScl);
    out += "[I2C SCAN COMPLETE]\n";
    sendReply(out, isBT);
  } else if (line == "INIT_OLED") {
    bool ok = initOledDisplay();
    String resp = ok ? "{\"oledInit\":true,\"sda\":" + String(chosenSda) + ",\"scl\":" + String(chosenScl) + ",\"addr\":\"0x" + String(activeOledAddr, HEX) + "\"}\n"
                     : "{\"oledInit\":false}\n";
    sendReply(resp, isBT);
  } else if (line == "REBOOT" || line == "RESTART") {
    Serial.println("[System] Rebooting ESP32...");
    delay(100);
    ESP.restart();
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

bool initOledDisplay() {
  struct I2CPinCandidate { int sda; int scl; const char* desc; };
  I2CPinCandidate pinCandidates[] = {
    { 15, 14, "Configured Pins (SDA=15, SCL=14)" },
    { 13, 2,  "Alternate Header Pins (SDA=13, SCL=2)" },
    { 14, 15, "Reversed Wiring (SDA=14, SCL=15)" },
    { 2,  13, "Reversed Alternate Pins (SDA=2, SCL=13)" }
  };

  byte oledAddr = 0;
  oledFound = false;

  for (const auto& candidate : pinCandidates) {
    Wire.end();
    pinMode(candidate.sda, INPUT_PULLUP);
    pinMode(candidate.scl, INPUT_PULLUP);
    Wire.setPins(candidate.sda, candidate.scl);
    Wire.begin(candidate.sda, candidate.scl);
    Wire.setClock(100000);
    Wire.setTimeOut(25); // Essential 25ms timeout prevents hanging if pins are floating!

    for (byte addr = 0x3C; addr <= 0x3D; addr++) {
      Wire.beginTransmission(addr);
      if (Wire.endTransmission() == 0) {
        oledAddr = addr;
        chosenSda = candidate.sda;
        chosenScl = candidate.scl;
        activeOledAddr = addr;
        Serial.printf("[DriveSphere] Auto-Detected OLED at 0x%02X on %s!\n", addr, candidate.desc);
        break;
      }
    }
    if (oledAddr != 0) break;
  }

  if (oledAddr == 0) {
    Serial.println(F("[DriveSphere] Notice: Auto-scan did not find OLED ACK. Initializing default SDA=15, SCL=14."));
    Wire.end();
    pinMode(15, INPUT_PULLUP);
    pinMode(14, INPUT_PULLUP);
    Wire.setPins(15, 14);
    Wire.begin(15, 14);
    Wire.setClock(100000);
    oledAddr = 0x3C;
    chosenSda = 15;
    chosenScl = 14;
    activeOledAddr = 0x3C;
  }

  if (display.begin(SSD1306_SWITCHCAPVCC, oledAddr, false, false)) {
    oledFound = true;
    display.ssd1306_command(SSD1306_SETCONTRAST);
    display.ssd1306_command(0xFF); // Maximum display brightness
    display.clearDisplay();
    display.display();
    Serial.printf("[DriveSphere] OLED SSD1306 Initialized at 0x%02X!\n", oledAddr);
    return true;
  } else {
    byte altAddr = (oledAddr == 0x3C) ? 0x3D : 0x3C;
    if (display.begin(SSD1306_SWITCHCAPVCC, altAddr, false, false)) {
      oledFound = true;
      activeOledAddr = altAddr;
      display.ssd1306_command(SSD1306_SETCONTRAST);
      display.ssd1306_command(0xFF);
      display.clearDisplay();
      display.display();
      Serial.printf("[DriveSphere] OLED SSD1306 Initialized at alternate 0x%02X!\n", altAddr);
      return true;
    }
  }
  return false;
}

// ---------------------- Setup & Main Loop ----------------------
void setup() {
  // 1. Disable brownout detector during high-draw camera + Wi-Fi bursts
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  // 2. Initialize Flashlight LED with PWM
  ledcAttach(FLASH_LED_PIN, FLASH_PWM_FREQ, FLASH_PWM_RES);
  setFlashBrightness(0);

  // 3. Probe & Initialize OV2640 Image Sensor FIRST (Deconflicted I2C)
  initCamera();

  // 4. Initialize Hardware UART2 for NEO-M8N GPS (PSRAM-Safe Pinout)
#if GPS_RX_PIN >= 0
  SerialGPS.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
  Serial.printf("[DriveSphere] NEO-M8N GPS UART2 started on RX=%d @ %d baud (PSRAM Protected)\n", GPS_RX_PIN, GPS_BAUD);
#else
  Serial.println(F("[DriveSphere] GPS UART2 disabled to preserve PSRAM"));
#endif

  // 5. Initialize OLED Display (I2C Bus 0)
  initOledDisplay();
  if (oledFound) {
    showLoadingSplash(35, cameraFound ? "CAMERA + OLED OK" : "BOOTING SYSTEM");
  }

  // 6. Initialize MPU-6050 IMU on shared Wire bus
  initMPU6050();
  if (oledFound) showLoadingSplash(60, "CALIBRATING IMU");

  // 7. Start Wi-Fi in High-Performance SoftAP Mode (Fixed Channel 1, No Sleep)
  WiFi.mode(WIFI_AP);
  WiFi.setSleep(false);
  IPAddress local_ip(192, 168, 4, 1);
  IPAddress gateway(192, 168, 4, 1);
  IPAddress subnet(255, 255, 255, 0);
  WiFi.softAPConfig(local_ip, gateway, subnet);
  WiFi.softAP(apSSID, apPassword, 1, 0, 4);
  IPAddress IP = WiFi.softAPIP();
  Serial.print(F("[DriveSphere] Wi-Fi SoftAP Started on: "));
  Serial.println(IP);

  Serial.printf("[DriveSphere Memory] Free DRAM: %u bytes | Free PSRAM: %u bytes\n", ESP.getFreeHeap(), ESP.getFreePsram());
  if (oledFound) showLoadingSplash(75, "STARTING COMMS");

  // Generate random dynamic 6-digit rolling PIN
  uint32_t randCode = esp_random() % 900000 + 100000;
  snprintf(btDynamicPin, sizeof(btDynamicPin), "%06u", randCode);

#if ENABLE_BLUETOOTH
  // Start Bluetooth Classic SPP
  SerialBT.enableSSP();
  SerialBT.onConfirmRequest(BTConfirmRequestCallback);
  SerialBT.onAuthComplete(BTAuthCompleteCallback);
  SerialBT.begin("DriveSphere-Hub");
  SerialBT.setPin(btDynamicPin, 6);
  Serial.printf("[DriveSphere Security] Bluetooth Active as 'DriveSphere-Hub' (PIN: %s | Physical Button Auth: ENABLED)\n", btDynamicPin);
#else
  Serial.println(F("[DriveSphere Comms] High-Speed Wi-Fi SoftAP Mode Active (192.168.4.1)"));
#endif
  if (oledFound) {
    showLoadingSplash(100, "SYSTEM READY");
    delay(400);
  }

  // Register Web Server Endpoints
  server.on("/", HTTP_GET, handleRoot);
  server.on("/generate_204", HTTP_GET, handleStatus);
  server.on("/status", HTTP_GET, handleStatus);
  server.on("/telemetry", HTTP_GET, handleTelemetry);
  server.on("/display", HTTP_POST, handleDisplay);
  server.on("/display", HTTP_GET, handleDisplay);
  server.on("/navigation", HTTP_POST, handleDisplay);
  server.on("/navigation", HTTP_GET, handleDisplay);
  server.on("/set_glyph", HTTP_GET, handleSetGlyph);
  server.on("/sos", HTTP_GET, handleSOS);
  server.on("/reset_sos", HTTP_POST, handleResetSOS);
  server.on("/capture", HTTP_GET, handleCamCapture);
  server.on("/stream", HTTP_GET, handleCamStream);
  server.on("/flash", HTTP_GET, handleFlash);
  server.on("/flash", HTTP_POST, handleFlash);
  server.onNotFound(handleNotFound);

  server.begin();
  Serial.println(F("[DriveSphere] All-In-One HTTP Server listening on port 80"));

  updateOLED();
}

void loop() {
  server.handleClient();
  checkButton();
  readGPS();
  readMPU6050();
#if ENABLE_BLUETOOTH
  handleBluetooth();
  if (btPairingNeedsUpdate) {
    btPairingNeedsUpdate = false;
    updateOLED();
  }
#else
  if (Serial.available()) {
    String line = Serial.readStringUntil('\n');
    processCommand(line, false);
  }
#endif

  // Refresh OLED periodically (every 150ms)
  if (millis() - lastDisplayRefreshTime > 150) {
    lastDisplayRefreshTime = millis();
    updateOLED();
  }
}
