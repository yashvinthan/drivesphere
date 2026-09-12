import subprocess
import time
import sys
import os
import serial
import serial.tools.list_ports

base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
arduino_cli = os.path.join(base_dir, "tools", "arduino-cli", "arduino-cli.exe")
sketch_dir = os.path.join(base_dir, "firmware", "esp32_guardian_hub")

# 1. Auto-detect active ESP32 COM port
available_ports = [
    p.device for p in serial.tools.list_ports.comports()
    if any(k in (p.description + p.manufacturer) for k in ["CH340", "CP210", "USB", "Serial", "FTDI"])
]
port = available_ports[0] if available_ports else "COM4"

print("=" * 65)
print(f"  DriveSphere Fully Automated ESP32 Flasher")
print(f"  Target Port: {port} | Sketch: esp32_guardian_hub")
print("=" * 65)

# 2. Pre-compile sketch
print("\n>>> [Step 1/3] Pre-compiling firmware...")
compile_cmd = [
    arduino_cli,
    "compile",
    "--fqbn", "esp32:esp32:esp32:PartitionScheme=huge_app",
    sketch_dir
]
c_res = subprocess.run(compile_cmd, capture_output=False)
if c_res.returncode != 0:
    print(">>> Compilation failed! Check errors above.")
    sys.exit(1)
print(">>> Compilation SUCCESS!\n")

# 3. Flashing loop
upload_cmd = [
    arduino_cli,
    "upload",
    "-p", port,
    "--fqbn", "esp32:esp32:esp32:PartitionScheme=huge_app",
    sketch_dir
]

print(">>> [Step 2/3] Uploading firmware to ESP32...")
print(">>> Note: If your ESP32 board does not auto-reset, hold the BOOT button now!")

max_attempts = 20
success = False

for attempt in range(1, max_attempts + 1):
    # Try software pulse reset on DTR/RTS
    try:
        s = serial.Serial(port, 115200)
        s.setDTR(False)
        s.setRTS(True)
        time.sleep(0.1)
        s.setDTR(True)
        s.setRTS(False)
        time.sleep(0.2)
        s.setDTR(False)
        s.close()
    except Exception:
        pass

    print(f"\n[Attempt {attempt}/{max_attempts}] Connecting to ESP32 on {port}...")
    res = subprocess.run(upload_cmd)
    if res.returncode == 0:
        success = True
        break
    time.sleep(1.5)

if success:
    print("\n" + "=" * 65)
    print("  SUCCESS! FIRMWARE FLASHED AND VERIFIED AUTOMATICALLY!")
    print("  You can release the BOOT button if you were holding it.")
    print("=" * 65)
    
    # 4. Verify boot output
    print("\n>>> [Step 3/3] Reading boot confirmation from ESP32...")
    time.sleep(1.0)
    try:
        s = serial.Serial(port, 115200, timeout=1.5)
        time.sleep(0.3)
        for _ in range(8):
            line = s.readline().decode('latin1', errors='replace').strip()
            if line:
                print(f"  [ESP32] {line}")
        s.close()
    except Exception as e:
        print(f"  (Serial monitor note: {e})")
    sys.exit(0)
else:
    print("\n>>> Timed out connecting to ESP32.")
    print(">>> Please hold the BOOT button while running the script.")
    sys.exit(1)
