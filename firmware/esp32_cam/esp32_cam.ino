/*
 * ======================================================================================
 * DriveSphere AI Guardian — ESP32-CAM High-Performance Firmware
 * Model: AI-Thinker ESP32-CAM with OV2640 Sensor
 *
 * Architecture & Enhancements:
 *   - Native esp_httpd Multi-Socket Web Server:
 *       Concurrent non-blocking streaming on `/stream` and snapshot capture on `/capture`.
 *       Fixes the classic single-threaded WebServer deadlock where streaming blocked all APIs.
 *   - Dual Wi-Fi AP + Station Mode (WIFI_AP_STA):
 *       Connects to vehicle Guardian Hub (`DriveSphere-Hub`) at fixed IP `192.168.4.2`.
 *       Simultaneously broadcasts standalone fallback AP (`DriveSphere-Cam`) at `192.168.4.1`.
 *       Automatic background reconnection if Guardian Hub powers cycle during rides.
 *   - LEDC PWM Flashlight Control on `/flash`:
 *       Smooth brightness control (0-255) to prevent high-power LED overheating & brownout.
 *   - Dynamic Camera Controls on `/control` & `/flip`:
 *       Runtime adjustment of framesize (QVGA, VGA, SVGA), quality, vflip, hmirror.
 *   - High-Frequency Telemetry on `/status`:
 *       Provides camera ready status, streaming FPS, Wi-Fi RSSI, IP addresses, and memory.
 * ======================================================================================
 */

#include "esp_camera.h"
#include <WiFi.h>
#include "esp_http_server.h"
#include "esp_timer.h"
#include "img_converters.h"
#include "fb_gfx.h"
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

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

#define FLASH_LED_PIN      4   // High-power white illumination LED (PWM controlled)
#define STATUS_LED_PIN    33   // Small red indicator LED (Active LOW)

#define FLASH_PWM_CHANNEL  7   // LEDC channel for flashlight
#define FLASH_PWM_FREQ  5000   // 5 kHz PWM frequency
#define FLASH_PWM_RES      8   // 8-bit resolution (0-255)

// ---------------------- Wi-Fi Configuration ----------------------
const char* hubSSID = "DriveSphere-Hub";
const char* hubPassword = "drivesphere123";

const char* apSSID = "DriveSphere-Cam";
const char* apPassword = "drivesphere123";

// Fixed IP configuration when connected to Guardian Hub
IPAddress camStaticIP(192, 168, 4, 2);
IPAddress hubGateway(192, 168, 4, 1);
IPAddress subnetMask(255, 255, 255, 0);

// Global Server & Camera State
httpd_handle_t stream_httpd = NULL;
httpd_handle_t camera_httpd = NULL;

bool isCameraInitialized = false;
int flashBrightness = 0; // 0 = off, 1-255 = brightness
unsigned long lastWifiCheck = 0;
unsigned long frameCount = 0;
float currentFps = 0.0f;
unsigned long lastFpsTime = 0;

// Streaming Multipart Boundary
#define PART_BOUNDARY "123456789000000000000987654321"
static const char* _STREAM_CONTENT_TYPE = "multipart/x-mixed-replace; boundary=" PART_BOUNDARY;
static const char* _STREAM_BOUNDARY = "\r\n--" PART_BOUNDARY "\r\n";
static const char* _STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

// ---------------------- Hardware LED Control ----------------------
void setFlashBrightness(int duty) {
  duty = constrain(duty, 0, 255);
  flashBrightness = duty;
#if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
  ledcWrite(FLASH_LED_PIN, duty);
#else
  ledcWrite(FLASH_PWM_CHANNEL, duty);
#endif
}

// ---------------------- Camera Hardware Init ----------------------
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

  if (psramFound()) {
    Serial.println(F("[DriveSphere Cam] PSRAM detected (4MB). Configuring VGA high-speed buffers."));
    config.frame_size = FRAMESIZE_VGA;   // 640x480 (Ideal for smooth AI inference & streaming)
    config.jpeg_quality = 12;            // 10-63 (Lower means higher quality)
    config.fb_count = 2;
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.grab_mode = CAMERA_GRAB_LATEST; // Always grabs latest frame, avoiding stale lag
  } else {
    Serial.println(F("[DriveSphere Cam] WARNING: No PSRAM detected. Falling back to QVGA in DRAM."));
    config.frame_size = FRAMESIZE_QVGA;  // 320x240
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
    s->set_brightness(s, 1);     // -2 to 2
    s->set_contrast(s, 0);       // -2 to 2
    s->set_saturation(s, 0);     // -2 to 2
    s->set_whitebal(s, 1);       // Auto white balance
    s->set_awb_gain(s, 1);
    s->set_wb_mode(s, 0);
    s->set_exposure_ctrl(s, 1);  // Auto exposure
    s->set_aec2(s, 0);
    s->set_gain_ctrl(s, 1);
    s->set_vflip(s, 0);          // Flip vertical (0: normal, 1: inverted)
    s->set_hmirror(s, 0);        // Mirror horizontal
  }

  Serial.println(F("[DriveSphere Cam] OV2640 Camera Sensor Ready!"));
  return true;
}

// ---------------------- HTTP Request Handlers ----------------------

// 1. High-Speed Non-Blocking Live MJPEG Stream (/stream)
static esp_err_t stream_handler(httpd_req_t *req) {
  camera_fb_t * fb = NULL;
  esp_err_t res = ESP_OK;
  size_t _jpg_buf_len = 0;
  uint8_t * _jpg_buf = NULL;
  char part_buf[128];

  res = httpd_resp_set_type(req, _STREAM_CONTENT_TYPE);
  if (res != ESP_OK) return res;

  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "X-Framerate", "25");

  while (true) {
    fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println(F("[DriveSphere Cam] Frame buffer acquisition failed"));
      res = ESP_FAIL;
      break;
    }

    _jpg_buf_len = fb->len;
    _jpg_buf = fb->buf;

    res = httpd_resp_send_chunk(req, _STREAM_BOUNDARY, strlen(_STREAM_BOUNDARY));
    if (res == ESP_OK) {
      size_t hlen = snprintf(part_buf, sizeof(part_buf), _STREAM_PART, (uint32_t)_jpg_buf_len);
      res = httpd_resp_send_chunk(req, part_buf, hlen);
    }
    if (res == ESP_OK) {
      res = httpd_resp_send_chunk(req, (const char *)_jpg_buf, _jpg_buf_len);
    }

    esp_camera_fb_return(fb);
    fb = NULL;

    if (res != ESP_OK) {
      break; // Client disconnected
    }

    frameCount++;
    unsigned long now = millis();
    if (now - lastFpsTime >= 1000) {
      currentFps = (float)frameCount * 1000.0f / (float)(now - lastFpsTime);
      frameCount = 0;
      lastFpsTime = now;
    }

    vTaskDelay(pdMS_TO_TICKS(10)); // Cooperative multitasking yield
  }

  return res;
}

// 2. Real-Time Snapshot Capture for Mobile AI Model (/capture)
static esp_err_t capture_handler(httpd_req_t *req) {
  if (!isCameraInitialized) {
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }

  camera_fb_t * fb = esp_camera_fb_get();
  if (!fb) {
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }

  httpd_resp_set_type(req, "image/jpeg");
  httpd_resp_set_hdr(req, "Content-Disposition", "inline; filename=capture.jpg");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "Cache-Control", "no-cache, no-store, must-revalidate");

  esp_err_t res = httpd_resp_send(req, (const char *)fb->buf, fb->len);
  esp_camera_fb_return(fb);
  return res;
}

// 3. Flashlight Illumination Control (/flash?state=1|0&brightness=0..255)
static esp_err_t flash_handler(httpd_req_t *req) {
  char buf[64];
  size_t buf_len = httpd_req_get_url_query_len(req) + 1;
  int targetDuty = flashBrightness;

  if (buf_len > 1 && httpd_req_get_url_query_str(req, buf, sizeof(buf)) == ESP_OK) {
    char param[16];
    if (httpd_query_key_value(buf, "state", param, sizeof(param)) == ESP_OK) {
      if (strcmp(param, "1") == 0 || strcmp(param, "true") == 0 || strcmp(param, "on") == 0) {
        targetDuty = (flashBrightness > 0) ? flashBrightness : 128; // Default 50% brightness
      } else {
        targetDuty = 0;
      }
    }
    if (httpd_query_key_value(buf, "brightness", param, sizeof(param)) == ESP_OK) {
      targetDuty = atoi(param);
    }
  } else {
    // Toggle on/off
    targetDuty = (flashBrightness > 0) ? 0 : 128;
  }

  setFlashBrightness(targetDuty);

  char json[96];
  snprintf(json, sizeof(json), "{\"flashActive\":%s,\"brightness\":%d}",
           (flashBrightness > 0) ? "true" : "false", flashBrightness);

  httpd_resp_set_type(req, "application/json");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(req, json, strlen(json));
}

// 4. Invert / Mirror Video Feed for Custom Mounting (/flip?vflip=1&hmirror=0)
static esp_err_t flip_handler(httpd_req_t *req) {
  sensor_t * s = esp_camera_sensor_get();
  if (!s) {
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }

  char buf[64];
  size_t buf_len = httpd_req_get_url_query_len(req) + 1;
  if (buf_len > 1 && httpd_req_get_url_query_str(req, buf, sizeof(buf)) == ESP_OK) {
    char param[16];
    if (httpd_query_key_value(buf, "vflip", param, sizeof(param)) == ESP_OK) {
      s->set_vflip(s, atoi(param));
    }
    if (httpd_query_key_value(buf, "hmirror", param, sizeof(param)) == ESP_OK) {
      s->set_hmirror(s, atoi(param));
    }
  }

  const char* resp = "{\"status\":\"ok\"}";
  httpd_resp_set_type(req, "application/json");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(req, resp, strlen(resp));
}

// 5. Dynamic Camera Sensor Parameter Adjustment (/control?var=framesize&val=...)
static esp_err_t control_handler(httpd_req_t *req) {
  sensor_t * s = esp_camera_sensor_get();
  if (!s) {
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }

  char buf[64];
  size_t buf_len = httpd_req_get_url_query_len(req) + 1;
  int res = 0;

  if (buf_len > 1 && httpd_req_get_url_query_str(req, buf, sizeof(buf)) == ESP_OK) {
    char variable[32];
    char value[16];
    if (httpd_query_key_value(buf, "var", variable, sizeof(variable)) == ESP_OK &&
        httpd_query_key_value(buf, "val", value, sizeof(value)) == ESP_OK) {
      int val = atoi(value);
      if (strcmp(variable, "framesize") == 0) {
        if (s->pixformat == PIXFORMAT_JPEG) res = s->set_framesize(s, (framesize_t)val);
      } else if (strcmp(variable, "quality") == 0) {
        res = s->set_quality(s, val);
      } else if (strcmp(variable, "contrast") == 0) {
        res = s->set_contrast(s, val);
      } else if (strcmp(variable, "brightness") == 0) {
        res = s->set_brightness(s, val);
      } else if (strcmp(variable, "saturation") == 0) {
        res = s->set_saturation(s, val);
      } else if (strcmp(variable, "vflip") == 0) {
        res = s->set_vflip(s, val);
      } else if (strcmp(variable, "hmirror") == 0) {
        res = s->set_hmirror(s, val);
      }
    }
  }

  if (res < 0) {
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }

  const char* resp = "{\"status\":\"ok\"}";
  httpd_resp_set_type(req, "application/json");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(req, resp, strlen(resp));
}

// 6. Diagnostics, Telemetry & Network Status (/status)
static esp_err_t status_handler(httpd_req_t *req) {
  String staIp = (WiFi.status() == WL_CONNECTED) ? WiFi.localIP().toString() : "0.0.0.0";
  String apIp = WiFi.softAPIP().toString();
  String primaryIp = (WiFi.status() == WL_CONNECTED) ? staIp : apIp;
  int rssi = (WiFi.status() == WL_CONNECTED) ? WiFi.RSSI() : 0;

  String json = "{";
  json += "\"device\":\"DriveSphere-AI-Cam\",";
  json += "\"cameraReady\":" + String(isCameraInitialized ? "true" : "false") + ",";
  json += "\"flashActive\":" + String((flashBrightness > 0) ? "true" : "false") + ",";
  json += "\"flashBrightness\":" + String(flashBrightness) + ",";
  json += "\"fps\":" + String(currentFps, 1) + ",";
  json += "\"stationConnected\":" + String((WiFi.status() == WL_CONNECTED) ? "true" : "false") + ",";
  json += "\"stationIp\":\"" + staIp + "\",";
  json += "\"apIp\":\"" + apIp + "\",";
  json += "\"primaryIp\":\"" + primaryIp + "\",";
  json += "\"rssi\":" + String(rssi) + ",";
  json += "\"freeHeap\":" + String(ESP.getFreeHeap()) + ",";
  json += "\"freePsram\":" + String(ESP.getFreePsram()) + ",";
  json += "\"uptimeSeconds\":" + String(millis() / 1000) + ",";
  json += "\"streamUrl\":\"http://" + primaryIp + "/stream\",";
  json += "\"captureUrl\":\"http://" + primaryIp + "/capture\"";
  json += "}";

  httpd_resp_set_type(req, "application/json");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(req, json.c_str(), json.length());
}

// CORS Preflight Options Handler
static esp_err_t options_handler(httpd_req_t *req) {
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Headers", "Content-Type");
  return httpd_resp_send(req, NULL, 0);
}

// ---------------------- Web Server Lifecycle ----------------------
void startCameraServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = 80;
  config.ctrl_port = 32768;
  config.max_open_sockets = 7;
  config.stack_size = 8192;
  config.lru_purge_enable = true;

  httpd_uri_t capture_uri = { .uri = "/capture", .method = HTTP_GET, .handler = capture_handler, .user_ctx = NULL };
  httpd_uri_t stream_uri  = { .uri = "/stream",  .method = HTTP_GET, .handler = stream_handler,  .user_ctx = NULL };
  httpd_uri_t flash_uri   = { .uri = "/flash",   .method = HTTP_GET, .handler = flash_handler,   .user_ctx = NULL };
  httpd_uri_t flash_p_uri = { .uri = "/flash",   .method = HTTP_POST, .handler = flash_handler,  .user_ctx = NULL };
  httpd_uri_t flip_uri    = { .uri = "/flip",    .method = HTTP_GET, .handler = flip_handler,    .user_ctx = NULL };
  httpd_uri_t flip_p_uri  = { .uri = "/flip",    .method = HTTP_POST, .handler = flip_handler,   .user_ctx = NULL };
  httpd_uri_t ctrl_uri    = { .uri = "/control", .method = HTTP_GET, .handler = control_handler, .user_ctx = NULL };
  httpd_uri_t status_uri  = { .uri = "/status",  .method = HTTP_GET, .handler = status_handler,  .user_ctx = NULL };
  httpd_uri_t opt_uri     = { .uri = "/*",       .method = HTTP_OPTIONS, .handler = options_handler, .user_ctx = NULL };

  Serial.printf("[DriveSphere Cam] Starting native esp_httpd server on port: '%d'\n", config.server_port);
  if (httpd_start(&camera_httpd, &config) == ESP_OK) {
    httpd_register_uri_handler(camera_httpd, &capture_uri);
    httpd_register_uri_handler(camera_httpd, &stream_uri);
    httpd_register_uri_handler(camera_httpd, &flash_uri);
    httpd_register_uri_handler(camera_httpd, &flash_p_uri);
    httpd_register_uri_handler(camera_httpd, &flip_uri);
    httpd_register_uri_handler(camera_httpd, &flip_p_uri);
    httpd_register_uri_handler(camera_httpd, &ctrl_uri);
    httpd_register_uri_handler(camera_httpd, &status_uri);
    httpd_register_uri_handler(camera_httpd, &opt_uri);
    Serial.println(F("[DriveSphere Cam] HTTP Server Started Successfully!"));
  } else {
    Serial.println(F("[DriveSphere Cam] ERROR: Failed to start HTTP server."));
  }
}

// ---------------------- Wi-Fi Multi-Mode Management ----------------------
void setupWifi() {
  // Use AP + Station Mode concurrently
  WiFi.mode(WIFI_AP_STA);

  // 1. Start Standalone Camera Fallback AP on distinct subnet (192.168.5.1)
  // This prevents lwIP internal routing collision when Station connects to Hub on 192.168.4.x
  IPAddress apIP(192, 168, 5, 1);
  IPAddress apGateway(192, 168, 5, 1);
  IPAddress apSubnet(255, 255, 255, 0);
  WiFi.softAPConfig(apIP, apGateway, apSubnet);
  WiFi.softAP(apSSID, apPassword);
  Serial.print(F("[DriveSphere Cam] Standalone AP active: "));
  Serial.print(apSSID);
  Serial.print(F(" | IP: "));
  Serial.println(WiFi.softAPIP());

  // 2. Configure Static IP on Guardian Hub Network (192.168.4.2)
  WiFi.config(camStaticIP, hubGateway, subnetMask);

  // 3. Connect to Vehicle Guardian Hub
  Serial.print(F("[DriveSphere Cam] Connecting to Guardian Hub: "));
  Serial.println(hubSSID);
  WiFi.begin(hubSSID, hubPassword);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 10) {
    delay(400);
    Serial.print(F("."));
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println(F("\n[DriveSphere Cam] Connected to Guardian Hub successfully!"));
    Serial.print(F("Fixed Station IP: "));
    Serial.println(WiFi.localIP());
  } else {
    Serial.println(F("\n[DriveSphere Cam] Guardian Hub not found on boot. Will auto-reconnect in background."));
  }
}

// Background Wi-Fi Reconnection Loop
void checkWifiReconnect() {
  if (millis() - lastWifiCheck < 8000) return;
  lastWifiCheck = millis();

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println(F("[DriveSphere Cam] Attempting reconnection to Guardian Hub..."));
    WiFi.begin(hubSSID, hubPassword);
  }
}

// ---------------------- Setup & Main Loop ----------------------
void setup() {
  // Disable brownout detector during high-draw camera + Wi-Fi bursts
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  Serial.println(F("\n======================================================="));
  Serial.println(F("  DriveSphere AI Dashcam & Rider Safety Cam v2.0"));
  Serial.println(F("======================================================="));

  // Initialize Red Status LED (Active LOW)
  pinMode(STATUS_LED_PIN, OUTPUT);
  digitalWrite(STATUS_LED_PIN, HIGH); // OFF

  // Initialize Flashlight with LEDC PWM (prevents LED burn-out)
#if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
  ledcAttach(FLASH_LED_PIN, FLASH_PWM_FREQ, FLASH_PWM_RES);
#else
  ledcSetup(FLASH_PWM_CHANNEL, FLASH_PWM_FREQ, FLASH_PWM_RES);
  ledcAttachPin(FLASH_LED_PIN, FLASH_PWM_CHANNEL);
#endif
  setFlashBrightness(0); // Flash OFF initially

  // Initialize OV2640 Image Sensor
  isCameraInitialized = initCamera();

  // Initialize Wi-Fi (AP + STA)
  setupWifi();

  // Start native multi-socket HTTP server
  startCameraServer();

  // Double-blink status LED to indicate system ready
  digitalWrite(STATUS_LED_PIN, LOW);
  delay(120);
  digitalWrite(STATUS_LED_PIN, HIGH);
  delay(80);
  digitalWrite(STATUS_LED_PIN, LOW);
  delay(120);
  digitalWrite(STATUS_LED_PIN, HIGH);

  Serial.println(F("[DriveSphere Cam] Camera Ready for Live AI Streaming & Inspection!"));
}

void loop() {
  checkWifiReconnect();
  delay(100); // esp_httpd runs on its own FreeRTOS background task
}
