import subprocess
import time
import sys

arduino_cli = r"c:\Users\yashv\dev\work in progress\drivesphere\tools\arduino-cli\arduino-cli.exe"
sketch_dir = r"c:\Users\yashv\dev\work in progress\drivesphere\firmware\esp32_guardian_hub"
port = "COM5"

print("="*60)
print("  DriveSphere ESP32 Flasher (Pins: OLED 14/15, Button 13)")
print("  PLEASE HOLD DOWN THE 'BOOT' BUTTON ON YOUR ESP32 NOW!")
print("="*60)

cmd = [
    arduino_cli,
    "upload",
    "-p", port,
    "--fqbn", "esp32:esp32:esp32",
    sketch_dir
]

max_attempts = 4
for attempt in range(1, max_attempts + 1):
    print(f"\n[Attempt {attempt}/{max_attempts}] Trying to connect... (Keep holding BOOT button)")
    res = subprocess.run(cmd)
    if res.returncode == 0:
        print("\n" + "="*60)
        print("  SUCCESS! FIRMWARE FLASHED AND VERIFIED!")
        print("  You can now release the BOOT button.")
        print("="*60)
        sys.exit(0)
    print(f"[Attempt {attempt}] Failed. Retrying in 2 seconds...")
    time.sleep(2)

print("\nCould not connect after several attempts. Please ensure the BOOT button is held while connecting.")
sys.exit(1)
