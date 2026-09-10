/*
 * DriveSphere — NEO-M8N GPS Hardware Passthrough Test
 * Connect NEO-M8N TX to ESP32-CAM GPIO 16 (U2RXD)
 * Connect NEO-M8N VCC to 5V and GND to GND
 */

#define GPS_RX_PIN 16
#define GPS_TX_PIN 17
#define GPS_BAUD   9600

HardwareSerial SerialGPS(2);
unsigned long byteCount = 0;
unsigned long lastLog = 0;

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println(F("\n======================================================="));
  Serial.println(F("  DriveSphere — NEO-M8N GPS Live NMEA Stream Test"));
  Serial.println(F("  Listening on Hardware UART2 (RX: GPIO 16) @ 9600 baud"));
  Serial.println(F("=======================================================\n"));

  SerialGPS.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
}

void loop() {
  while (SerialGPS.available() > 0) {
    char c = SerialGPS.read();
    Serial.write(c); // Stream raw NMEA sentences directly to PC USB
    byteCount++;
  }

  if (millis() - lastLog > 3000) {
    lastLog = millis();
    if (byteCount == 0) {
      Serial.println(F("\n[STATUS: WAITING] No bytes on GPIO 16 yet. Check:"));
      Serial.println(F("  1. NEO-M8N TX -> ESP32-CAM GPIO 16 (U2RXD)"));
      Serial.println(F("  2. NEO-M8N VCC -> 5V (Power LED should be glowing)"));
      Serial.println(F("  3. NEO-M8N GND -> GND\n"));
    }
  }
}
