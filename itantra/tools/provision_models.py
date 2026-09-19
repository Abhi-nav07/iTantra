import sys
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
    {
        "dir": "shared/stt",
        "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny",
        "filename": "tiny-encoder.int8.onnx",
        "expected_sha256": "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"
    },
    {
        "dir": "shared/stt",
        "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny",
        "filename": "tiny-decoder.int8.onnx",
        "expected_sha256": "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"
    },
    {
        "dir": "shared/stt",
        "repo_id": "csukuangfj/sherpa-onnx-whisper-tiny",
        "filename": "tiny-tokens.txt",
        "expected_sha256": "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"
    },

    # --- MT Indic->English (CTranslate2) ---
    {
        "dir": "mt/indic-en",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M",
        "filename": "indic-en-200m-ct2/ctranslate2_model/model.bin",
        "local_filename": "model.bin",
        "expected_sha256": "a88c1c56918d267c4003279b2b3d4ed47f7e18a859d38ebb8491da6c422e4de8"
    },
    {
        "dir": "mt/indic-en",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M",
        "filename": "indic-en-200m-ct2/ctranslate2_model/config.json",
        "local_filename": "config.json",
        "expected_sha256": "10874b31f398169314575b99a04560b6d8944fba1c7a0d96eb89f7676bf08979"
    },
    {
        "dir": "mt/indic-en",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M",
        "filename": "indic-en-200m-ct2/ctranslate2_model/source_vocabulary.json",
        "local_filename": "source_vocabulary.json",
        "expected_sha256": "26d1ba4b6e918bef2bdccf1e0370130a3d641d30f7a945898a00f56043a9d8ad"
    },
    {
        "dir": "mt/indic-en",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M",
        "filename": "indic-en-200m-ct2/ctranslate2_model/target_vocabulary.json",
        "local_filename": "target_vocabulary.json",
        "expected_sha256": "debadde275c3afb460bf97913d02959537a4e24350376b93f1cba878e3661453"
    },
    {
        "dir": "mt/indic-en/vocab",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M",
        "filename": "indic-en-200m-ct2/ctranslate2_model/vocab/model.SRC",
        "local_filename": "model.SRC",
        "expected_sha256": "ac9257c8e76b8b607705b959cc3d075656ea33032f7a974e467b8941df6e98d4"
    },
    {
        "dir": "mt/indic-en/vocab",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M",
        "filename": "indic-en-200m-ct2/ctranslate2_model/vocab/model.TGT",
        "local_filename": "model.TGT",
        "expected_sha256": "3cedc5cbcc740369b76201942a0f096fec7287fee039b55bdb956f301235b914"
    },

    # --- MT English->Indic (CTranslate2) ---
    {
        "dir": "mt/en-indic",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M",
        "filename": "en-indic-200m-ct2/ctranslate2_model/model.bin",
        "local_filename": "model.bin",
        "expected_sha256": "2b4b4c195008f27e97f39be503f470b583c22fd670e67a9a886431df61348eba"
    },
    {
        "dir": "mt/en-indic",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M",
        "filename": "en-indic-200m-ct2/ctranslate2_model/config.json",
        "local_filename": "config.json",
        "expected_sha256": "10874b31f398169314575b99a04560b6d8944fba1c7a0d96eb89f7676bf08979"
    },
    {
        "dir": "mt/en-indic",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M",
        "filename": "en-indic-200m-ct2/ctranslate2_model/source_vocabulary.json",
        "local_filename": "source_vocabulary.json",
        "expected_sha256": "66919522447d51be41250cc780c656e2ef495c403f96a7e4294dc36472524b8d"
    },
    {
        "dir": "mt/en-indic",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M",
        "filename": "en-indic-200m-ct2/ctranslate2_model/target_vocabulary.json",
        "local_filename": "target_vocabulary.json",
        "expected_sha256": "7f28e3f24fcd0a203eb307e8c56ac0b99350a6a1562c25b6e8ffd97d6016878c"
    },
    {
        "dir": "mt/en-indic/vocab",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M",
        "filename": "en-indic-200m-ct2/ctranslate2_model/vocab/model.SRC",
        "local_filename": "model.SRC",
        "expected_sha256": "3cedc5cbcc740369b76201942a0f096fec7287fee039b55bdb956f301235b914"
    },
    {
        "dir": "mt/en-indic/vocab",
        "repo_id": "adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M",
        "filename": "en-indic-200m-ct2/ctranslate2_model/vocab/model.TGT",
        "local_filename": "model.TGT",
        "expected_sha256": "ac9257c8e76b8b607705b959cc3d075656ea33032f7a974e467b8941df6e98d4"
    },

    # --- TTS Models (willwade/mms-tts-multilingual-models-onnx) ---
    {"dir": "hi/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "hin/model.onnx", "local_filename": "model.onnx", "expected_sha256": "c44b4179e7ff0da4d76eac3929fc1dbd67c51f02da8fca083c445e17450c3f75"},
    {"dir": "hi/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "hin/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d"},

    {"dir": "en/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "eng/model.onnx", "local_filename": "model.onnx", "expected_sha256": "409bafdb550948dc5c0b216e21b341732ef3cefead8008b512b034f1bdb27132"},
    {"dir": "en/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "eng/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "87c8ef66eae5473ed0cc0366b3964c736ca6c5f676c979522ea31234e47430b9"},

    {"dir": "bn/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "ben/model.onnx", "local_filename": "model.onnx", "expected_sha256": "c67081369061e87ba302bfbc0445543c4c23c04133514b7d6c7be2710363bc42"},
    {"dir": "bn/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "ben/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "a74190ec42b8f0afb349276430c716f1b585090d8fe6d3c1d6d5fe510798a387"},

    {"dir": "gu/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "guj/model.onnx", "local_filename": "model.onnx", "expected_sha256": "6d9b4f268765e19b6620983bd698c5e4fa943309560b6c7b199b50a4aae30d47"},
    {"dir": "gu/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "guj/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "2d855f2affb7586cc6be095a4382eb0bee2a22242deda32c86aab6b1a810d8c4"},

    {"dir": "mr/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "mar/model.onnx", "local_filename": "model.onnx", "expected_sha256": "046cecf7cca77680c9ca319953e777c8d0233ce78e9b55a47aa909edf87d99e8"},
    {"dir": "mr/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "mar/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "4d968029d0754b41633cb0871cce6796a5ab3d3bc2b9b91c5721cfdf85156083"},

    {"dir": "kn/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "kan/model.onnx", "local_filename": "model.onnx", "expected_sha256": "9644cfa46c5802c4c2c8196f91eedc0cd54d46527bbc32589ebaf967ed07591f"},
    {"dir": "kn/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "kan/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "a4a44037f7492c9e5b69a4483250e54af9a2d528bd4e19de95a21ff0c5e82a8a"},

    {"dir": "ml/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "mal/model.onnx", "local_filename": "model.onnx", "expected_sha256": "7f03397b7144325dc3deab36315c59e423319b8b575497caacbb08d28072b26c"},
    {"dir": "ml/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "mal/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "3a752c36593cc519193c8caa6c00371369b423366fa6ead8d91945a28b2c46a3"},

    {"dir": "ta/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "tam/model.onnx", "local_filename": "model.onnx", "expected_sha256": "c44055b2e7c5719ca26708f6eb03680b0f1bbf6fdcb0df6480478f872f1f6e34"},
    {"dir": "ta/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "tam/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "0b3f692319bb5fae8658e2f84bf252bca92450d0207bbba7273caa1a182d81b8"},

    {"dir": "te/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "tel/model.onnx", "local_filename": "model.onnx", "expected_sha256": "a8ec6093c0ec3c6dcb7edc0c4cd6c08e312cd3299b00d0aa4988d2a7fea2baa5"},
    {"dir": "te/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "tel/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "528152cb4121272e6e71f7a1d91c8d4922b92fae414ae599db608cb92a738bb7"},

    {"dir": "or/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "ory/model.onnx", "local_filename": "model.onnx", "expected_sha256": "ab867f39b00f1fcb39dc97f5079150959b50df50d7f10e75232c56b52f19c021"},
    {"dir": "or/tts", "repo_id": "willwade/mms-tts-multilingual-models-onnx", "filename": "ory/tokens.txt", "local_filename": "tokens.txt", "expected_sha256": "bc90e5423446ca730aef6b56a4656cb07421c7538429a50b9c396fe0758fd587"},
]

def calculate_sha256(filepath):
    sha256_hash = hashlib.sha256()
    with open(filepath, "rb") as f:
        for byte_block in iter(lambda: f.read(65536), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

print("=== Starting Verified Model Provisioning ===")
failures = []

for model in models_to_download:
    dest_dir = os.path.join(TARGET_DIR, model["dir"])
    os.makedirs(dest_dir, exist_ok=True)

    local_filename = model.get("local_filename", model["filename"])
    dest_path = os.path.join(dest_dir, local_filename)
    expected_sha = model.get("expected_sha256")

    # If file already exists and is non-zero, verify its integrity
    if os.path.exists(dest_path) and os.path.getsize(dest_path) > 0:
        actual_sha = calculate_sha256(dest_path)
        if expected_sha and actual_sha.lower() == expected_sha.lower():
            print(f"ALREADY_INSTALLED_VERIFIED: {model['dir']}/{local_filename} (SHA256: {actual_sha[:16]}...)")
            continue
        else:
            print(f"CORRUPT_EXISTING: {dest_path} failed checksum verification! Expected {expected_sha}, got {actual_sha}. Re-downloading...")
            try:
                os.remove(dest_path)
            except Exception as e:
                print(f"Could not remove corrupt file: {e}")

    print(f"DOWNLOADING: {model['filename']} from {model['repo_id']}...")
    try:
        with tempfile.TemporaryDirectory() as tmpdir:
            tmp_path = hf_hub_download(
                repo_id=model["repo_id"],
                filename=model["filename"],
                local_dir=tmpdir
            )

            # Check zero-byte
            if os.path.getsize(tmp_path) == 0:
                print(f"FAILED: Downloaded file is 0 bytes: {model['filename']}")
                failures.append(f"{model['dir']}/{local_filename} (Zero bytes)")
                continue

            # SHA256 Check
            actual_sha = calculate_sha256(tmp_path)
            if expected_sha:
                if actual_sha.lower() != expected_sha.lower():
                    print(f"FAILED: Checksum mismatch for {model['filename']}!")
                    print(f"  Expected: {expected_sha}")
                    print(f"  Calculated: {actual_sha}")
                    failures.append(f"{model['dir']}/{local_filename} (Checksum mismatch)")
                    continue
                else:
                    print(f"HASH_VERIFIED: {model['filename']} matches expected SHA256")
            else:
                print(f"REVIEW_REQUIRED: No expected SHA256 configured for {model['filename']}")

            # Atomic move into final position
            shutil.move(tmp_path, dest_path)
            print(f"INSTALLED: {dest_path}")

    except Exception as e:
        print(f"FAILED: {model['filename']}: {e}")
        failures.append(f"{model['dir']}/{local_filename} ({e})")

# Directory Layout Verification
print("\n=== Verifying Complete Directory Layout ===")
missing_artifacts = []
for model in models_to_download:
    local_filename = model.get("local_filename", model["filename"])
    expected_path = os.path.join(TARGET_DIR, model["dir"], local_filename)
    if not os.path.exists(expected_path) or os.path.getsize(expected_path) == 0:
        missing_artifacts.append(f"{model['dir']}/{local_filename}")

if missing_artifacts:
    print("FAILED: The following required artifacts are missing or empty:")
    for m in missing_artifacts:
        print(f"  - {m}")
    failures.extend(missing_artifacts)

if failures:
    print("\n=== PROVISIONING FAILED ===")
    print(f"Total failures: {len(failures)}")
    for f in set(failures):
        print(f"  - {f}")
    sys.exit(1)

print("\n=== Provisioning Complete: All 10 Languages + Shared STT + MT Verified ===")
print("Layout confirmed:")
print(f"  - Shared STT: {os.path.join(TARGET_DIR, 'shared', 'stt')}")
print(f"  - MT Models: {os.path.join(TARGET_DIR, 'mt')}")
print(f"  - TTS Models: 10 languages under {TARGET_DIR}/<lang>/tts")
print("\nTo sideload to an Android device or emulator:")
print("  python tools/sideload_models.py")
