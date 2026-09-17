# iTantra Hindi Domain Training Pipeline

This directory contains the pipeline for fine-tuning the Hindi speech model on the real-world iTantra field dataset.

## Status
- **Pretrained Baseline**: Verified (IndicConformer -> Sherpa-ONNX)
- **Domain Dataset**: PENDING COLLECTION (Real field dataset required)
- **Training**: BLOCKED (Waiting for dataset)

## Data Architecture
We mandate strict separation:
- `train`: 80% (speaker disjoint)
- `validation`: 10% (speaker disjoint)
- `test`: 10% (unseen speakers)

Main dataset MUST NOT be synthetic. Use synthetic audio ONLY for augmentation if necessary.

## Process
1. `prepare_dataset.py`: Organizes, filters, and formats real audio (16kHz mono) and generates manifests.
2. `evaluate.py`: Establishes pre-training baseline WER.
3. `train.py`: Fine-tunes the base model using the dataset.
4. `export_mobile.py`: Exports and quantizes (INT8) the model to ONNX for Android inference.
