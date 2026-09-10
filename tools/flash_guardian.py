import subprocess
import time
import sys
import serial

port = "COM5"
arduino_cli = r"c:\Users\yashv\dev\work in progress\drivesphere\tools\arduino-cli\arduino-cli.exe"
sketch_dir = r"c:\Users\yashv\dev\work in progress\drivesphere\firmware\esp32_guardian_hub"

print("=" * 65)
print("  DriveSphere Smart Flasher (Monitoring COM5)")
print("=" * 65)

def try_upload():
    print("\n>>> Attempting upload with arduino-cli...")
    cmd = [
        arduino_cli,
        "upload",
        "-p", port,
        "--fqbn", "esp32:esp32:esp32:PartitionScheme=huge_app",
        sketch_dir
    ]
    res = subprocess.run(cmd, capture_output=False)
    return res.returncode == 0

for cycle in range(1, 120):
    print(f"\n[Check #{cycle}] Reading ESP32 boot state on {port}...")
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

        has_crash_loop = any("invalid header" in l or "boot:0xb" in l for l in raw_lines)
        has_download_mode = any("boot:0x13" in l or "waiting for download" in l for l in raw_lines)

        if has_download_mode:
            print(">>> SUCCESS: ESP32 IS IN DOWNLOAD MODE (boot:0x13)!")
            if try_upload():
                print("\n" + "=" * 65)
                print("  SUCCESS! NEW AERO DIGITAL CLUSTER OLED UI FLASHED!")
                print("  Now REMOVE the IO0 wire and press RST button!")
                print("=" * 65)
                sys.exit(0)
        elif has_crash_loop:
            print("  [STATE] ESP32 is in BOOT:0xB (Flash boot) -> IO0 is NOT grounded!")
            print("  [ACTION] Connect Right Pin 3 (IO0) to Right Pin 4 (GND) and press RST!")
        else:
            print("  [STATE] Listening... attempting esptool sync...")
            if try_upload():
                print("\n" + "=" * 65)
                print("  SUCCESS! NEW AERO DIGITAL CLUSTER OLED UI FLASHED!")
                print("  Now REMOVE the IO0 wire and press RST button!")
                print("=" * 65)
                sys.exit(0)

    except serial.SerialException as e:
        print(f"  [WAIT] COM5 busy or disconnected ({e}). Retrying in 1s...")
        time.sleep(1)
    except Exception as e:
        print(f"  Error: {e}")
        time.sleep(1)

print("Timeout waiting for download mode.")
sys.exit(1)
