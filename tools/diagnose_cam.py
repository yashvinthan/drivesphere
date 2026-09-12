import sys
import os
import time
import subprocess
import serial
import serial.tools.list_ports
import urllib.request
import json

base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
arduino_cli = os.path.join(base_dir, "tools", "arduino-cli", "arduino-cli.exe")
sketch_dir = os.path.join(base_dir, "scratch", "cam_test_sketch")

print("=" * 65)
print("  DriveSphere Hardware Diagnostic: ESP32-CAM Sensor & Link Check")
print("=" * 65)

# Step 1: Detect all active serial COM ports
ports = list(serial.tools.list_ports.comports())
print(f"\n[1] Scanning Connected USB Serial Devices...")
if not ports:
    print("  >> No COM ports detected. Please ensure your ESP32 or ESP32-CAM is connected via USB.")
else:
    for p in ports:
        print(f"  -> {p.device}: {p.description} [{p.hwid}]")

# Step 2: Check current device on COM4 (or first available)
target_port = ports[0].device if ports else "COM4"
print(f"\n[2] Interrogating device on {target_port}...")

try:
    s = serial.Serial(target_port, 115200, timeout=1)
    time.sleep(0.3)
    s.write(b'STATUS\n')
    time.sleep(0.5)
    lines = []
    t_end = time.time() + 1.5
    while time.time() < t_end:
        l = s.readline().decode('utf-8', errors='ignore').strip()
        if l:
            lines.append(l)
    s.close()

    is_guardian_hub = any("DriveSphere-Guardian-Hub" in l for l in lines)
    if is_guardian_hub:
        print(f"  >> Detected: DRIVE-SPHERE GUARDIAN HUB (OLED/IMU/GPS Unit)")
        for l in lines:
            if "{" in l:
                try:
                    data = json.loads(l)
                    stations = data.get("stationsConnected", 0)
                    print(f"  >> Hub Wi-Fi SoftAP is active at 192.168.4.1")
                    print(f"  >> Connected Stations to Hub AP: {stations}")
                    if stations > 0:
                        print("  >> NOTE: 1 or more devices are joined to the Hub Wi-Fi!")
                    else:
                        print("  >> NOTE: 0 devices joined to the Hub Wi-Fi. The ESP32-CAM has not connected to DriveSphere-Hub yet.")
                except Exception:
                    pass
    else:
        print(f"  >> Output from {target_port}:")
        for l in lines[:5]:
            print(f"     {l}")
except Exception as e:
    print(f"  >> Could not read {target_port}: {e}")

# Step 3: Check network endpoints if accessible
print(f"\n[3] Testing Network Endpoints for ESP32-CAM...")
test_ips = ["192.168.4.2", "192.168.4.1", "192.168.43.2", "192.168.43.3", "192.168.5.1"]
found_cam = False

for ip in test_ips:
    try:
        req = urllib.request.Request(f"http://{ip}/status", headers={"User-Agent": "DriveSphere-Diag"})
        with urllib.request.urlopen(req, timeout=1.0) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
            print(f"  [SUCCESS] Endpoint http://{ip}/status responded!")
            print(f"            {content[:120]}")
            found_cam = True
            break
    except Exception:
        pass

if not found_cam:
    print("  >> No ESP32-CAM responded on standard IP addresses from this PC.")

print("\n" + "=" * 65)
print("  DIAGNOSTIC SUMMARY & NEXT STEPS")
print("=" * 65)
print("""
To test the ESP32-CAM hardware sensor directly:
1. Connect your ESP32-CAM to your PC via USB (using FTDI adapter or ESP32-CAM-MB baseboard).
2. Connect GPIO 0 to GND to put it in bootloader mode.
3. Run: python tools/flash_cam.py
4. Remove the GPIO 0 to GND jumper and press the RESET button.
5. The serial output will display:
   >> Sensor PID: 0x26 (Omnivision OV2640 2.0 MP detected)
   >> Frame size: ~25,000 bytes JPEG captured
   >> Wi-Fi IP address
""")
