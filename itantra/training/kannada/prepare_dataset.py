#!/usr/bin/env python3
"""
Dataset preparation script for Kannada iTantra Domain Training.
Status: PENDING COMPUTE / DATASET BLOCKER

This script will:
1. Parse raw audio recordings and transcripts.
2. Standardize audio to 16kHz, mono, PCM.
3. Split into Train/Validation/Test (speaker disjoint).
4. Generate manifests for the training pipeline.
"""

import sys

def main():
    print("Error: Real iTantra field dataset not yet collected.")
    print("Cannot prepare dataset.")
    sys.exit(1)

if __name__ == "__main__":
    main()
