import os
import sys
import urllib.request
import zipfile
import subprocess
import shutil
from pathlib import Path

BASE_DIR = Path("c:/Users/yashv/dev/drivesphere").resolve()
TOOLS_DIR = BASE_DIR / "tools"
SDK_DIR = Path("C:/Users/yashv/android-sdk").resolve()
GRADLE_DIR = TOOLS_DIR / "gradle-9.3.1"
JAVA_HOME = Path("C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot")

def log(msg):
    print(f"[BUILD-APK] {msg}", flush=True)

def download_file(url, target_path):
    if target_path.exists():
        log(f"File already exists: {target_path}")
        return
    log(f"Downloading {url} -> {target_path}...")
    target_path.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(url, str(target_path))
    log(f"Downloaded: {target_path.stat().st_size} bytes")

def setup_gradle():
    gradle_bin = GRADLE_DIR / "bin" / "gradle.bat"
    if gradle_bin.exists():
        log("Gradle 9.3.1 already installed.")
        return gradle_bin

    zip_path = TOOLS_DIR / "gradle-9.3.1-bin.zip"
    url = "https://services.gradle.org/distributions/gradle-9.3.1-bin.zip"
    download_file(url, zip_path)
    
    log("Extracting Gradle...")
    with zipfile.ZipFile(zip_path, 'r') as zf:
        zf.extractall(TOOLS_DIR)
    
    log(f"Gradle ready at: {gradle_bin}")
    return gradle_bin

def setup_android_sdk():
    cmdline_zip = TOOLS_DIR / "commandlinetools-win.zip"
    url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    download_file(url, cmdline_zip)
    
    latest_dir = SDK_DIR / "cmdline-tools" / "latest"
    sdkmanager_bat = latest_dir / "bin" / "sdkmanager.bat"
    
    if not sdkmanager_bat.exists():
        log("Extracting Android command-line tools...")
        temp_extract = TOOLS_DIR / "cmdline-temp"
        if temp_extract.exists():
            shutil.rmtree(temp_extract)
        with zipfile.ZipFile(cmdline_zip, 'r') as zf:
            zf.extractall(temp_extract)
        
        latest_dir.parent.mkdir(parents=True, exist_ok=True)
        # The zip contains a 'cmdline-tools' folder
        src_cmdline = temp_extract / "cmdline-tools"
        if latest_dir.exists():
            shutil.rmtree(latest_dir)
        shutil.move(str(src_cmdline), str(latest_dir))
        shutil.rmtree(temp_extract, ignore_errors=True)
        log("Android command-line tools extracted to 'latest'.")

    # Accept licenses
    licenses_dir = SDK_DIR / "licenses"
    licenses_dir.mkdir(parents=True, exist_ok=True)
    sdk_lic = licenses_dir / "android-sdk-license"
    sdk_preview_lic = licenses_dir / "android-sdk-preview-license"
    
    hashes = (
        "24333f8a63068f51de1e527a1876e9330e7608d6\n"
        "84831b9409646a918e30573bab4c9c91346d8abd\n"
        "d56f5187479451eabf01fb78af6dfcb131a6481e\n"
    )
    sdk_lic.write_text(hashes, encoding="utf-8")
    sdk_preview_lic.write_text("84831b9409646a918e30573bab4c9c91346d8abd\n", encoding="utf-8")
    log("Android licenses written.")

    # Write local.properties
    local_props = BASE_DIR / "local.properties"
    clean_sdk_path = str(SDK_DIR).replace("\\", "\\\\")
    local_props.write_text(f"sdk.dir={clean_sdk_path}\n", encoding="utf-8")
    log(f"Wrote {local_props}")

    return sdkmanager_bat

def install_sdk_components(sdkmanager_bat):
    env = os.environ.copy()
    env["JAVA_HOME"] = str(JAVA_HOME)
    env["ANDROID_HOME"] = str(SDK_DIR)
    env["PATH"] = f"{JAVA_HOME}/bin;{env.get('PATH', '')}"

    packages = [
        "platform-tools",
        "platforms;android-35",
        "platforms;android-36",
        "build-tools;35.0.0",
        "build-tools;36.0.0"
    ]
    log(f"Installing SDK packages: {packages}...")
    cmd = [str(sdkmanager_bat), f"--sdk_root={SDK_DIR}"] + packages
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env)
    stdout, _ = p.communicate(input="y\ny\ny\ny\ny\n")
    log(f"SDKManager returned {p.returncode}. Output:\n{stdout[-500:] if len(stdout)>500 else stdout}")

def build_debug_apk(gradle_bat, max_retries=6):
    env = os.environ.copy()
    env["JAVA_HOME"] = str(JAVA_HOME)
    env["ANDROID_HOME"] = str(SDK_DIR)
    env["PATH"] = f"{JAVA_HOME}/bin;{env.get('PATH', '')}"

    for attempt in range(1, max_retries + 1):
        log(f"Running Gradle assembleDebug (Attempt {attempt}/{max_retries})...")
        cmd = [str(gradle_bat), "assembleDebug", "--stacktrace"]
        p = subprocess.Popen(cmd, cwd=str(BASE_DIR), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env)
        
        for line in iter(p.stdout.readline, ''):
            line = line.strip()
            if line:
                print(f"[GRADLE] {line}", flush=True)
        p.stdout.close()
        p.wait()
        log(f"Gradle finished with return code {p.returncode}")
        if p.returncode == 0:
            return True
        import time
        time.sleep(3)
    return False

def copy_and_verify_apk():
    candidates = list(BASE_DIR.glob("app/build/outputs/apk/debug/*.apk"))
    if not candidates:
        log("ERROR: No APK found in app/build/outputs/apk/debug/!")
        return False

    apk_file = candidates[0]
    file_size = apk_file.stat().st_size
    log(f"Built APK: {apk_file} (Size: {file_size} bytes / {file_size / (1024*1024):.2f} MB)")

    if file_size < 1024 * 1024:
        log(f"ERROR: APK is smaller than 1MB ({file_size} bytes)!")
        return False

    # Verify APK validity with zipfile
    try:
        with zipfile.ZipFile(apk_file, 'r') as zf:
            namelist = zf.namelist()
            if "AndroidManifest.xml" not in namelist or "classes.dex" not in namelist:
                log("ERROR: APK is missing AndroidManifest.xml or classes.dex!")
                return False
            log(f"APK integrity verified! Contains {len(namelist)} items including AndroidManifest.xml and classes.dex.")
    except Exception as e:
        log(f"ERROR reading APK zip: {e}")
        return False

    # Place 1: .build-outputs/app-debug.apk
    out1_dir = BASE_DIR / ".build-outputs"
    out1_dir.mkdir(parents=True, exist_ok=True)
    out1_apk = out1_dir / "app-debug.apk"
    shutil.copy2(str(apk_file), str(out1_apk))
    log(f"Copied to: {out1_apk} ({out1_apk.stat().st_size} bytes)")

    # Place 2: APK_DOWNLOAD/app-debug.apk
    out2_dir = BASE_DIR / "APK_DOWNLOAD"
    out2_dir.mkdir(parents=True, exist_ok=True)
    out2_apk = out2_dir / "app-debug.apk"
    shutil.copy2(str(apk_file), str(out2_apk))
    log(f"Copied to: {out2_apk} ({out2_apk.stat().st_size} bytes)")

    log("ALL APK COPIES AND VERIFICATIONS SUCCESSFUL!")
    return True

def main():
    gradle_bat = setup_gradle()
    sdkmanager_bat = setup_android_sdk()
    install_sdk_components(sdkmanager_bat)
    success = build_debug_apk(gradle_bat)
    if success:
        copy_and_verify_apk()
    else:
        log("Gradle build failed, inspecting...")

if __name__ == "__main__":
    main()
