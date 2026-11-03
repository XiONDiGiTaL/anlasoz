// JNI bridge between AnlaSöz and llama.cpp.
//
// One engine at a time: a model plus the context that holds its KV cache.
// Generation is called from a single background coroutine in Kotlin, guarded
// there by a mutex, so the engine itself only needs a cancellation flag and a
// lock around the context that can be unloaded from another thread.

#include <android/log.h>
#include <jni.h>
#include <sys/system_properties.h>

#include <algorithm>
#include <atomic>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <mutex>
#include <string>
#include <vector>

#include "ggml-backend.h"
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
    int n_layer = 0;
    int gpu_layers = 0;
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

// ggml-vulkan is C++ underneath its C API and throws on a failed pipeline or
// dispatch. Nothing above it catches that, and an uncaught exception ends the
// process — which on Android looks like an out-of-memory kill with no message.
// A phone driver that rejects a shader is a real thing (Qualcomm Adreno does it
// for some quantised matmul shaders), so this is caught, labelled, and handed to
// Kotlin, which reloads the model on the CPU instead.
constexpr const char * GPU_REJECTED = "GPU_UNAVAILABLE";

void throw_gpu_rejected(JNIEnv * env, const char * detail) {
    std::string message = GPU_REJECTED;
    message += ": ";
    message += detail != nullptr ? detail : "the GPU rejected the model's shaders";
    throw_illegal(env, message.c_str());
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
    } else if (getenv("ANLASOZ_VK_DEBUG") != nullptr) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", text);
    }
}

JNIEXPORT void JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_backendInit(JNIEnv *, jclass) {
    llama_log_set(log_to_android, nullptr);
    // ggml's own debug logging is off by default: it prints every graph node and
    // measurably slows generation. adb shell setprop debug.anlasoz.vk 1 turns it
    // on, which is the only way to see which device or pipeline it picked.
    char property[PROP_VALUE_MAX] = {0};
    if (__system_property_get("debug.anlasoz.vk", property) > 0 && property[0] == '1') {
        setenv("ANLASOZ_VK_DEBUG", "1", 1);
    }
    llama_backend_init();
    LOGI("backend: %s", llama_print_system_info());
}

JNIEXPORT jstring JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_backendInfo(JNIEnv * env, jclass) {
    const char * info = llama_print_system_info();
    return env->NewStringUTF(info != nullptr ? info : "");
}

JNIEXPORT jlong JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_supportsGpuOffload(JNIEnv *, jclass) {
    return llama_supports_gpu_offload() ? JNI_TRUE : JNI_FALSE;
}

// First GPU or integrated GPU ggml registered, e.g. "Adreno (TM) 740". Empty
// when the build is CPU-only or the driver exposed no usable device.
JNIEXPORT jstring JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_gpuDeviceName(JNIEnv * env, jclass) {
    llama_backend_init();
    const int count = (int) ggml_backend_dev_count();
    for (int i = 0; i < count; ++i) {
        ggml_backend_dev_t device = ggml_backend_dev_get((size_t) i);
        if (device == nullptr) continue;
        const enum ggml_backend_dev_type type = ggml_backend_dev_type(device);
        if (type != GGML_BACKEND_DEVICE_TYPE_GPU && type != GGML_BACKEND_DEVICE_TYPE_IGPU) {
            continue;
        }
        // The ggml name is a registry key ("Vulkan0"); the description is the
        // hardware ("Adreno (TM) 740"), which is what a person recognises.
        const char * description = ggml_backend_dev_description(device);
        const char * name = ggml_backend_dev_name(device);
        const char * label = (description != nullptr && description[0] != '\0') ? description : name;
        return env->NewStringUTF(label != nullptr ? label : "");
    }
    return env->NewStringUTF("");
}

// The ggml CPU device, for the NULL-terminated device list that keeps a
// CPU-only load away from the GPU backend.
//
// This matters more than it looks. A build that carries Vulkan registers a
// Vulkan_Host device as well, and llama.cpp's scheduler will happily split a
// graph between it and the CPU even when every layer is on the CPU. Each split
// boundary costs a copy and a scheduler round trip, the host device is not given
// the context's thread count, and a 24-layer model then runs on roughly one
// core instead of all of them. Naming the CPU device explicitly means the
// scheduler has nothing else to choose from.
ggml_backend_dev_t cpu_device() {
    const size_t count = ggml_backend_dev_count();
    for (size_t i = 0; i < count; ++i) {
        ggml_backend_dev_t device = ggml_backend_dev_get(i);
        if (device != nullptr && ggml_backend_dev_type(device) == GGML_BACKEND_DEVICE_TYPE_CPU) {
            return device;
        }
    }
    return nullptr;
}

JNIEXPORT jint JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_modelLayerCount(JNIEnv *, jclass, jlong handle) {
    auto * engine = as_engine(handle);
    return engine != nullptr ? engine->n_layer : 0;
}

JNIEXPORT jlong JNICALL
Java_net_xiondigital_anlasoz_ai_LlamaBridge_loadModel(
    JNIEnv * env, jclass, jstring path, jint n_ctx, jint n_threads, jint gpu_layers) {
    const std::string file = to_std(env, path);
    if (file.empty()) {
        throw_illegal(env, "No model file");
        return 0L;
    }

    llama_backend_init();

    auto * engine = new Engine();
    engine->n_ctx = n_ctx > 0 ? n_ctx : 4096;
    engine->n_threads = n_threads > 0 ? n_threads : 4;
    engine->gpu_layers = gpu_layers;

    // Memory-mapped weights so the 2 GB file does not have to sit in anonymous
    // memory. gpu_layers is 0 for CPU-only and negative for "every layer the
    // backend will take": on a phone the weights live in the same RAM either
    // way, but the matrix maths moves to the GPU.
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = gpu_layers;

    // A CPU-only load is pinned to the CPU device so the scheduler cannot split
    // the graph with the GPU backend (see cpu_device above). Static so the
    // pointer stays valid for as long as llama.cpp may read it.
    static ggml_backend_dev_t cpu_only_devices[2] = {nullptr, nullptr};
    if (gpu_layers == 0) {
        if (cpu_only_devices[0] == nullptr) cpu_only_devices[0] = cpu_device();
        if (cpu_only_devices[0] != nullptr) model_params.devices = cpu_only_devices;
    }

    LOGI(
        "loading %s (ctx=%d threads=%d gpu_layers=%d)",
        file.c_str(), engine->n_ctx, engine->n_threads, gpu_layers);
    engine->model = llama_model_load_from_file(file.c_str(), model_params);
    if (engine->model == nullptr) {
        delete engine;
        throw_illegal(env, "The model file could not be loaded");
        return 0L;
    }

    // Memory, not throughput, is the constraint on a phone, and prompt time is
    // where an analysis is won or lost. At a 256-token microbatch the logits of
    // a 152k vocabulary already ask for ~155 MB, which is close to the limit a
    // phone will hand out; going bigger buys little and risks ggml aborting on a
    // failed allocation. 256 measured 105 tokens/s of prompt on an Adreno 740
    // phone against 37 tokens/s at 128.
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (uint32_t) engine->n_ctx;
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 256;
    ctx_params.n_threads = engine->n_threads;
    ctx_params.n_threads_batch = engine->n_threads;

    engine->ctx = llama_init_from_model(engine->model, ctx_params);
    if (engine->ctx == nullptr) {
        delete engine;
        throw_illegal(env, "Not enough memory to start the model");
        return 0L;
    }
    engine->vocab = llama_model_get_vocab(engine->model);
    engine->n_layer = llama_model_n_layer(engine->model);
    // Logged because a context that quietly ran on one thread instead of all of
    // them is the difference between a few seconds and a minute, and nothing
    // else in the app can see the thread count llama.cpp settled on.
    LOGI(
        "model ready: %d layers, %d requested on the gpu, %d/%d threads",
        engine->n_layer,
        gpu_layers,
        llama_n_threads(engine->ctx),
        llama_n_threads_batch(engine->ctx));
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
    LOGI("prompt is %d tokens, %d left for the answer", (int) tokens.size(), budget);
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
    bool gpu_rejected = false;
    std::string gpu_detail;
    for (size_t offset = 0; offset < tokens.size(); offset += chunk) {
        const size_t count = std::min(chunk, tokens.size() - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, (int32_t) count);
        int32_t status = 0;
        try {
            status = llama_decode(engine->ctx, batch);
        } catch (const std::exception & error) {
            gpu_detail = error.what();
            gpu_rejected = true;
            break;
        } catch (...) {
            gpu_rejected = true;
            break;
        }
        if (status != 0) {
            throw_illegal(env, "The model failed to read the prompt");
            return nullptr;
        }
    }
    if (gpu_rejected) {
        LOGE("prompt decode failed: %s", gpu_detail.c_str());
        throw_gpu_rejected(env, gpu_detail.c_str());
        return nullptr;
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
        if (cls != nullptr) {
            report = env->GetMethodID(cls, "onProgress", "(I)V");
            env->DeleteLocalRef(cls);
        }
        // A failed lookup leaves a pending exception, and returning to Java with
        // one set turns a cosmetic progress counter into a process-wide
        // NoSuchMethodError. The counter is not worth that: drop it instead.
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            report = nullptr;
        }
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
        int32_t status = 0;
        try {
            status = llama_decode(engine->ctx, next);
        } catch (const std::exception & error) {
            gpu_detail = error.what();
            gpu_rejected = true;
            break;
        } catch (...) {
            gpu_rejected = true;
            break;
        }
        if (status != 0) {
            failed = true;
            break;
        }
    }

    llama_sampler_free(sampler);

    if (gpu_rejected) {
        LOGE("generation failed: %s", gpu_detail.c_str());
        throw_gpu_rejected(env, gpu_detail.c_str());
        return nullptr;
    }
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
