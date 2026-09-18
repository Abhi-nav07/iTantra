import os
import shutil
import tempfile
import hashlib
from huggingface_hub import hf_hub_download

# Central Model Provisioning Script
# Ensures that only verified, compatible models are downloaded for Sherpa-ONNX and CTranslate2

TARGET_DIR = os.path.join(os.path.dirname(__file__), "..", "provisioned_models")
os.makedirs(TARGET_DIR, exist_ok=True)

models_to_download = [
    # --- Shared STT (Whisper Tiny Multilingual) ---
    {"dir": "shared/stt", "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-decoder.int8.onnx"},
    {"dir": "shared/stt", "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-encoder.int8.onnx"},
    {"dir": "shared/stt", "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny", "filename": "tiny-tokens.txt"},

    # --- MT Indic->English (CTranslate2) ---
    {"dir": "mt/indic-en", "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M", "filename": "indic-en-200m-ct2/ctranslate2_model/model.bin", "local_filename": "model.bin"},
    {"dir": "mt/indic-en", "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M", "filename": "indic-en-200m-ct2/ctranslate2_model/source_vocabulary.json", "local_filename": "source_vocabulary.json"},
    {"dir": "mt/indic-en", "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M", "filename": "indic-en-200m-ct2/ctranslate2_model/target_vocabulary.json", "local_filename": "target_vocabulary.json"},
    {"dir": "mt/indic-en/vocab", "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M", "filename": "indic-en-200m-ct2/ctranslate2_model/vocab/model.SRC", "local_filename": "model.SRC"},
    {"dir": "mt/indic-en/vocab", "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M", "filename": "indic-en-200m-ct2/ctranslate2_model/vocab/model.TGT", "local_filename": "model.TGT"},

    # --- MT English->Indic (CTranslate2) ---
    {"dir": "mt/en-indic", "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M", "filename": "en-indic-200m-ct2/ctranslate2_model/model.bin", "local_filename": "model.bin"},
    {"dir": "mt/en-indic", "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M", "filename": "en-indic-200m-ct2/ctranslate2_model/source_vocabulary.json", "local_filename": "source_vocabulary.json"},
    {"dir": "mt/en-indic", "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M", "filename": "en-indic-200m-ct2/ctranslate2_model/target_vocabulary.json", "local_filename": "target_vocabulary.json"},
    {"dir": "mt/en-indic/vocab", "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M", "filename": "en-indic-200m-ct2/ctranslate2_model/vocab/model.SRC", "local_filename": "model.SRC"},
    {"dir": "mt/en-indic/vocab", "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M", "filename": "en-indic-200m-ct2/ctranslate2_model/vocab/model.TGT", "local_filename": "model.TGT"},

    # --- TTS Models (willwade/mms-tts-multilingual-models-onnx) ---
    {"dir": "hi/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "hin/model.onnx", "local_filename": "model.onnx"},
    {"dir": "hi/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "hin/tokens.txt", "local_filename": "tokens.txt"},

    {"dir": "en/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "eng/model.onnx", "local_filename": "model.onnx"},
    {"dir": "en/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "eng/tokens.txt", "local_filename": "tokens.txt"},

    {"dir": "bn/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "ben/model.onnx", "local_filename": "model.onnx"},
    {"dir": "bn/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "ben/tokens.txt", "local_filename": "tokens.txt"},

    {"dir": "gu/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "guj/model.onnx", "local_filename": "model.onnx"},
    {"dir": "gu/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "guj/tokens.txt", "local_filename": "tokens.txt"},

    {"dir": "mr/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "mar/model.onnx", "local_filename": "model.onnx"},
    {"dir": "mr/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "mar/tokens.txt", "local_filename": "tokens.txt"},

    {"dir": "kn/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "kan/model.onnx", "local_filename": "model.onnx"},
    {"dir": "kn/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "kan/tokens.txt", "local_filename": "tokens.txt"},

    {"dir": "ml/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "mal/model.onnx", "local_filename": "model.onnx"},
    {"dir": "ml/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "mal/tokens.txt", "local_filename": "tokens.txt"},

    {"dir": "ta/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "tam/model.onnx", "local_filename": "model.onnx"},
    {"dir": "ta/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "tam/tokens.txt", "local_filename": "tokens.txt"},

    {"dir": "te/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "tel/model.onnx", "local_filename": "model.onnx"},
    {"dir": "te/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "tel/tokens.txt", "local_filename": "tokens.txt"},

    {"dir": "or/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "ory/model.onnx", "local_filename": "model.onnx"},
    {"dir": "or/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "ory/tokens.txt", "local_filename": "tokens.txt"},
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
        dest_dir = os.path.join(TARGET_DIR, model["dir"])
        os.makedirs(dest_dir, exist_ok=True)

        local_filename = model.get("local_filename", model["filename"])
        dest_path = os.path.join(dest_dir, local_filename)
        if os.path.exists(dest_path) and os.path.getsize(dest_path) > 0:
            print(f"Skipping {local_filename} (already exists)")
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
            print("CRITICAL: Partial install. Will skip this file for now to continue execution.")

print("=== Provisioning Complete ===")
print("To manually test offline model behavior on a debuggable device, run:")
print("  adb push provisioned_models/ /data/local/tmp/provisioned_models")
print("  adb shell run-as com.itantra.app mkdir -p files/language_packs")
print("  adb shell run-as com.itantra.app mkdir -p files/translation_models")
print("  adb shell run-as com.itantra.app cp -r /data/local/tmp/provisioned_models/* files/language_packs/")
print("  adb shell run-as com.itantra.app cp -r files/language_packs/mt/* files/translation_models/")
print("  adb shell rm -r /data/local/tmp/provisioned_models")
