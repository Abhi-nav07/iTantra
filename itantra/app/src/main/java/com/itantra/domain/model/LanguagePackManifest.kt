package com.itantra.domain.model

import kotlinx.serialization.Serializable

/**
 * Versioned, on-disk/over-the-wire description of a downloadable language
 * pack. This is pure data — no download or install logic lives here.
 *
 * Backward compatibility strategy: [schemaVersion] is bumped whenever a
 * breaking change is made to this shape. Parsers must check
 * [schemaVersion] before trusting any other field, and should reject (not
 * guess-fill) manifests with an unrecognized major schema version.
 *
 * NOTE (Task 01): no real model files are referenced by any manifest
 * shipped with this task. See app/src/main/assets/language_packs/ for the
 * one example development manifest, which points at placeholder sizes and
 * a non-resolving example URL.
 */
@Serializable
data class LanguagePackManifest(
    /** Bump on breaking shape changes. Current: 1. */
    val schemaVersion: Int,

    /** Wire code matching [LanguageCode.wireCode], e.g. "hi". */
    val languageCode: String,

    val displayName: String,

    /** Monotonically increasing per-language pack version, e.g. "1.0.0". */
    val packVersion: String,

    /** Compressed download size, for showing the user before they commit. */
    val downloadSizeBytes: Long,

    /** Size once unpacked/installed on device. */
    val installedSizeBytes: Long,

    /** Lowest app versionCode able to load this pack's model format. */
    val minimumAppVersion: Int,

    val sttModel: ModelAssetInfo,
    val ttsModel: ModelAssetInfo,

    /**
     * Recommended minimum free RAM (bytes) to safely load this pack's
     * STT+TTS assets together without evicting other active-language
     * assets. Advisory only in Task 01 — not enforced yet.
     */
    val minimumRamBytes: Long,
)

/**
 * Describes one on-device inference asset (either the STT or the TTS side
 * of a pack). Kept generic so it can describe very different runtimes
 * (e.g. an ONNX Runtime Mobile graph vs. a sherpa-onnx bundle) without a
 * schema change.
 */
@Serializable
data class ModelAssetInfo(
    /** Human-readable model identifier/family, e.g. "sherpa-onnx-streaming-zipformer". */
    val modelId: String,

    /** Model file format tag: "onnx", "tflite", "ggml", etc. Free-form on
     *  purpose — new formats must not require a schema bump. */
    val format: String,

    /** Quantization applied, e.g. "int8", "fp16", "none". */
    val quantization: String,

    /** SHA-256 checksum(s) of the asset file(s), for install-time and
     *  periodic integrity validation. Empty map is valid for Task 01
     *  placeholder manifests where no real file exists yet. */
    val checksumsSha256: Map<String, String> = emptyMap(),

    /** Optional remote source. Null means "not yet published" — a manifest
     *  entry may exist before its file is hosted anywhere. */
    val downloadUrl: String? = null,

    /** List of remote filenames to download. The first file is considered primary. */
    val files: List<String> = emptyList(),

    val sizeBytes: Long,
)
