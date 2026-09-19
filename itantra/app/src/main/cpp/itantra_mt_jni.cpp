#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <ctranslate2/translator.h>
#include <sentencepiece_processor.h>

struct TranslationEngine {
    std::unique_ptr<ctranslate2::Translator> translator;
    std::unique_ptr<sentencepiece::SentencePieceProcessor> sp_source;
    std::unique_ptr<sentencepiece::SentencePieceProcessor> sp_target;
    std::mutex mtx; // Ensure thread-safe inference
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_itantra_core_translation_CTranslate2TranslationEngine_nativeCreateEngine(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPathStr) {
    const char* c_modelPath = env->GetStringUTFChars(modelPathStr, nullptr);
    std::string modelPath(c_modelPath);
    env->ReleaseStringUTFChars(modelPathStr, c_modelPath);

    try {
        auto* engine = new TranslationEngine();

        // CTranslate2 configuration
        engine->translator = std::make_unique<ctranslate2::Translator>(modelPath, ctranslate2::Device::CPU);
        // Load sentencepiece models from the vocab directory
        engine->sp_source = std::make_unique<sentencepiece::SentencePieceProcessor>();
        engine->sp_target = std::make_unique<sentencepiece::SentencePieceProcessor>();

        auto src_status = engine->sp_source->Load(modelPath + "/vocab/model.SRC");
        if (!src_status.ok()) {
             // Fallback if vocab is in same directory
             src_status = engine->sp_source->Load(modelPath + "/model.SRC");
             if (!src_status.ok()) {
                 delete engine;
                 jclass exClass = env->FindClass("java/lang/RuntimeException");
                 env->ThrowNew(exClass, "Failed to load source SentencePiece model.");
                 return 0;
             }
        }

        auto tgt_status = engine->sp_target->Load(modelPath + "/vocab/model.TGT");
        if (!tgt_status.ok()) {
             tgt_status = engine->sp_target->Load(modelPath + "/model.TGT");
             if (!tgt_status.ok()) {
                 tgt_status = engine->sp_target->Load(modelPath + "/model.SRC");
                 if (!tgt_status.ok()) {
                     delete engine;
                     jclass exClass = env->FindClass("java/lang/RuntimeException");
                     env->ThrowNew(exClass, "Failed to load target SentencePiece model.");
                     return 0;
                 }
             }
        }

        return reinterpret_cast<jlong>(engine);
    } catch (const std::exception& e) {
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exClass, e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_itantra_core_translation_CTranslate2TranslationEngine_nativeDestroyEngine(
        JNIEnv* env,
        jobject /* this */,
        jlong handle) {
    if (handle != 0) {
        auto* engine = reinterpret_cast<TranslationEngine*>(handle);
        delete engine;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_itantra_core_translation_CTranslate2TranslationEngine_nativeTranslate(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jstring textStr,
        jstring sourceTagStr,
        jstring targetTagStr) {
    if (handle == 0) return env->NewStringUTF("");

    auto* engine = reinterpret_cast<TranslationEngine*>(handle);
    std::lock_guard<std::mutex> lock(engine->mtx);

    const char* c_text = env->GetStringUTFChars(textStr, nullptr);
    const char* c_sourceTag = env->GetStringUTFChars(sourceTagStr, nullptr);
    const char* c_targetTag = env->GetStringUTFChars(targetTagStr, nullptr);

    std::string text(c_text);
    std::string sourceTag(c_sourceTag);
    std::string targetTag(c_targetTag);

    env->ReleaseStringUTFChars(textStr, c_text);
    env->ReleaseStringUTFChars(sourceTagStr, c_sourceTag);
    env->ReleaseStringUTFChars(targetTagStr, c_targetTag);

    try {
        // 1. SentencePiece Tokenization
        std::vector<std::string> sp_tokens;
        engine->sp_source->Encode(text, &sp_tokens);

        // 2. Add AI4Bharat tags.
        // Official IndicTrans2 expects [SRC_LANG, TGT_LANG, *SP_TOKENS]
        std::vector<std::string> source_tokens;
        if (!sourceTag.empty()) {
            source_tokens.push_back(sourceTag);
        }
        if (!targetTag.empty()) {
            source_tokens.push_back(targetTag);
        }

        // Insert sp_tokens
        source_tokens.insert(source_tokens.end(), sp_tokens.begin(), sp_tokens.end());

        // 3. Translate
        std::vector<std::vector<std::string>> batch = {source_tokens};
        ctranslate2::TranslationOptions options;
        options.max_decoding_length = 256;
        options.beam_size = 4; // standard beam search

        std::vector<ctranslate2::TranslationResult> results = engine->translator->translate_batch(batch, options);
        if (results.empty()) {
            return env->NewStringUTF("");
        }

        const auto& target_tokens = results[0].output();

        // 4. Detokenize
        std::string output_text;
        engine->sp_target->Decode(target_tokens, &output_text);

        return env->NewStringUTF(output_text.c_str());
    } catch (const std::exception& e) {
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exClass, e.what());
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_itantra_core_translation_CTranslate2TranslationEngine_nativePrepareInputForTest(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jstring textStr,
        jstring sourceTagStr,
        jstring targetTagStr) {
    if (handle == 0) return nullptr;

    auto* engine = reinterpret_cast<TranslationEngine*>(handle);
    std::lock_guard<std::mutex> lock(engine->mtx);

    const char* c_text = env->GetStringUTFChars(textStr, nullptr);
    const char* c_sourceTag = env->GetStringUTFChars(sourceTagStr, nullptr);
    const char* c_targetTag = env->GetStringUTFChars(targetTagStr, nullptr);

    std::string text(c_text);
    std::string sourceTag(c_sourceTag);
    std::string targetTag(c_targetTag);

    env->ReleaseStringUTFChars(textStr, c_text);
    env->ReleaseStringUTFChars(sourceTagStr, c_sourceTag);
    env->ReleaseStringUTFChars(targetTagStr, c_targetTag);

    try {
        std::vector<std::string> sp_tokens;
        engine->sp_source->Encode(text, &sp_tokens);

        std::vector<std::string> source_tokens;
        if (!sourceTag.empty()) {
            source_tokens.push_back(sourceTag);
        }
        if (!targetTag.empty()) {
            source_tokens.push_back(targetTag);
        }
        source_tokens.insert(source_tokens.end(), sp_tokens.begin(), sp_tokens.end());

        jclass stringClass = env->FindClass("java/lang/String");
        jobjectArray result = env->NewObjectArray(source_tokens.size(), stringClass, env->NewStringUTF(""));
        for (size_t i = 0; i < source_tokens.size(); ++i) {
            env->SetObjectArrayElement(result, i, env->NewStringUTF(source_tokens[i].c_str()));
        }
        return result;
    } catch (const std::exception& e) {
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exClass, e.what());
        return nullptr;
    }
}
