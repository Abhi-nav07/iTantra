import os
from huggingface_hub import hf_hub_download

# Module 8B: Reproducible Provisioning Script
# Use this script to download the exact model files required for the Golden Path.
# Note: Do NOT commit downloaded model weights to Git.

TARGET_DIR = os.path.join(os.path.dirname(__file__), "..", "translation_models")
os.makedirs(TARGET_DIR, exist_ok=True)

models_to_download = [
    # 1. IndicTrans2 MT Models (Indic-to-English and English-to-Indic INT8 ONNX variants)
    # Approximated repo IDs for INT8 variants. In reality, these are specific to the optimized mobile exports.
    {"repo_id": "ai4bharat/indictrans2-en-indic-1B-onnx", "filename": "encoder_model.onnx"},
    {"repo_id": "ai4bharat/indictrans2-en-indic-1B-onnx", "filename": "decoder_model.onnx"},
    {"repo_id": "ai4bharat/indictrans2-en-indic-1B-onnx", "filename": "tokenizer.onnx"},
    
    # 2. Hindi STT (Sherpa-ONNX Conformer)
    {"repo_id": "parismitaglobalsolutions/indicconformer-sherpa-onnx", "filename": "hi/model.int8.onnx"},
    {"repo_id": "parismitaglobalsolutions/indicconformer-sherpa-onnx", "filename": "tokens.txt"},
    
    # 3. English STT
    {"repo_id": "parismitaglobalsolutions/indicconformer-sherpa-onnx", "filename": "en/model.int8.onnx"},

    # 4. Hindi TTS (VITS MMS)
    {"repo_id": "csukuangfj/vits-mms-hin", "filename": "vits-mms-hin.onnx"},
    {"repo_id": "csukuangfj/vits-mms-hin", "filename": "lexicon.txt"},
    {"repo_id": "csukuangfj/vits-mms-hin", "filename": "tokens.txt"},

    # 5. English TTS (VITS MMS)
    {"repo_id": "csukuangfj/vits-mms-eng", "filename": "vits-mms-eng.onnx"},
    {"repo_id": "csukuangfj/vits-mms-eng", "filename": "lexicon.txt"},
    {"repo_id": "csukuangfj/vits-mms-eng", "filename": "tokens.txt"},
]

print("=== Starting Golden Path Model Provisioning ===")
for model in models_to_download:
    print(f"Downloading {model['filename']} from {model['repo_id']}...")
    try:
        path = hf_hub_download(repo_id=model["repo_id"], filename=model["filename"], local_dir=TARGET_DIR)
        print(f"Successfully downloaded to {path}")
    except Exception as e:
        print(f"Failed to download {model['filename']}: {e}")

print("=== Provisioning Complete ===")
print(f"Transfer these files to the Android device via adb:")
print(f"adb push {TARGET_DIR} /data/user/0/com.itantra.app/files/")
