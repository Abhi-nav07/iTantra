import os
import shutil
import tempfile
import hashlib
from huggingface_hub import hf_hub_download

TARGET_DIR = os.path.join(os.path.dirname(__file__), "..", "translation_models")
os.makedirs(TARGET_DIR, exist_ok=True)

models_to_download = [
    # 1. MT (IndicTrans2)
    # BLOCKED: No verified bare ONNX export of IndicTrans2 works directly with standard ORT without custom KV caching loops and tokenizers.

    # 2. Hindi & English STT (Multilingual Whisper Tiny via Sherpa-ONNX)
    {"repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-decoder.int8.onnx"},
    {"repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-encoder.int8.onnx"},
    {"repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-tokens.txt"},
    
    # 3. Hindi TTS (VITS Piper Pratham Medium)
    {"repo_id": "csukuangfj/vits-piper-hi_IN-pratham-medium", "filename": "hi_IN-pratham-medium.onnx"},
    {"repo_id": "csukuangfj/vits-piper-hi_IN-pratham-medium", "filename": "hi_IN-pratham-medium.onnx.json"},
    {"repo_id": "csukuangfj/vits-piper-hi_IN-pratham-medium", "filename": "tokens.txt"},

    # 4. English TTS (VITS Piper Amy Medium)
    {"repo_id": "csukuangfj/vits-piper-en_US-amy-medium", "filename": "en_US-amy-medium.onnx"},
    {"repo_id": "csukuangfj/vits-piper-en_US-amy-medium", "filename": "en_US-amy-medium.onnx.json"},
    {"repo_id": "csukuangfj/vits-piper-en_US-amy-medium", "filename": "tokens.txt"},
]

print("=== Starting Real Golden Path Model Provisioning ===")
with tempfile.TemporaryDirectory() as tmpdir:
    for model in models_to_download:
        print(f"Downloading {model['filename']} from {model['repo_id']}...")
        try:
            # Temporary download
            tmp_path = hf_hub_download(repo_id=model["repo_id"], filename=model["filename"], local_dir=tmpdir)
            
            # Atomic install
            dest_path = os.path.join(TARGET_DIR, model["filename"])
            shutil.copy2(tmp_path, dest_path)
            print(f"Successfully installed to {dest_path}")
        except Exception as e:
            print(f"Failed to download {model['filename']}: {e}")
            print("CRITICAL: Partial install prevented. Halting.")
            break

print("=== Provisioning Complete ===")
print(f"adb push {TARGET_DIR} /data/user/0/com.itantra.app/files/")
