import ctranslate2
import sentencepiece as spm
from IndicTransToolkit import IndicProcessor
import os
import sys

# To fix Windows console unicode printing
sys.stdout.reconfigure(encoding='utf-8')

def translate(text, src_lang, tgt_lang, model_dir):
    ip = IndicProcessor(inference=True)
    
    # 1. Preprocess
    # 1. Preprocess without prepending tags to the string itself.
    # IndicProcessor.preprocess_batch normally prepends tags if we provide them.
    # We will provide them so it normalizes correctly, but we will STRIP the tags before tokenizing,
    # OR we can just omit src_lang/tgt_lang and prepend manually. But omitting might skip some language-specific rules.
    preprocessed_text_with_tags = ip.preprocess_batch([text], src_lang=src_lang, tgt_lang=tgt_lang)[0]
    
    # Extract the raw preprocessed text by stripping the two tags
    # Example: "hin_Deva eng_Latn मुझे..." -> "मुझे..."
    parts = preprocessed_text_with_tags.split(" ", 2)
    if len(parts) >= 3 and parts[0] == src_lang and parts[1] == tgt_lang:
        preprocessed_text = parts[2]
    else:
        preprocessed_text = preprocessed_text_with_tags

    # 2. Tokenize the raw preprocessed text
    sp_source = spm.SentencePieceProcessor(model_file=os.path.join(model_dir, "vocab", "model.SRC"))
    sp_tokens = sp_source.encode(preprocessed_text, out_type=str)
    
    # 3. Manually construct the input token sequence: [SRC_LANG, TGT_LANG, *SP_TOKENS]
    source_tokens = [src_lang, tgt_lang] + sp_tokens
    
    # 3. Translate
    translator = ctranslate2.Translator(model_dir, device="cpu")
    results = translator.translate_batch([source_tokens], max_decoding_length=256, beam_size=4)
    target_tokens = results[0].hypotheses[0]
    
    # 4. Detokenize
    sp_target = spm.SentencePieceProcessor(model_file=os.path.join(model_dir, "vocab", "model.TGT"))
    output_text = sp_target.decode(target_tokens)
    
    # 5. Postprocess
    final_output = ip.postprocess_batch([output_text], lang=tgt_lang)[0]
    
    print("SOURCE TEXT:")
    print(text)
    print("PREPROCESSED INPUT:")
    print(preprocessed_text)
    print("FINAL CT2 INPUT TOKENS:")
    print(source_tokens)
    print("RAW CT2 OUTPUT TOKENS:")
    print(target_tokens)
    print("FINAL POSTPROCESSED TRANSLATION:")
    print(final_output)
    print("--------------------------------------------------")
    
    return final_output

def main():
    hi_en_model = os.path.join("provisioned_models", "mt", "indic-en")
    en_hi_model = os.path.join("provisioned_models", "mt", "en-indic")
    
    try:
        print("Hindi->English:")
        translate("मुझे चिकित्सा सहायता चाहिए", "hin_Deva", "eng_Latn", hi_en_model)
        
        print("English->Hindi:")
        translate("We need medical assistance", "eng_Latn", "hin_Deva", en_hi_model)
        
        print("Bengali->English:")
        translate("আমার চিকিৎসা সহায়তা প্রয়োজন", "ben_Beng", "eng_Latn", hi_en_model)
        
        print("English->Tamil:")
        translate("We need medical assistance", "eng_Latn", "tam_Taml", en_hi_model)
        
        print("Marathi->Kannada pivot:")
        print("Hop 1 (Marathi->English):")
        pivot = translate("मला वैद्यकीय मदतीची गरज आहे", "mar_Deva", "eng_Latn", hi_en_model)
        print("Hop 2 (English->Kannada):")
        translate(pivot, "eng_Latn", "kan_Knda", en_hi_model)
    except Exception as e:
        print(f"HOST MT ROOT CAUSE: {e}")

if __name__ == "__main__":
    main()
