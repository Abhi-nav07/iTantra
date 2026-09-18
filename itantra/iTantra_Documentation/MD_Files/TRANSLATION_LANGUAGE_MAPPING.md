# Module 7A: Translation Language Mapping

This document maps the 10 supported iTantra internal `LanguageCode` constants to standard language tokens typically used by translation models like IndicTrans2 or OPUS-MT.

## iTantra Internal Enum
Our system uses the `LanguageCode` enum which maps to the ISO 639-1 / 639-2 standard where possible.

```kotlin
enum class LanguageCode(val id: Int) {
    HINDI(1),      // hi
    ENGLISH(2),    // en
    MARATHI(3),    // mr
    BENGALI(4),    // bn
    GUJARATI(5),   // gu
    ODIA(6),       // or
    TAMIL(7),      // ta
    TELUGU(8),     // te
    KANNADA(9),    // kn
    MALAYALAM(10)  // ml
}
```

## Translation Model Tokens
When passing text to the translation engine, the engine requires specific BCP-47 or custom tokens (e.g., `__hi__`).

| iTantra Code | Language | Typical IndicTrans2 Token | FLORES-200 Token |
|--------------|----------|---------------------------|------------------|
| `hi` | Hindi | `hin_Deva` | `hin_Deva` |
| `en` | English | `eng_Latn` | `eng_Latn` |
| `mr` | Marathi | `mar_Deva` | `mar_Deva` |
| `bn` | Bengali | `ben_Beng` | `ben_Beng` |
| `gu` | Gujarati | `guj_Gujr` | `guj_Gujr` |
| `or` | Odia | `ory_Orya` | `ory_Orya` |
| `ta` | Tamil | `tam_Taml` | `tam_Taml` |
| `te` | Telugu | `tel_Telu` | `tel_Telu` |
| `kn` | Kannada | `kan_Knda` | `kan_Knda` |
| `ml` | Malayalam | `mal_Mlym` | `mal_Mlym` |

## Router Resolution
The `TranslationRouter` will contain a mapper that converts the incoming/outgoing `LanguageCode` to the specific string token required by the loaded ONNX model graph. If an unsupported language code is requested, the router gracefully defaults to bypassing translation.
