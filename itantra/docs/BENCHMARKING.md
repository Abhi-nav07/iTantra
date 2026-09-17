# Word Error Rate (WER) Benchmarking

## Methodology
iTantra uses a strict Levenshtein-distance algorithm applied at the **word level**.
This ensures all substitutions, deletions, and insertions are precisely tracked against reference texts.

**Normalization Rules:**
1. Text is lowercased (for code-switched English words).
2. Punctuation is aggressively removed (Devanagari danda, commas, periods, quotes).
3. Matras, numeric spelling, and general spelling are **NOT** normalized away. If the STT mishears a word, we count it as an honest failure.

## Metrics Recorded
Each session logs:
- `Audio Duration`: Time from PTT press to PTT release.
- `Finalization Latency`: Time from PTT release until the recognizer produces final text.
- `Real Time Factor (RTF)`: Finalization Latency / Audio Duration. (Ideally < 1).
- `WER`: Percentage of words incorrectly transcribed.

## Execution
Run the internal benchmark tool in the App via:
`Diagnostics Screen -> RUN HINDI WER BENCHMARK`.

Results are exported locally to `Context.filesDir/benchmarks/`.
