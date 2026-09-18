# Training and Accuracy Report

## 1. How much has the model been trained in this project?
**Zero.** We have **not** trained or fine-tuned any models from scratch within this project environment. 

### Why?
As established in the Module 6B-1 requirements:
> *"We need baseline WER (Word Error Rate) and latency before domain fine-tuning. Without baseline measurements we cannot prove that later fine-tuning improved anything."*

Our current approach relies exclusively on **pretrained foundational models** created by large AI research organizations:
- **STT (Speech-to-Text):** We are using the `indicconformer` models built by **AI4Bharat**, which were pretrained on thousands of hours of real human speech across Indian datasets (e.g., Shruti, MUCS).
- **TTS (Text-to-Speech):** We are using the `vits-mms` models built by **Meta**, which were trained on real human voice recordings (Massively Multilingual Speech project).

These foundational models were downloaded and plugged directly into our `Sherpa-ONNX` inference engine to establish our "Baseline."

---

## 2. How accurate is it in the real world?
Currently, the real-world accuracy (Word Error Rate) for this specific application is **NOT MEASURED**.

### Why is it not measured yet?
While AI4Bharat and Meta report high accuracy (often 85-95%) on general academic datasets, **real-world operational accuracy drops significantly** depending on factors such as:
1. **Domain Vocabulary:** Are users speaking general conversational Hindi/English, or are they using specific technical/emergency terms (e.g., "bogie derailment," "coordinate 54 North")?
2. **Acoustic Environment:** Is the speaker in a quiet room, or outdoors in high wind with machinery noise?
3. **Microphone Hardware:** How does the specific Android phone's microphone array compress and distort the audio before it reaches the model?

### What needs to happen next to measure it?
We have built the **Word Error Rate (WER) Evaluation Framework** inside the app. To get the real-world accuracy numbers, a user must physically test it using the steps outlined in our newly created `DATASET_PLAN.md`:
1. **Record Real Audio:** Deploy the app to a physical Android device and manually speak 10–20 real utterances across different categories (Emergency, Numbers, Locations).
2. **Run Inference:** Let the offline model transcribe the audio.
3. **Calculate WER:** The app will compare the raw reference transcript against the model's hypothesis using the formula `WER = (Substitutions + Deletions + Insertions) / Total Words`.

Once we have this baseline real-world accuracy from your physical device tests, we can then begin **fine-tuning (training)** the models on specific datasets (like noisy environments or specific vocabulary) to objectively improve that score.
