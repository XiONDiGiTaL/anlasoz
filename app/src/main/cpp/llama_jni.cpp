// JNI bridge between AnlaSöz and llama.cpp.
//
// One engine at a time: a model plus the context that holds its KV cache.
// Generation is called from a single background coroutine in Kotlin, guarded
// there by a mutex, so the engine itself only needs a cancellation flag and a
// lock around the context that can be unloaded from another thread.

#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "ggml.h"
#include "llama.h"

#define TAG "AnlaSozLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int n_ctx = 0;
    int n_threads = 4;
    std::atomic<bool> cancel{false};
    std::mutex mu;

    ~Engine() {
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
    }
};

Engine * as_engine(jlong handle) {
    return reinterpret_cast<Engine *>(handle);
}

std::string to_std(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

void throw_illegal(JNIEnv * env, const char * message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

// Applies the model's own chat template so each family gets the prompt shape it
// was trained on; chatml is the fallback for templates llama.cpp cannot render.
std::string render_prompt(
    Engine * engine,
    const std::vector<std::string> & roles,
    const std::vector<std::string> & contents,
    bool * ok) {
    *ok = false;
    std::vector<llama_chat_message> messages;
    messages.reserve(roles.size());
    for (size_t i = 0; i < roles.size() && i < contents.size(); ++i) {
        llama_chat_message message{};
        message.role = roles[i].c_str();
        message.content = contents[i].c_str();
        messages.push_back(message);
    }

    const char * model_template = llama_model_chat_template(engine->model, nullptr);
    const char * candidates[2];
    candidates[0] = model_template != nullptr ? model_template : "chatml";
    candidates[1] = "chatml";

    for (const char * tmpl : candidates) {
        if (tmpl == nullptr) continue;
        std::vector<char> buffer(4096);
        int32_t needed = llama_chat_apply_template(
            tmpl, messages.data(), messages.size(), true, buffer.data(), (int32_t) buffer.size());
        if (needed < 0) continue;
        if (needed > (int32_t) buffer.size()) {
            buffer.resize((size_t) needed + 1);
            needed = llama_chat_apply_template(
                tmpl, messages.data(), messages.size(), true, buffer.data(), (int32_t) buffer.size());
            if (needed < 0 || needed > (int32_t) buffer.size()) continue;
        }
        *ok = true;
        return std::string(buffer.data(), (size_t) needed);
    }
    return {};
}

std::vector<llama_token> tokenize(
    const llama_vocab * vocab,
    const std::string & text,
    bool * ok) {
    *ok = false;
    if (text.empty()) return {};
    int32_t count = -llama_tokenize(
        vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, true, true);
    if (count <= 0) return {};
    std::vector<llama_token> tokens((size_t) count);
    int32_t written = llama_tokenize(
        vocab, text.c_str(), (int32_t) text.size(), tokens.data(), count, true, true);
    if (written < 0) return {};
    tokens.resize((size_t) written);
    *ok = true;
    return tokens;
}

}  // namespace

extern "C" {

// Only warnings and errors are forwarded: ggml logs every graph node at INFO,
// which is thousands of lines per ubatch shape and measurably slows generation
// on a device that is already memory-bound.
void log_to_android(enum ggml_log_level level, const char * text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
    }
}

JNIEXPORT void JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_backendInit(JNIEnv *, jclass) {
    llama_log_set(log_to_android, nullptr);
    llama_backend_init();
    LOGI("backend: %s", llama_print_system_info());
}

JNIEXPORT jstring JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_backendInfo(JNIEnv * env, jclass) {
    const char * info = llama_print_system_info();
    return env->NewStringUTF(info != nullptr ? info : "");
}

JNIEXPORT jlong JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_loadModel(
    JNIEnv * env, jclass, jstring path, jint n_ctx, jint n_threads) {
    const std::string file = to_std(env, path);
    if (file.empty()) {
        throw_illegal(env, "No model file");
        return 0L;
    }

    auto * engine = new Engine();
    engine->n_ctx = n_ctx > 0 ? n_ctx : 4096;
    engine->n_threads = n_threads > 0 ? n_threads : 4;

    // Defaults are what a phone wants: no GPU offload, memory-mapped weights so
    // the 2 GB file does not have to sit in anonymous memory.
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    LOGI("loading %s (ctx=%d threads=%d)", file.c_str(), engine->n_ctx, engine->n_threads);
    engine->model = llama_model_load_from_file(file.c_str(), model_params);
    if (engine->model == nullptr) {
        delete engine;
        throw_illegal(env, "The model file could not be loaded");
        return 0L;
    }

    // Memory, not throughput, is the constraint on a phone. A 512-token
    // microbatch at a 152k vocabulary alone asks for ~310 MB of logits, and
    // ggml aborts when the allocation fails. Small microbatches keep the
    // transient buffers small; llama.cpp splits long prompts across them.
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (uint32_t) engine->n_ctx;
    ctx_params.n_batch = 256;
    ctx_params.n_ubatch = 128;
    ctx_params.n_threads = engine->n_threads;
    ctx_params.n_threads_batch = engine->n_threads;

    engine->ctx = llama_init_from_model(engine->model, ctx_params);
    if (engine->ctx == nullptr) {
        delete engine;
        throw_illegal(env, "Not enough memory to start the model");
        return 0L;
    }
    engine->vocab = llama_model_get_vocab(engine->model);
    LOGI("model ready");
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_freeModel(JNIEnv *, jclass, jlong handle) {
    auto * engine = as_engine(handle);
    if (engine == nullptr) return;
    {
        std::lock_guard<std::mutex> guard(engine->mu);
        delete engine;
    }
}

JNIEXPORT void JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_cancel(JNIEnv *, jclass, jlong handle) {
    auto * engine = as_engine(handle);
    if (engine != nullptr) engine->cancel.store(true);
}

JNIEXPORT jstring JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_generate(
    JNIEnv * env,
    jclass,
    jlong handle,
    jobjectArray roles_array,
    jobjectArray contents_array,
    jint max_tokens,
    jfloat temperature,
    jfloat top_p,
    jobject progress) {
    auto * engine = as_engine(handle);
    if (engine == nullptr) {
        throw_illegal(env, "The local model is not loaded");
        return nullptr;
    }

    std::lock_guard<std::mutex> guard(engine->mu);
    engine->cancel.store(false);

    // ---- messages ---------------------------------------------------------
    std::vector<std::string> roles;
    std::vector<std::string> contents;
    if (roles_array != nullptr && contents_array != nullptr) {
        jsize count = env->GetArrayLength(roles_array);
        if (count > env->GetArrayLength(contents_array)) count = env->GetArrayLength(contents_array);
        roles.reserve((size_t) count);
        contents.reserve((size_t) count);
        for (jsize i = 0; i < count; ++i) {
            auto role = (jstring) env->GetObjectArrayElement(roles_array, i);
            auto content = (jstring) env->GetObjectArrayElement(contents_array, i);
            roles.push_back(to_std(env, role));
            contents.push_back(to_std(env, content));
            env->DeleteLocalRef(role);
            env->DeleteLocalRef(content);
        }
    }

    bool ok = false;
    const std::string prompt = render_prompt(engine, roles, contents, &ok);
    if (!ok || prompt.empty()) {
        throw_illegal(env, "This model does not expose a usable chat template");
        return nullptr;
    }

    std::vector<llama_token> tokens = tokenize(engine->vocab, prompt, &ok);
    if (!ok || tokens.empty()) {
        throw_illegal(env, "The message could not be tokenized");
        return nullptr;
    }

    const int budget = engine->n_ctx - (int) tokens.size();
    if (budget < 64) {
        throw_illegal(env, "The message is too long for this model's context");
        return nullptr;
    }
    int limit = max_tokens > 0 ? max_tokens : 1024;
    if (limit > budget) limit = budget;

    // Fresh KV cache for every request: conversations are independent turns,
    // and retaining context across them is the app's job, not the engine's.
    llama_memory_clear(llama_get_memory(engine->ctx), true);

    // A batch may not exceed n_batch, and a real message plus its context can
    // be longer than that, so the prompt is decoded in slices. Positions are
    // tracked by the context, so successive slices continue naturally.
    const int64_t prompt_started = ggml_time_ms();
    const size_t chunk = 256;
    for (size_t offset = 0; offset < tokens.size(); offset += chunk) {
        const size_t count = std::min(chunk, tokens.size() - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, (int32_t) count);
        if (llama_decode(engine->ctx, batch) != 0) {
            throw_illegal(env, "The model failed to read the prompt");
            return nullptr;
        }
    }
    LOGI(
        "prompt %d tokens decoded in %lld ms",
        (int) tokens.size(),
        (long long) (ggml_time_ms() - prompt_started));

    auto chain_params = llama_sampler_chain_default_params();
    chain_params.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(chain_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p > 0.f ? top_p : 0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature > 0.f ? temperature : 0.35f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jmethodID report = nullptr;
    if (progress != nullptr) {
        jclass cls = env->GetObjectClass(progress);
        report = env->GetMethodID(cls, "onProgress", "(I)V");
    }

    const int64_t started = ggml_time_ms();
    int64_t first_token = 0;
    std::string output;
    output.reserve((size_t) limit * 4);
    int produced = 0;
    bool failed = false;

    for (int i = 0; i < limit; ++i) {
        if (engine->cancel.load()) break;

        llama_token token = llama_sampler_sample(sampler, engine->ctx, -1);
        if (first_token == 0) first_token = ggml_time_ms() - started;
        if (llama_vocab_is_eog(engine->vocab, token)) break;

        char piece[512];
        const int32_t size = llama_token_to_piece(
            engine->vocab, token, piece, (int32_t) sizeof(piece), 0, true);
        if (size > 0) output.append(piece, (size_t) size);
        ++produced;

        if (report != nullptr && produced % 24 == 0) {
            env->CallVoidMethod(progress, report, (jint) produced);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                report = nullptr;
            }
        }
        if (produced % 32 == 0) {
            const int64_t now = ggml_time_ms() - started;
            LOGI(
                "%d tokens in %lld ms (%.2f tok/s)",
                produced,
                (long long) now,
                now > 0 ? produced * 1000.0 / now : 0.0);
        }

        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(engine->ctx, next) != 0) {
            failed = true;
            break;
        }
    }

    llama_sampler_free(sampler);

    if (failed) {
        throw_illegal(env, "The model stopped unexpectedly while generating");
        return nullptr;
    }
    const int64_t elapsed = ggml_time_ms() - started;
    const double per_second = elapsed > 0 ? produced * 1000.0 / elapsed : 0.0;
    LOGI(
        "prompt %d tokens, first token %lld ms, %d tokens in %lld ms (%.1f tok/s)",
        (int) tokens.size(),
        (long long) first_token,
        produced,
        (long long) elapsed,
        per_second);
    return env->NewStringUTF(output.c_str());
}

}  // extern "C"
