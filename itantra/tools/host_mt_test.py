import os
import sys

# To fix Windows console unicode printing
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

def validate_translation(source_text, translated_text, src_lang, tgt_lang):
    if not translated_text or not isinstance(translated_text, str):
        return False, "Empty translation output"
    cleaned = translated_text.strip()
    if not cleaned:
        return False, "Blank translation output"
    if cleaned == source_text.strip():
        return False, "Source text echo (no translation performed)"
    if cleaned in [src_lang, tgt_lang, f"{src_lang} {tgt_lang}"]:
        return False, "Tag-only output"
    return True, "Valid"

def translate(text, src_lang, tgt_lang, model_dir):
    import ctranslate2
    import sentencepiece as spm
    from IndicTransToolkit import IndicProcessor

    ip = IndicProcessor(inference=True)

    # 1. Preprocess with tags for language-specific rules, then strip prefix tags
    preprocessed_text_with_tags = ip.preprocess_batch([text], src_lang=src_lang, tgt_lang=tgt_lang)[0]
    parts = preprocessed_text_with_tags.split(" ", 2)
    if len(parts) >= 3 and parts[0] == src_lang and parts[1] == tgt_lang:
        preprocessed_text = parts[2]
    else:
        preprocessed_text = preprocessed_text_with_tags

    # 2. Tokenize the raw preprocessed text
    vocab_dir = os.path.join(model_dir, "vocab")
    sp_src_path = os.path.join(vocab_dir, "model.SRC") if os.path.exists(os.path.join(vocab_dir, "model.SRC")) else os.path.join(model_dir, "model.SRC")
    sp_tgt_path = os.path.join(vocab_dir, "model.TGT") if os.path.exists(os.path.join(vocab_dir, "model.TGT")) else os.path.join(model_dir, "model.TGT")

    sp_source = spm.SentencePieceProcessor(model_file=sp_src_path)
    sp_tokens = sp_source.encode(preprocessed_text, out_type=str)

    # 3. Construct input token sequence: [SRC_LANG, TGT_LANG, *SP_TOKENS]
    source_tokens = [src_lang, tgt_lang] + sp_tokens

    # 4. Translate
    translator = ctranslate2.Translator(model_dir, device="cpu")
    results = translator.translate_batch([source_tokens], max_decoding_length=256, beam_size=4)
    target_tokens = results[0].hypotheses[0]

    # 5. Detokenize
    sp_target = spm.SentencePieceProcessor(model_file=sp_tgt_path)
    output_text = sp_target.decode(target_tokens)

    # 6. Postprocess
    final_output = ip.postprocess_batch([output_text], lang=tgt_lang)[0]
    return final_output

def main():
    hi_en_model = os.path.join(os.path.dirname(__file__), "..", "provisioned_models", "mt", "indic-en")
    en_hi_model = os.path.join(os.path.dirname(__file__), "..", "provisioned_models", "mt", "en-indic")
    if not os.path.exists(hi_en_model):
        hi_en_model = os.path.join("provisioned_models", "mt", "indic-en")
    if not os.path.exists(en_hi_model):
        en_hi_model = os.path.join("provisioned_models", "mt", "en-indic")

    test_cases = [
        ("hi -> en", "मुझे चिकित्सा सहायता चाहिए", "hin_Deva", "eng_Latn", hi_en_model),
        ("en -> hi", "We need medical assistance", "eng_Latn", "hin_Deva", en_hi_model),
        ("bn -> en", "আমার চিকিৎসা সহায়তা প্রয়োজন", "ben_Beng", "eng_Latn", hi_en_model),
        ("en -> ta", "We need medical assistance", "eng_Latn", "tam_Taml", en_hi_model),
    ]

    failures = 0
    results = {}

    print("========================================")
    print("  iTantra Host Machine Translation Test ")
    print("========================================\n")

    for name, text, src, tgt, model_dir in test_cases:
        print(f"Testing {name}...")
        try:
            output = translate(text, src, tgt, model_dir)
            valid, reason = validate_translation(text, output, src, tgt)
            if valid:
                print(f"  [PASS] {name}: {output}")
                results[name] = output
            else:
                print(f"  [FAIL] {name}: {reason} (Output: '{output}')")
                failures += 1
                results[name] = f"FAIL: {reason}"
        except Exception as e:
            print(f"  [FAIL] {name}: Exception: {e}")
            failures += 1
            results[name] = f"FAIL: {e}"

    # Marathi -> Kannada 2-Hop Pivot
    print("Testing mr -> kn (2-Hop Pivot)...")
    try:
        mr_input = "मला वैद्यकीय मदतीची गरज आहे"
        # Hop 1: mr -> en
        hop1_out = translate(mr_input, "mar_Deva", "eng_Latn", hi_en_model)
        valid1, reason1 = validate_translation(mr_input, hop1_out, "mar_Deva", "eng_Latn")
        if not valid1:
            print(f"  [FAIL] mr -> kn Hop 1 (mr->en) failed: {reason1} (Output: '{hop1_out}')")
            failures += 1
            results["mr -> kn"] = f"FAIL Hop 1: {reason1}"
        else:
            print(f"  Hop 1 (mr->en): {hop1_out}")
            # Hop 2: en -> kn
            hop2_out = translate(hop1_out, "eng_Latn", "kan_Knda", en_hi_model)
            valid2, reason2 = validate_translation(hop1_out, hop2_out, "eng_Latn", "kan_Knda")
            if not valid2:
                print(f"  [FAIL] mr -> kn Hop 2 (en->kn) failed: {reason2} (Output: '{hop2_out}')")
                failures += 1
                results["mr -> kn"] = f"FAIL Hop 2: {reason2}"
            else:
                print(f"  [PASS] mr -> kn Pivot: {hop2_out}")
                results["mr -> kn"] = hop2_out
    except Exception as e:
        print(f"  [FAIL] mr -> kn: Exception: {e}")
        failures += 1
        results["mr -> kn"] = f"FAIL: {e}"

    print("\n----------------------------------------")
    print("Summary of Translation Outputs:")
    print(f"  HI->EN = {results.get('hi -> en', 'NOT_RUN')}")
    print(f"  EN->HI = {results.get('en -> hi', 'NOT_RUN')}")
    print(f"  BN->EN = {results.get('bn -> en', 'NOT_RUN')}")
    print(f"  EN->TA = {results.get('en -> ta', 'NOT_RUN')}")
    print(f"  MR->KN = {results.get('mr -> kn', 'NOT_RUN')}")
    print("----------------------------------------")

    if failures == 0 and len(results) == 5:
        print("\nHOST_REAL_MT = PASS")
        sys.exit(0)
    else:
        print(f"\nHOST_REAL_MT = FAIL (Failures: {failures})")
        sys.exit(1)

if __name__ == "__main__":
    main()
