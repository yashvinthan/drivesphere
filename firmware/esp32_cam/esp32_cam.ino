/*
 * ======================================================================================
 * DriveSphere AI Guardian — ESP32-CAM Firmware
 * Model: AI-Thinker ESP32-CAM with OV2640 Sensor
 *
 * Capabilities:
 *   - High-Speed MJPEG Video Streaming on `/stream` (up to 20 FPS in VGA/SVGA)
 *   - Real-Time JPEG Snapshot Capture on `/capture` (for AI helmet & seatbelt inspection)
 *   - On-Board High-Power Flashlight Control on `/flash` (GPIO 4)
 *   - Dual Wi-Fi Networking:
 *       1. Attempts to connect to vehicle's Guardian Hub (`DriveSphere-Hub`)
 *       2. If Hub is not detected within 5 seconds, boots standalone AP (`DriveSphere-Cam`)
 *   - REST Diagnostics on `/status`
 * ======================================================================================
 */

#include "esp_camera.h"
#include <WiFi.h>
#include <WebServer.h>

// ---------------------- AI-Thinker Camera Pinout ----------------------
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27

#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

#define FLASH_LED_PIN      4   // High-power onboard white illumination LED
#define STATUS_LED_PIN    33   // Small red indicator LED (Active LOW)

// ---------------------- Wi-Fi Configuration ----------------------
const char* hubSSID = "DriveSphere-Hub";
const char* hubPassword = "drivesphere123";

const char* apSSID = "DriveSphere-Cam";
const char* apPassword = "drivesphere123";

WebServer server(80);

bool isFlashOn = false;
bool isCameraInitialized = false;

// ---------------------- Camera Initialization ----------------------
bool initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0       = Y2_GPIO_NUM;
  config.pin_d1       = Y3_GPIO_NUM;
  config.pin_d2       = Y4_GPIO_NUM;
  config.pin_d3       = Y5_GPIO_NUM;
  config.pin_d4       = Y6_GPIO_NUM;
  config.pin_d5       = Y7_GPIO_NUM;
  config.pin_d6       = Y8_GPIO_NUM;
  config.pin_d7       = Y9_GPIO_NUM;
  config.pin_xclk     = XCLK_GPIO_NUM;
  config.pin_pclk     = PCLK_GPIO_NUM;
  config.pin_vsync    = VSYNC_GPIO_NUM;
  config.pin_href     = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn     = PWDN_GPIO_NUM;
  config.pin_reset    = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  // Frame size & quality configuration
  if (psramFound()) {
    Serial.println(F("[DriveSphere Cam] PSRAM detected! Enabling high resolution."));
    config.frame_size = FRAMESIZE_VGA;   // 640x480 (Ideal for smooth streaming & AI detection)
    config.jpeg_quality = 12;            // 10-63, lower means higher quality
    config.fb_count = 2;
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.grab_mode = CAMERA_GRAB_LATEST;
  } else {
    Serial.println(F("[DriveSphere Cam] No PSRAM found, using internal RAM."));
    config.frame_size = FRAMESIZE_QVGA;  // 320x240 fallback
    config.jpeg_quality = 14;
    config.fb_count = 1;
    config.fb_location = CAMERA_FB_IN_DRAM;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[DriveSphere Cam] Camera init failed with error 0x%x\n", err);
    return false;
  }

  sensor_t * s = esp_camera_sensor_get();
  if (s != NULL) {
    // Correct color and balance for road environments
    s->set_brightness(s, 1);     // -2 to 2
    s->set_contrast(s, 0);       // -2 to 2
    s->set_saturation(s, 0);     // -2 to 2
    s->set_whitebal(s, 1);       // 0 = disable , 1 = enable
    s->set_awb_gain(s, 1);       // 0 = disable , 1 = enable
    s->set_wb_mode(s, 0);        // 0 to 4 - 0: Auto
    s->set_exposure_ctrl(s, 1);  // 0 = disable , 1 = enable
    s->set_aec2(s, 0);           // 0 = disable , 1 = enable
    s->set_gain_ctrl(s, 1);      // 0 = disable , 1 = enable
    s->set_vflip(s, 0);          // Flip vertical (0: normal, 1: inverted)
    s->set_hmirror(s, 0);        // Mirror horizontal
  }

  Serial.println(F("[DriveSphere Cam] OV2640 Camera Initialized!"));
  return true;
}

// ---------------------- HTTP Handlers ----------------------

// 1. Single JPEG Snapshot Capture (GET /capture)
void handleCapture() {
  if (!isCameraInitialized) {
    server.send(503, "text/plain", "Camera not initialized");
    return;
  }

  camera_fb_t * fb = esp_camera_fb_get();
  if (!fb) {
    server.send(500, "text/plain", "Camera capture failed");
    return;
  }

  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Content-Disposition", "inline; filename=capture.jpg");
  server.setContentLength(fb->len);
  server.send(200, "image/jpeg", "");
  
  WiFiClient client = server.client();
  client.write(fb->buf, fb->len);
  
  esp_camera_fb_return(fb);
}

// 2. High-Speed Live MJPEG Stream (GET /stream)
void handleStream() {
  if (!isCameraInitialized) {
    server.send(503, "text/plain", "Camera not initialized");
    return;
  }

  WiFiClient client = server.client();

  String response = "HTTP/1.1 200 OK\r\n";
  response += "Access-Control-Allow-Origin: *\r\n";
  response += "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n";
  client.print(response);

  // Stream loop while client remains connected
  while (client.connected()) {
    camera_fb_t * fb = esp_camera_fb_get();
    if (!fb) {
      delay(10);
      continue;
    }

    client.print("--frame\r\n");
    client.print("Content-Type: image/jpeg\r\n");
    client.print("Content-Length: " + String(fb->len) + "\r\n\r\n");
    client.write(fb->buf, fb->len);
    client.print("\r\n");

    esp_camera_fb_return(fb);
    delay(30); // ~25-30 FPS cap to maintain Wi-Fi stability
  }
}

// 3. High-Power Flash LED Toggle (POST /flash)
void handleFlash() {
  if (server.hasArg("state")) {
    String stateArg = server.arg("state");
    isFlashOn = (stateArg == "1" || stateArg == "true" || stateArg == "on");
  } else {
    isFlashOn = !isFlashOn;
  }

  digitalWrite(FLASH_LED_PIN, isFlashOn ? HIGH : LOW);

  String json = "{\"flash\":" + String(isFlashOn ? "true" : "false") + "}";
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", json);
}

// 4. Invert / Flip Orientation (POST /flip)
void handleFlip() {
  sensor_t * s = esp_camera_sensor_get();
  if (!s) {
    server.send(500, "application/json", "{\"error\":\"sensor_null\"}");
    return;
  }

  int vflip = server.hasArg("vflip") ? server.arg("vflip").toInt() : 0;
  int hmirror = server.hasArg("hmirror") ? server.arg("hmirror").toInt() : 0;

  s->set_vflip(s, vflip);
  s->set_hmirror(s, hmirror);

  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"status\":\"ok\"}");
}

// 5. System Status (GET /status)
void handleStatus() {
  String ip = (WiFi.getMode() == WIFI_STA) ? WiFi.localIP().toString() : WiFi.softAPIP().toString();
  String mode = (WiFi.getMode() == WIFI_STA) ? "STATION (Connected to Hub)" : "ACCESS_POINT";

  String json = "{";
  json += "\"device\":\"DriveSphere-AI-Cam\",";
  json += "\"cameraReady\":" + String(isCameraInitialized ? "true" : "false") + ",";
  json += "\"flashActive\":" + String(isFlashOn ? "true" : "false") + ",";
  json += "\"wifiMode\":\"" + mode + "\",";
  json += "\"ip\":\"" + ip + "\",";
  json += "\"streamUrl\":\"http://" + ip + "/stream\",";
  json += "\"captureUrl\":\"http://" + ip + "/capture\"";
  json += "}";

  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", json);
}

void handleNotFound() {
  server.send(404, "text/plain", "DriveSphere AI Camera - Not Found");
}

// ---------------------- Setup & Main Loop ----------------------
void setup() {
  Serial.begin(115200);
  Serial.println(F("\n============================================="));
  Serial.println(F("  DriveSphere AI Dashcam & Rider Safety Cam"));
  Serial.println(F("============================================="));

  pinMode(FLASH_LED_PIN, OUTPUT);
  digitalWrite(FLASH_LED_PIN, LOW); // Flash off by default

  pinMode(STATUS_LED_PIN, OUTPUT);
  digitalWrite(STATUS_LED_PIN, HIGH); // Red LED off (active low)

  // Initialize OV2640 Sensor
  isCameraInitialized = initCamera();

  // Try connecting to existing DriveSphere-Hub Wi-Fi AP
  Serial.print(F("[DriveSphere Cam] Searching for Guardian Hub: "));
  Serial.println(hubSSID);

  WiFi.mode(WIFI_STA);
  WiFi.begin(hubSSID, hubPassword);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 10) {
    delay(500);
    Serial.print(F("."));
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println(F("\n[DriveSphere Cam] Connected to Guardian Hub!"));
    Serial.print(F("Camera Station IP: "));
    Serial.println(WiFi.localIP());
  } else {
    // Fallback: Start Standalone Camera Access Point
    Serial.println(F("\n[DriveSphere Cam] Guardian Hub not detected. Starting Standalone AP: DriveSphere-Cam..."));
    WiFi.mode(WIFI_AP);
    WiFi.softAP(apSSID, apPassword);
    Serial.print(F("Camera AP IP: "));
    Serial.println(WiFi.softAPIP());
  }

  // Register Web Endpoints
  server.on("/stream", HTTP_GET, handleStream);
  server.on("/capture", HTTP_GET, handleCapture);
  server.on("/flash", HTTP_POST, handleFlash);
  server.on("/flip", HTTP_POST, handleFlip);
  server.on("/status", HTTP_GET, handleStatus);
  server.onNotFound(handleNotFound);

  server.begin();
  Serial.println(F("[DriveSphere Cam] HTTP Server Started on Port 80."));

  // Blink status LED twice to signal ready
  digitalWrite(STATUS_LED_PIN, LOW);
  delay(100);
  digitalWrite(STATUS_LED_PIN, HIGH);
  delay(100);
  digitalWrite(STATUS_LED_PIN, LOW);
  delay(100);
  digitalWrite(STATUS_LED_PIN, HIGH);
}

void loop() {
  server.handleClient();
}
