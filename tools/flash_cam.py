import subprocess
import time
import sys
import os
import serial
import serial.tools.list_ports

# Dynamically resolve directory paths
base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
arduino_cli = os.path.join(base_dir, "tools", "arduino-cli", "arduino-cli.exe")
sketch_dir = os.path.join(base_dir, "firmware", "esp32_cam")

# Check CLI argument or auto-detect port
if len(sys.argv) > 1 and sys.argv[1].startswith("COM"):
    port = sys.argv[1]
else:
    available_ports = [
        p.device for p in serial.tools.list_ports.comports()
        if any(k in ((p.description or "") + (p.manufacturer or "")) for k in ["CH340", "CP210", "USB", "Serial", "FTDI"])
    ]
    if len(available_ports) != 1:
        print("ERROR: ESP32-CAM port was not uniquely detected.")
        print("Connect the ESP32-CAM USB-to-UART adapter and run: python tools/flash_cam.py COMx")
        print(f"Detected ports: {available_ports or 'none'}")
        sys.exit(2)
    port = available_ports[0]

print("=" * 65)
print("  DriveSphere ESP32-CAM AI Dashcam Flasher")
print(f"  Target Port: {port} | Sketch: esp32_cam")
print("=" * 65)

def try_upload():
    print("\n>>> Attempting upload with arduino-cli...")
    cmd = [
        arduino_cli,
        "upload",
        "-p", port,
        "--fqbn", "esp32:esp32:esp32cam:PartitionScheme=huge_app",
        sketch_dir
    ]
    res = subprocess.run(cmd, capture_output=False)
    return res.returncode == 0

for cycle in range(1, 60):
    print(f"\n[Check #{cycle}] Checking ESP32-CAM boot state on {port}...")
    try:
        s = serial.Serial(port, 115200, timeout=1)
        time.sleep(0.3)
        raw_lines = []
        start = time.time()
        while time.time() - start < 1.2:
            if s.in_waiting:
                line = s.readline().decode('latin1', errors='replace').strip()
                if line:
                    raw_lines.append(line)
            time.sleep(0.02)
        s.close()

        if any("DriveSphere-Guardian-Hub" in line or "GUARDIAN HUB" in line.upper() for line in raw_lines):
            print(f"ERROR: {port} identifies as the Guardian Hub. Refusing to flash it.")
            sys.exit(2)

        has_download_mode = any("boot:0x13" in l or "waiting for download" in l for l in raw_lines)

        if has_download_mode:
            print(">>> SUCCESS: ESP32-CAM IS IN DOWNLOAD MODE (boot:0x13)!")
            if try_upload():
                print("\n" + "=" * 65)
                print("  SUCCESS! ESP32-CAM FIRMWARE FLASHED!")
                print("  Now REMOVE the GPIO 0 to GND jumper and press RESET button!")
                print("=" * 65)
                sys.exit(0)
        else:
            print("  [STATE] ESP32-CAM not in download mode.")
            print("  [ACTION] Connect GPIO 0 to GND and press RESET button!")

    except serial.SerialException as e:
        print(f"  [WAIT] {port} busy or disconnected ({e}). Retrying in 1s...")
        time.sleep(1)
    except Exception as e:
        print(f"  Error: {e}")
        time.sleep(1)

print("\nTimeout waiting for download mode. Please ensure GPIO 0 is grounded and retry.")
sys.exit(1)
