#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================
// Session
// ============================================================

struct LlamaSession {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    int nCtx = 0;
    int nBatch = 0;
};

static bool isValidSession(const LlamaSession* s) {
    return s && s->model && s->ctx && s->vocab;
}

// ============================================================
// Helpers
// ============================================================

// Count "big" cores. Using little cores for matmul threads slows
// llama.cpp down because every thread waits for the slowest one.
static int detectPerformanceCores() {
    const int total = static_cast<int>(std::thread::hardware_concurrency());
    std::vector<long> freqs;

    for (int i = 0; i < total; ++i) {
        char path[128];
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i);
        FILE* f = fopen(path, "r");
        if (!f)
            continue;
        long khz = 0;
        if (fscanf(f, "%ld", &khz) != 1)
            khz = 0;
        fclose(f);
        if (khz > 0)
            freqs.push_back(khz);
    }

    if (freqs.empty()) {
        return std::max(2, std::min(total, 4));
    }

    const long maxFreq = *std::max_element(freqs.begin(), freqs.end());
    int count = 0;
    for (long f : freqs) {
        if (f * 10 >= maxFreq * 7)
            ++count; // >= 70% of fastest core
    }
    return std::max(2, count);
}

static std::string toStdString(JNIEnv* env, jbyteArray arr) {
    if (!arr)
        return {};
    const jsize len = env->GetArrayLength(arr);
    std::string s(static_cast<size_t>(len), '\0');
    if (len > 0) {
        env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(&s[0]));
    }
    return s;
}

static jbyteArray toByteArray(JNIEnv* env, const char* data, size_t len) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(len));
    if (arr && len > 0) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(len),
                                reinterpret_cast<const jbyte*>(data));
    }
    return arr;
}

// Length of the longest prefix that does not end in a half-finished
// UTF-8 character (tokens can split emoji / CJK across pieces).
static size_t validUtf8Prefix(const std::string& s) {
    const size_t n = s.size();
    for (size_t back = 1; back <= std::min<size_t>(3, n); ++back) {
        const unsigned char c = static_cast<unsigned char>(s[n - back]);
        if ((c & 0xC0) == 0x80)
            continue; // continuation byte
        const size_t need = c >= 0xF0 ? 4 : c >= 0xE0 ? 3 : c >= 0xC0 ? 2 : 1;
        return need > back ? n - back : n;
    }
    return n;
}

// Wrap the text in the model's own chat template (ChatML, Llama-3, Gemma...).
// Instruct models give much better output when prompted this way.
static std::string buildPrompt(llama_model* model, const std::string& system,
                               const std::string& user, bool& templated) {
    templated = false;
    const char* tmpl = llama_model_chat_template(model, nullptr);

    const std::string fallback = system.empty() ? user : system + "\n\n" + user;
    if (!tmpl)
        return fallback;

    std::vector<llama_chat_message> msgs;
    if (!system.empty())
        msgs.push_back({"system", system.c_str()});
    msgs.push_back({"user", user.c_str()});

    std::vector<char> buf(user.size() + system.size() + 1024);
    int n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(),
                                      static_cast<int32_t>(buf.size()));
    if (n > static_cast<int>(buf.size())) {
        buf.resize(static_cast<size_t>(n));
        n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(),
                                      static_cast<int32_t>(buf.size()));
    }
    if (n < 0)
        return fallback;

    templated = true;
    return std::string(buf.data(), static_cast<size_t>(n));
}

// ============================================================
// Load model
// ============================================================

extern "C" JNIEXPORT jlong JNICALL Java_ai_sudo_llama_LlamaBridge_loadModel(
    JNIEnv* env, jobject /* thiz */, jstring modelPath, jint contextSize, jint threads) {

    if (modelPath == nullptr) {
        LOGE("Model path is null");
        return 0;
    }

    static std::once_flag backendOnce;
    std::call_once(backendOnce, [] {
        llama_backend_init();
    });

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path)
        return 0;
    LOGI("Loading model: %s", path);

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0; // CPU only

    llama_model* model = llama_model_load_from_file(path, modelParams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load model");
        return 0;
    }

    const int cpuThreads = threads > 0 ? threads : detectPerformanceCores();
    LOGI("Using %d CPU threads", cpuThreads);

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(contextSize);
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = cpuThreads;
    cp.n_threads_batch = cpuThreads;
    cp.no_perf = true;

    llama_context* ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        return 0;
    }

    auto* session = new LlamaSession();
    session->model = model;
    session->ctx = ctx;
    session->vocab = llama_model_get_vocab(model);
    session->nCtx = static_cast<int>(llama_n_ctx(ctx));
    session->nBatch = static_cast<int>(llama_n_batch(ctx));

    LOGI("Model ready (n_ctx=%d, n_batch=%d)", session->nCtx, session->nBatch);
    return reinterpret_cast<jlong>(session);
}

// ============================================================
// Generate
// ============================================================

extern "C" JNIEXPORT jbyteArray JNICALL Java_ai_sudo_llama_LlamaBridge_generate(
    JNIEnv* env, jobject /* thiz */, jlong sessionPtr, jbyteArray systemPrompt, jbyteArray prompt,
    jint maxTokens, jfloat temperature, jobject listener) {

    auto* session = reinterpret_cast<LlamaSession*>(sessionPtr);
    if (!isValidSession(session) || prompt == nullptr || maxTokens <= 0) {
        LOGE("Invalid arguments");
        return toByteArray(env, "", 0);
    }

    llama_context* ctx = session->ctx;
    const llama_vocab* vocab = session->vocab;

    // --- Build + tokenize prompt -----------------------------
    const std::string userText = toStdString(env, prompt);
    const std::string systemText = toStdString(env, systemPrompt);

    bool templated = false;
    const std::string full = buildPrompt(session->model, systemText, userText, templated);

    // Templates already contain BOS where needed; don't add it twice.
    const bool addSpecial = !templated;

    int n = -llama_tokenize(vocab, full.c_str(), static_cast<int32_t>(full.size()), nullptr, 0,
                            addSpecial, true);
    if (n <= 0) {
        LOGE("Failed to calculate token count");
        return toByteArray(env, "", 0);
    }

    std::vector<llama_token> tokens(static_cast<size_t>(n));
    n = llama_tokenize(vocab, full.c_str(), static_cast<int32_t>(full.size()), tokens.data(),
                       static_cast<int32_t>(tokens.size()), addSpecial, true);
    if (n <= 0) {
        LOGE("Tokenization failed: %d", n);
        return toByteArray(env, "", 0);
    }
    tokens.resize(static_cast<size_t>(n));

    if (n >= session->nCtx - 1) {
        LOGE("Prompt (%d tokens) does not fit in context (%d)", n, session->nCtx);
        return toByteArray(env, "", 0);
    }
    const int maxNew = std::min<int>(maxTokens, session->nCtx - n);

    // --- Reset KV cache (previous call left its tokens behind) -
    llama_memory_clear(llama_get_memory(ctx), true);

    // --- Decode prompt in n_batch-sized chunks ---------------
    const auto t0 = std::chrono::steady_clock::now();

    for (int i = 0; i < n; i += session->nBatch) {
        const int chunk = std::min(session->nBatch, n - i);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, chunk);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("Prompt decode failed");
            return toByteArray(env, "", 0);
        }
    }

    const auto t1 = std::chrono::steady_clock::now();

    // --- Sampler ---------------------------------------------
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(
            sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.1f, 0.0f, 0.0f));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_min_p(0.05f, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    // --- Streaming callback setup ----------------------------
    jmethodID onToken = nullptr;
    if (listener) {
        jclass cls = env->GetObjectClass(listener);
        onToken = env->GetMethodID(cls, "onToken", "([B)Z");
        env->DeleteLocalRef(cls);
    }

    std::string output;
    std::string pending;
    output.reserve(static_cast<size_t>(maxNew) * 4);

    // Sends complete UTF-8 text to Kotlin. Returns false if caller wants to stop.
    auto emit = [&]() -> bool {
        const size_t len = validUtf8Prefix(pending);
        if (len == 0)
            return true;

        output.append(pending, 0, len);
        bool keepGoing = true;

        if (onToken) {
            jbyteArray arr = toByteArray(env, pending.data(), len);
            keepGoing = env->CallBooleanMethod(listener, onToken, arr) == JNI_TRUE;
            env->DeleteLocalRef(arr);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                keepGoing = false;
            }
        }
        pending.erase(0, len);
        return keepGoing;
    };

    // --- Generation loop -------------------------------------
    int generated = 0;
    const auto g0 = std::chrono::steady_clock::now();

    while (generated < maxNew) {
        llama_token token = llama_sampler_sample(sampler, ctx, -1);

        if (llama_vocab_is_eog(vocab, token))
            break;

        char piece[256];
        const int32_t len = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
        if (len > 0)
            pending.append(piece, static_cast<size_t>(len));

        ++generated;
        if (!emit())
            break;
        if (generated >= maxNew)
            break; // skip a pointless final decode

        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("Generation decode failed");
            break;
        }
    }

    llama_sampler_free(sampler);

    const auto g1 = std::chrono::steady_clock::now();
    const double promptSec = std::chrono::duration<double>(t1 - t0).count();
    const double genSec = std::chrono::duration<double>(g1 - g0).count();

    LOGI("Prompt: %d tokens in %.2fs (%.1f t/s) | Generated: %d tokens in %.2fs (%.1f t/s)", n,
         promptSec, promptSec > 0 ? n / promptSec : 0.0, generated, genSec,
         genSec > 0 ? generated / genSec : 0.0);

    return toByteArray(env, output.data(), output.size());
}

// ============================================================
// Free
// ============================================================

extern "C" JNIEXPORT void JNICALL Java_ai_sudo_llama_LlamaBridge_freeModel(JNIEnv* /* env */,
                                                                           jobject /* thiz */,
                                                                           jlong sessionPtr) {

    auto* session = reinterpret_cast<LlamaSession*>(sessionPtr);
    if (!session)
        return;

    if (session->ctx)
        llama_free(session->ctx);
    if (session->model)
        llama_model_free(session->model);
    delete session;

    LOGI("Llama session freed");
}