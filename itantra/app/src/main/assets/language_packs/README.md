# language_packs/ (dev sample manifests only)

`hi_dev_manifest.json` is the ONE example development manifest required by
Task 01. It demonstrates the `LanguagePackManifest` schema
(`domain/model/LanguagePackManifest.kt`) end to end.

It does **not** point at a real model:
- `downloadUrl` is `null` (nothing is hosted).
- `checksumsSha256` is empty (nothing to verify).
- `downloadSizeBytes` / `installedSizeBytes` / per-model `sizeBytes` are
  illustrative placeholder figures for UI/layout purposes only — they are
  packaging metadata for a manifest that describes no real file, not a
  performance measurement, and must not be confused with the
  N/A-until-measured rule that applies to `InferenceMetrics` /
  `TransmissionMetrics`.

`MockLanguagePackRepository` currently uses its own in-memory sample data
for the 10-language list shown in the Language Packs screen; it does not
yet parse this file. `LanguagePackManifestParser` (in
`data/languagepack/`) shows how a manifest like this one would be parsed
by a future real repository.
