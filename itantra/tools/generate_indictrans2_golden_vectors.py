import os
import json
import urllib.request
import tempfile
from IndicTransToolkit import IndicProcessor
import sentencepiece as spm

# Golden Vector test cases defined by the user
test_cases = [
    {"src_tag": "hin_Deva", "tgt_tag": "eng_Latn", "text": "मुझे चिकित्सा सहायता चाहिए"},
    {"src_tag": "eng_Latn", "tgt_tag": "hin_Deva", "text": "We need medical assistance"},
    {"src_tag": "ben_Beng", "tgt_tag": "eng_Latn", "text": "আমার একটি ডাক্তারের প্রয়োজন"},
    {"src_tag": "eng_Latn", "tgt_tag": "tam_Taml", "text": "Where is the nearest hospital?"},
    {"src_tag": "mar_Deva", "tgt_tag": "kan_Knda", "text": "मला तातडीने मदत हवी आहे"},
    {"src_tag": "tam_Taml", "tgt_tag": "tel_Telu", "text": "எனக்கு உதவி தேவை"},
    {"src_tag": "guj_Gujr", "tgt_tag": "eng_Latn", "text": "મને મદદ કરો"},
    {"src_tag": "eng_Latn", "tgt_tag": "mal_Mlym", "text": "Call an ambulance"},
    {"src_tag": "kan_Knda", "tgt_tag": "eng_Latn", "text": "ದಯವಿಟ್ಟು ನನಗೆ ಸಹಾಯ ಮಾಡಿ"},
    {"src_tag": "eng_Latn", "tgt_tag": "ory_Orya", "text": "I feel sick"}
]

def download_spm(repo_id, filename):
    url = f"https://huggingface.co/{repo_id}/resolve/main/{filename}"
    out_path = os.path.join(tempfile.gettempdir(), os.path.basename(filename).replace(".SRC", f"_{repo_id.split('-')[-2]}.SRC"))
    if not os.path.exists(out_path):
        print(f"Downloading {filename}...")
        urllib.request.urlretrieve(url, out_path)
    return out_path

def main():
    print("Downloading SentencePiece models for IndicTrans2...")
    sp_en_indic_src = download_spm("adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M", "en-indic-200m-ct2/ctranslate2_model/vocab/model.SRC")
    sp_indic_en_src = download_spm("adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M", "indic-en-200m-ct2/ctranslate2_model/vocab/model.SRC")
    
    sp_en_indic = spm.SentencePieceProcessor(model_file=sp_en_indic_src)
    sp_indic_en = spm.SentencePieceProcessor(model_file=sp_indic_en_src)

    ip = IndicProcessor(inference=True)
    
    vectors = []

    for case in test_cases:
        src = case["src_tag"]
        tgt = case["tgt_tag"]
        text = case["text"]
        
        preprocessed_text = ip.preprocess_batch([text], src_lang=src, tgt_lang=tgt)[0]
        
        sp_model = sp_indic_en if src != "eng_Latn" else sp_en_indic
        sp_tokens = sp_model.encode(preprocessed_text, out_type=str)
        
        vectors.append({
            "raw_input": text,
            "source_tag": src,
            "target_tag": tgt,
            "preprocessed_text": preprocessed_text,
            "sp_tokens": sp_tokens
        })
        
    out_file = os.path.join(os.path.dirname(__file__), "golden_vectors.json")
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(vectors, f, ensure_ascii=False, indent=2)
        
    print(f"Generated {len(vectors)} golden vectors in {out_file}")

if __name__ == "__main__":
    main()
