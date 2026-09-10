import subprocess
import time
import sys

arduino_cli = r"c:\Users\yashv\dev\work in progress\drivesphere\tools\arduino-cli\arduino-cli.exe"
sketch_dir = r"c:\Users\yashv\dev\work in progress\drivesphere\firmware\esp32_guardian_hub"
port = "COM5"

print("=================================================================")
print("  DriveSphere Live Flasher: Waiting for ESP32 on COM5...")
print("  (Firmware: OLED & MPU6050 on IO14/IO15, GPS on IO16/IO17, Button on IO13)")
print("=================================================================")

cmd = [
    arduino_cli,
    "upload",
    "-p", port,
    "--fqbn", "esp32:esp32:esp32:PartitionScheme=huge_app",
    sketch_dir
]

for attempt in range(1, 100):
    print(f"\n[Attempt {attempt}/100] Listening for ESP32 on {port}...")
    res = subprocess.run(cmd, capture_output=False)
    if res.returncode == 0:
        print("\n" + "="*60)
        print("  SUCCESS! FIRMWARE FLASHED AND VERIFIED!")
        print("  OLED Display:   IO15 (SDA) & IO14 (SCL)  [Bus 0]")
        print("  MPU-6050 IMU:   IO13 (SDA) & IO2 (SCL)   [Bus 1 - Dedicated]")
        print("  NEO-M8N GPS:    IO16 (U2RXD)")
        print("  Push Button:    IO12 (Internal Pullup)")
        print("="*60)
        sys.exit(0)
    time.sleep(1)

print("Timeout waiting for ESP32.")
sys.exit(1)
