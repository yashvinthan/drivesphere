import serial
import time
import sys

port = "COM5"
baud = 115200

print("="*65)
print("  DriveSphere — NEO-M8N GPS Hardware Diagnostic Tool")
print("  Listening on " + port + " @ " + str(baud) + " baud...")
print("="*65)

try:
    ser = serial.Serial(port, baud, timeout=2)
except Exception as e:
    print(f"\n[ERROR] Cannot open {port}: {e}")
    print("Ensure the board is plugged in and no other serial monitor is open.")
    sys.exit(1)

# Discard old buffer
ser.reset_input_buffer()

print("\nReading live telemetry from ESP32...")
print("-----------------------------------------------------------------")

start_time = time.time()
found_gps = False

try:
    while time.time() - start_time < 15:
        line = ser.readline().decode('utf-8', errors='replace').strip()
        if not line:
            continue
        print("  " + line)
        if "[GPS CHECK]" in line:
            found_gps = True

    if not found_gps:
        print("\n-----------------------------------------------------------------")
        print("[DIAGNOSTIC SUMMARY]")
        print("No '[GPS CHECK]' messages were received.")
        print("1. Did you disconnect GPIO 0 from GND after flashing?")
        print("2. Did you press the RST button on the ESP32 to start execution?")
        print("3. Check that NEO-M8N TX is wired to ESP32 GPIO 16 (U2RXD).")
    else:
        print("\n-----------------------------------------------------------------")
        print("[DIAGNOSTIC SUMMARY]")
        print("GPS Telemetry stream is ACTIVE.")
        print("- If 'Chars: > 0' & 'Fix: SEARCHING': The ESP32 is successfully receiving")
        print("  NMEA data from NEO-M8N. Place the ceramic antenna near a window or")
        print("  outdoors for 1-3 minutes to acquire full 3D satellite lock.")
        print("- If 'Chars: 0': Check that NEO-M8N TX is connected to GPIO 16 and")
        print("  the module's power LED is glowing.")

except KeyboardInterrupt:
    print("\nStopped.")
finally:
    ser.close()
