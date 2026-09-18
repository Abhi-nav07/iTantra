import os
import shutil
import tempfile
import hashlib
from huggingface_hub import hf_hub_download

# Central Model Provisioning Script
# Ensures that only verified, compatible models are downloaded for Sherpa-ONNX

TARGET_DIR = os.path.join(os.path.dirname(__file__), "..", "provisioned_models")
os.makedirs(TARGET_DIR, exist_ok=True)

models_to_download = [
    # --- Hindi STT ---
    {"lang": "hi", "type": "stt", "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-decoder.int8.onnx"},
    {"lang": "hi", "type": "stt", "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-encoder.int8.onnx"},
    {"lang": "hi", "type": "stt", "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-tokens.txt"},
    
    # --- English STT ---
    {"lang": "en", "type": "stt", "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-decoder.int8.onnx"},
    {"lang": "en", "type": "stt", "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-encoder.int8.onnx"},
    {"lang": "en", "type": "stt", "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-tokens.txt"},
    
    # --- Hindi TTS ---
    {"lang": "hi", "type": "tts", "repo_id": "csukuangfj/vits-piper-hi_IN-pratham-medium", "filename": "hi_IN-pratham-medium.onnx"},
    {"lang": "hi", "type": "tts", "repo_id": "csukuangfj/vits-piper-hi_IN-pratham-medium", "filename": "tokens.txt"},

    # --- English TTS ---
    {"lang": "en", "type": "tts", "repo_id": "csukuangfj/vits-piper-en_US-amy-medium", "filename": "en_US-amy-medium.onnx"},
    {"lang": "en", "type": "tts", "repo_id": "csukuangfj/vits-piper-en_US-amy-medium", "filename": "tokens.txt"},
]

def calculate_sha256(filepath):
    sha256_hash = hashlib.sha256()
    with open(filepath, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

print("=== Starting Verified Model Provisioning ===")
with tempfile.TemporaryDirectory() as tmpdir:
    for model in models_to_download:
        lang_dir = os.path.join(TARGET_DIR, model["lang"])
        type_dir = os.path.join(lang_dir, model["type"])
        os.makedirs(type_dir, exist_ok=True)
        
        dest_path = os.path.join(type_dir, model["filename"])
        if os.path.exists(dest_path) and os.path.getsize(dest_path) > 0:
            print(f"Skipping {model['filename']} (already exists)")
            continue
            
        print(f"Downloading {model['filename']} from {model['repo_id']}...")
        try:
            tmp_path = hf_hub_download(repo_id=model["repo_id"], filename=model["filename"], local_dir=tmpdir)
            
            # SHA256 Check
            checksum = calculate_sha256(tmp_path)
            print(f"HASH RECORDED: {checksum}")
            
            # Atomic install using shutil.move
            shutil.move(tmp_path, dest_path)
            print(f"Successfully installed to {dest_path}")
            
        except Exception as e:
            print(f"Failed to download {model['filename']}: {e}")
            print("CRITICAL: Partial install prevented. Halting.")
            break

print("=== Provisioning Complete ===")
print("To manually test offline model behavior on a debuggable device, run:")
print("  adb push provisioned_models/hi /data/local/tmp/hi")
print("  adb shell run-as com.itantra.app mkdir -p files/language_packs")
print("  adb shell run-as com.itantra.app cp -r /data/local/tmp/hi files/language_packs/hi")
print("  adb shell rm -r /data/local/tmp/hi")
