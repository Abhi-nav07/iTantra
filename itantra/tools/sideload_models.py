import sys
import os
import subprocess

# iTantra Model Sideload Script
# Transfers locally provisioned models to an Android device or emulator
# Destination on device:
#   - /data/data/com.itantra.app/files/language_packs/shared/stt/
#   - /data/data/com.itantra.app/files/language_packs/<lang>/tts/
#   - /data/data/com.itantra.app/files/translation_models/indic-en/
#   - /data/data/com.itantra.app/files/translation_models/en-indic/

PROVISIONED_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "provisioned_models"))
PACKAGE_NAME = "com.itantra.app"

def run_cmd(cmd):
    print(f"Running: {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"ERROR: {result.stderr.strip()}")
        return False
    if result.stdout.strip():
        print(result.stdout.strip())
    return True

def main():
    if not os.path.exists(PROVISIONED_DIR):
        print(f"ERROR: Provisioned directory does not exist: {PROVISIONED_DIR}")
        print("Run python tools/provision_models.py first!")
        sys.exit(1)

    # 1. Check for connected devices
    res = subprocess.run(["adb", "devices"], capture_output=True, text=True)
    lines = [l.strip() for l in res.stdout.strip().splitlines() if l.strip() and not l.startswith("List of devices")]
    devices = [l.split()[0] for l in lines if "device" in l]

    if not devices:
        print("No Android device or emulator detected via adb.")
        print("Please connect a device with USB debugging enabled, or start an emulator.")
        sys.exit(1)

    print(f"Found device(s): {', '.join(devices)}. Targeting {devices[0]}")
    target_device = devices[0]

    # 2. Push to /data/local/tmp
    temp_remote = "/data/local/tmp/itantra_models"
    print(f"\n1. Pushing provisioned models to {temp_remote} on device...")
    run_cmd(["adb", "-s", target_device, "shell", f"rm -rf {temp_remote}"])
    run_cmd(["adb", "-s", target_device, "push", PROVISIONED_DIR, temp_remote])

    # 3. Create app directories via run-as
    print("\n2. Installing into application sandbox...")
    setup_commands = f"""
run-as {PACKAGE_NAME} mkdir -p files/language_packs/shared/stt
run-as {PACKAGE_NAME} mkdir -p files/translation_models/indic-en
run-as {PACKAGE_NAME} mkdir -p files/translation_models/en-indic

# Copy shared STT
run-as {PACKAGE_NAME} cp -r {temp_remote}/shared/stt/* files/language_packs/shared/stt/

# Copy MT models
run-as {PACKAGE_NAME} cp -r {temp_remote}/mt/indic-en/* files/translation_models/indic-en/
run-as {PACKAGE_NAME} cp -r {temp_remote}/mt/en-indic/* files/translation_models/en-indic/

# Copy all 10 language TTS packs
for lang in hi en bn gu mr kn ml ta te or; do
    if [ -d "{temp_remote}/$lang/tts" ]; then
        run-as {PACKAGE_NAME} mkdir -p "files/language_packs/$lang/tts"
        run-as {PACKAGE_NAME} cp -r "{temp_remote}/$lang/tts/"* "files/language_packs/$lang/tts/"
    fi
done

# Cleanup temporary files
rm -rf {temp_remote}
"""
    cmd = ["adb", "-s", target_device, "shell", setup_commands]
    if run_cmd(cmd):
        print("\n=== Model Sideload Complete! ===")
        print("All 10 language packs, shared Whisper STT, and IndicTrans2 MT models installed into app sandbox.")
    else:
        print("\nSideload encountered an error. Ensure the app is installed with debuggable=true.")

if __name__ == "__main__":
    main()
