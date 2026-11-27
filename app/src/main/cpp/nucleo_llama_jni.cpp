#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <deque>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>
#include <type_traits>
#include <utility>
#include <vector>

#include "llama.h"

#define LOG_TAG "NucleoLlamaJNI"

namespace {

std::mutex g_log_mutex;
std::deque<std::string> g_log_queue;
std::string g_pending_llama_line;
constexpr size_t kMaxBufferedLogLines = 500;

void push_log_line_locked(const std::string & line) {
    if (line.empty()) return;
    if (g_log_queue.size() >= kMaxBufferedLogLines) {
        g_log_queue.pop_front();
    }
    g_log_queue.push_back(line);
}

void log_printf(int prio, const char * fmt, ...) {
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    __android_log_print(prio, LOG_TAG, "%s", buf);
    std::lock_guard<std::mutex> lock(g_log_mutex);
    push_log_line_locked(std::string("[JNI] ") + buf);
}

#define LOGI(...) log_printf(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGE(...) log_printf(ANDROID_LOG_ERROR, __VA_ARGS__)

void nucleo_llama_log_callback(ggml_log_level level, const char * text, void * /* user_data */) {
    if (text == nullptr) return;
    const int prio = (level == GGML_LOG_LEVEL_ERROR)
        ? ANDROID_LOG_ERROR
        : (level == GGML_LOG_LEVEL_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO);

    __android_log_print(prio, LOG_TAG, "%s", text);

    std::lock_guard<std::mutex> lock(g_log_mutex);
    g_pending_llama_line += text;
    size_t pos = 0;
    while ((pos = g_pending_llama_line.find('\n')) != std::string::npos) {
        std::string line = g_pending_llama_line.substr(0, pos);
        g_pending_llama_line.erase(0, pos + 1);
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        if (!line.empty()) {
            push_log_line_locked(line);
        }
    }
    if (g_pending_llama_line.size() > 1024) {
        push_log_line_locked(g_pending_llama_line);
        g_pending_llama_line.clear();
    }
}

std::mutex g_engine_mutex;
llama_model * g_model = nullptr;
llama_context * g_ctx = nullptr;
std::string g_loaded_model_path;
int32_t g_loaded_ctx_size = 0;
std::atomic<bool> g_cancel_requested{false};
std::atomic<bool> g_backend_initialized{false};

void ensure_backend_init() {
    bool expected = false;
    if (g_backend_initialized.compare_exchange_strong(expected, true)) {
        llama_log_set(nucleo_llama_log_callback, nullptr);
        llama_backend_init();
        LOGI("llama_backend_init concluído; gpu_offload=%d", llama_supports_gpu_offload() ? 1 : 0);
    }
}

void unload_locked() {
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_loaded_model_path.clear();
    g_loaded_ctx_size = 0;
}

int recommended_decode_threads() {
    int cores = static_cast<int>(std::thread::hardware_concurrency());
    if (cores <= 0) return 4;
    // Em SoCs Android big.LITTLE, limitar a 4 threads evita contenção em núcleos LITTLE lentos.
    return std::clamp(cores / 2, 2, 4);
}

int recommended_prefill_threads() {
    int cores = static_cast<int>(std::thread::hardware_concurrency());
    if (cores <= 0) return 4;
    return std::clamp(cores / 2, 2, 4);
}

bool verify_gguf_file(const std::string & path, uint64_t & out_size_bytes) {
    out_size_bytes = 0;
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Não foi possível abrir arquivo GGUF: %s", path.c_str());
        return false;
    }
    const std::ifstream::pos_type end_pos = file.tellg();
    if (end_pos <= 16) {
        LOGE("Arquivo GGUF pequeno demais ou inválido (%lld bytes): %s",
             static_cast<long long>(end_pos), path.c_str());
        return false;
    }
    out_size_bytes = static_cast<uint64_t>(end_pos);
    file.seekg(0, std::ios::beg);
    char magic[4] = {0, 0, 0, 0};
    file.read(magic, 4);
    if (!file || magic[0] != 'G' || magic[1] != 'G' || magic[2] != 'U' || magic[3] != 'F') {
        LOGE("Assinatura GGUF inválida (%02X %02X %02X %02X) em %s",
             static_cast<unsigned char>(magic[0]),
             static_cast<unsigned char>(magic[1]),
             static_cast<unsigned char>(magic[2]),
             static_cast<unsigned char>(magic[3]),
             path.c_str());
        return false;
    }
    return true;
}

// Compatibilidade automática com assinaturas de llama_chat_apply_template (6 ou 7 argumentos)
template <typename Fn>
int32_t invoke_chat_template(
    Fn fn,
    const llama_model * model,
    const char * tmpl,
    const llama_chat_message * chat,
    size_t n_msg,
    bool add_ass,
    char * buf,
    int32_t length
) {
    if constexpr (std::is_invocable_r_v<int32_t, Fn, const char *, const llama_chat_message *, size_t, bool, char *, int32_t>) {
        return fn(tmpl, chat, n_msg, add_ass, buf, length);
    } else {
        return fn(model, tmpl, chat, n_msg, add_ass, buf, length);
    }
}

template <typename T, typename = void>
struct has_use_mmap : std::false_type {};

template <typename T>
struct has_use_mmap<T, std::void_t<decltype(std::declval<T &>().use_mmap)>> : std::true_type {};

template <typename T, typename = void>
struct has_use_mlock : std::false_type {};

template <typename T>
struct has_use_mlock<T, std::void_t<decltype(std::declval<T &>().use_mlock)>> : std::true_type {};

template <typename ModelParams>
void configure_model_load_params(ModelParams & mparams) {
    if constexpr (has_use_mmap<ModelParams>::value) {
        mparams.use_mmap = true;
    }
    if constexpr (has_use_mlock<ModelParams>::value) {
        mparams.use_mlock = false;
    }
}

// Compatibilidade automática com assinaturas de llama_sampler_init_penalties (4 ou 5 argumentos com n_vocab)
template <typename Fn>
llama_sampler * invoke_penalties_sampler(
    Fn fn,
    int32_t n_vocab,
    int32_t penalty_last_n,
    float penalty_repeat,
    float penalty_freq,
    float penalty_present
) {
    if constexpr (std::is_invocable_r_v<llama_sampler *, Fn, int32_t, int32_t, float, float, float>) {
        return fn(n_vocab, penalty_last_n, penalty_repeat, penalty_freq, penalty_present);
    } else {
        return fn(penalty_last_n, penalty_repeat, penalty_freq, penalty_present);
    }
}

// Retorna quantos bytes iniciais de `s` formam sequências UTF-8 completas
size_t valid_utf8_prefix_len(const std::string & s) {
    size_t i = 0;
    const size_t n = s.size();
    while (i < n) {
        unsigned char c = static_cast<unsigned char>(s[i]);
        size_t len = 1;
        if ((c & 0x80) == 0x00) {
            len = 1;
        } else if ((c & 0xE0) == 0xC0) {
            len = 2;
        } else if ((c & 0xF0) == 0xE0) {
            len = 3;
        } else if ((c & 0xF8) == 0xF0) {
            len = 4;
        } else {
            // Byte inválido isolado -> avança 1 para não travar stream
            ++i;
            continue;
        }
        if (i + len > n) {
            break; // sequência incompleta no final do buffer
        }
        i += len;
    }
    return i;
}

bool emit_bytes_to_callback(JNIEnv * env, jobject callback, jmethodID on_token_method, const std::string & chunk) {
    if (chunk.empty()) return true;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(chunk.size()));
    if (arr == nullptr) {
        LOGE("Falha ao alocar jbyteArray de %zu bytes", chunk.size());
        return false;
    }
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(chunk.size()), reinterpret_cast<const jbyte *>(chunk.data()));
    env->CallVoidMethod(callback, on_token_method, arr);
    env->DeleteLocalRef(arr);
    if (env->ExceptionCheck()) {
        LOGE("Exceção Java lançada dentro de onTokenBytes");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return false;
    }
    return true;
}

std::string jstring_to_std(JNIEnv * env, jstring jstr) {
    if (jstr == nullptr) return {};
    const char * utf = env->GetStringUTFChars(jstr, nullptr);
    if (utf == nullptr) return {};
    std::string out(utf);
    env->ReleaseStringUTFChars(jstr, utf);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_engine_LlamaCppNativeBridge_nativeSystemInfo(
    JNIEnv * env,
    jobject /* thiz */
) {
    ensure_backend_init();
    const char * info = llama_print_system_info();
    return env->NewStringUTF(info != nullptr ? info : "llama.cpp arm64");
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_engine_LlamaCppNativeBridge_nativeDrainLogs(
    JNIEnv * env,
    jobject /* thiz */
) {
    std::deque<std::string> drained;
    {
        std::lock_guard<std::mutex> lock(g_log_mutex);
        if (!g_pending_llama_line.empty()) {
            push_log_line_locked(g_pending_llama_line);
            g_pending_llama_line.clear();
        }
        drained.swap(g_log_queue);
    }

    jclass str_cls = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(drained.size()), str_cls, nullptr);
    if (result == nullptr) {
        env->DeleteLocalRef(str_cls);
        return nullptr;
    }
    jsize idx = 0;
    for (const std::string & line : drained) {
        jstring jline = env->NewStringUTF(line.c_str());
        if (jline != nullptr) {
            env->SetObjectArrayElement(result, idx, jline);
            env->DeleteLocalRef(jline);
        }
        ++idx;
    }
    env->DeleteLocalRef(str_cls);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_LlamaCppNativeBridge_nativeLoadModel(
    JNIEnv * env,
    jobject /* thiz */,
    jstring jmodel_path,
    jint n_ctx_requested
) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    ensure_backend_init();
    g_cancel_requested.store(false);

    const std::string model_path = jstring_to_std(env, jmodel_path);
    if (model_path.empty()) {
        LOGE("nativeLoadModel: caminho vazio");
        return JNI_FALSE;
    }

    const int32_t target_ctx = std::clamp(static_cast<int32_t>(n_ctx_requested), 512, 8192);
    if (g_model != nullptr && g_ctx != nullptr &&
        g_loaded_model_path == model_path && g_loaded_ctx_size == target_ctx) {
        LOGI("nativeLoadModel: reutilizando modelo já em memória (%s, n_ctx=%d)",
             model_path.c_str(), target_ctx);
        return JNI_TRUE;
    }

    uint64_t file_size_bytes = 0;
    if (!verify_gguf_file(model_path, file_size_bytes)) {
        return JNI_FALSE;
    }

    unload_locked();

    const auto load_t0 = std::chrono::steady_clock::now();
    LOGI("nativeLoadModel: iniciando carga de %s (%.1f MB), n_ctx=%d",
         model_path.c_str(),
         static_cast<double>(file_size_bytes) / (1024.0 * 1024.0),
         target_ctx);

    llama_model_params mparams = llama_model_default_params();
    configure_model_load_params(mparams);
    mparams.n_gpu_layers = llama_supports_gpu_offload() ? 99 : 0;

    g_model = llama_model_load_from_file(model_path.c_str(), mparams);
    if (g_model == nullptr) {
        LOGE("Falha ao carregar modelo GGUF: %s", model_path.c_str());
        return JNI_FALSE;
    }

    const auto load_t1 = std::chrono::steady_clock::now();
    const auto load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(load_t1 - load_t0).count();
    LOGI("llama_model_load_from_file concluído em %lld ms", static_cast<long long>(load_ms));

    if (g_cancel_requested.load()) {
        LOGI("Carga abortada por cancelamento do usuário");
        unload_locked();
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(target_ctx);
    cparams.n_batch = 512;
    cparams.n_ubatch = 256;
    cparams.n_threads = recommended_decode_threads();
    cparams.n_threads_batch = recommended_prefill_threads();

    g_ctx = llama_init_from_model(g_model, cparams);
    if (g_ctx == nullptr) {
        LOGE("Falha ao criar contexto llama.cpp (n_ctx=%d)", target_ctx);
        unload_locked();
        return JNI_FALSE;
    }

    const auto ctx_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - load_t1
    ).count();

    g_loaded_model_path = model_path;
    g_loaded_ctx_size = target_ctx;
    LOGI("Contexto criado em %lld ms (n_ctx=%d, threads_decode=%d, threads_prefill=%d, hw_cores=%u)",
         static_cast<long long>(ctx_ms),
         target_ctx,
         cparams.n_threads,
         cparams.n_threads_batch,
         std::thread::hardware_concurrency());
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_engine_LlamaCppNativeBridge_nativeUnloadModel(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    LOGI("nativeUnloadModel chamado");
    unload_locked();
}

JNIEXPORT void JNICALL
Java_com_example_engine_LlamaCppNativeBridge_nativeCancel(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    g_cancel_requested.store(true);
    LOGI("nativeCancel sinalizado");
}

JNIEXPORT jint JNICALL
Java_com_example_engine_LlamaCppNativeBridge_nativeGenerateStream(
    JNIEnv * env,
    jobject /* thiz */,
    jobjectArray jroles,
    jobjectArray jcontents,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat repeat_penalty,
    jint max_tokens,
    jobject callback
) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    g_cancel_requested.store(false);

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("nativeGenerateStream chamado sem modelo carregado");
        return -1;
    }

    if (callback == nullptr) {
        LOGE("Callback nulo em nativeGenerateStream");
        return -2;
    }

    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_token_method = env->GetMethodID(cb_cls, "onTokenBytes", "([B)V");
    env->DeleteLocalRef(cb_cls);
    if (on_token_method == nullptr) {
        LOGE("Método onTokenBytes([B)V não encontrado no callback");
        return -2;
    }

    // Limpa KV cache para nova inferência stateless a partir do histórico completo
    llama_memory_clear(llama_get_memory(g_ctx), true);

    const jsize msg_count = env->GetArrayLength(jroles);
    std::vector<std::string> role_storage;
    std::vector<std::string> content_storage;
    role_storage.reserve(msg_count);
    content_storage.reserve(msg_count);

    std::vector<llama_chat_message> chat_msgs;
    chat_msgs.reserve(msg_count);

    for (jsize i = 0; i < msg_count; ++i) {
        auto jrole = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
        auto jcontent = static_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
        role_storage.push_back(jstring_to_std(env, jrole));
        content_storage.push_back(jstring_to_std(env, jcontent));
        env->DeleteLocalRef(jrole);
        env->DeleteLocalRef(jcontent);

        chat_msgs.push_back(llama_chat_message{
            role_storage.back().c_str(),
            content_storage.back().c_str()
        });
    }

    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    std::vector<char> formatted(4096);
    int32_t formatted_len = invoke_chat_template(
        llama_chat_apply_template,
        g_model,
        tmpl,
        chat_msgs.data(),
        chat_msgs.size(),
        true,
        formatted.data(),
        static_cast<int32_t>(formatted.size())
    );

    if (formatted_len > static_cast<int32_t>(formatted.size())) {
        formatted.resize(static_cast<size_t>(formatted_len) + 1);
        formatted_len = invoke_chat_template(
            llama_chat_apply_template,
            g_model,
            tmpl,
            chat_msgs.data(),
            chat_msgs.size(),
            true,
            formatted.data(),
            static_cast<int32_t>(formatted.size())
        );
    }

    std::string prompt_text;
    const bool used_model_template = (formatted_len > 0);
    if (used_model_template) {
        prompt_text.assign(formatted.data(), static_cast<size_t>(formatted_len));
    } else {
        // Fallback ChatML caso o GGUF não tenha metadado de chat_template ou template Jinja complexo
        for (size_t i = 0; i < chat_msgs.size(); ++i) {
            prompt_text += "<|im_start|>";
            prompt_text += chat_msgs[i].role;
            prompt_text += "\n";
            prompt_text += chat_msgs[i].content;
            prompt_text += "<|im_end|>\n";
        }
        prompt_text += "<|im_start|>assistant\n";
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(g_ctx));

    int32_t n_prompt_tokens = -llama_tokenize(
        vocab,
        prompt_text.c_str(),
        static_cast<int32_t>(prompt_text.size()),
        nullptr,
        0,
        true,
        true
    );
    if (n_prompt_tokens <= 0) {
        LOGE("Falha ao calcular tokens do prompt (n_prompt_tokens=%d)", n_prompt_tokens);
        return -3;
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt_tokens));
    if (llama_tokenize(
            vocab,
            prompt_text.c_str(),
            static_cast<int32_t>(prompt_text.size()),
            prompt_tokens.data(),
            n_prompt_tokens,
            true,
            true
        ) < 0) {
        LOGE("Falha na tokenização do prompt");
        return -3;
    }

    if (n_prompt_tokens >= n_ctx - 40) {
        // Trunca início preservando o final mais recente para caber na janela de contexto
        const int32_t keep = std::max(64, n_ctx / 2);
        prompt_tokens.erase(prompt_tokens.begin(), prompt_tokens.end() - std::min<int32_t>(keep, n_prompt_tokens));
        n_prompt_tokens = static_cast<int32_t>(prompt_tokens.size());
    }

    LOGI("Prompt pronto: msgs=%d, template=%s, chars=%zu, tokens=%d, n_ctx=%d",
         static_cast<int>(msg_count),
         used_model_template ? "GGUF" : "ChatML-fallback",
         prompt_text.size(),
         n_prompt_tokens,
         n_ctx);

    // Prefill em chunks de até 256 tokens para manter cancelamento responsivo no mobile
    const auto prefill_t0 = std::chrono::steady_clock::now();
    const int32_t chunk_size = 256;
    for (int32_t offset = 0; offset < n_prompt_tokens; offset += chunk_size) {
        if (g_cancel_requested.load()) {
            LOGI("Prefill cancelado em offset=%d/%d", offset, n_prompt_tokens);
            return 0;
        }
        const int32_t count = std::min(chunk_size, n_prompt_tokens - offset);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + offset, count);
        const int decode_rc = llama_decode(g_ctx, batch);
        if (decode_rc != 0) {
            LOGE("Erro em llama_decode durante prefill (rc=%d, offset=%d, count=%d)", decode_rc, offset, count);
            return -4;
        }
    }
    const auto prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - prefill_t0
    ).count();
    LOGI("Prefill concluído em %lld ms (%d tokens)", static_cast<long long>(prefill_ms), n_prompt_tokens);

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    const int32_t n_vocab = llama_vocab_n_tokens(vocab);
    llama_sampler_chain_add(
        smpl,
        invoke_penalties_sampler(
            llama_sampler_init_penalties,
            n_vocab,
            64,
            repeat_penalty,
            0.0f,
            0.0f
        )
    );
    if (temperature <= 0.01f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(std::max(1, static_cast<int>(top_k))));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(std::clamp(top_p, 0.05f, 1.0f), 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    const int32_t available_gen = std::max(32, n_ctx - n_prompt_tokens - 4);
    const int32_t safe_max_tokens = std::clamp(static_cast<int32_t>(max_tokens), 32, available_gen);
    int32_t generated_count = 0;
    int32_t emitted_callbacks = 0;
    std::string pending_utf8;
    std::vector<char> piece_buf(256);
    const auto gen_t0 = std::chrono::steady_clock::now();

    for (int32_t step = 0; step < safe_max_tokens; ++step) {
        if (g_cancel_requested.load()) {
            LOGI("Geração cancelada no passo %d", step);
            break;
        }

        llama_token new_token = llama_sampler_sample(smpl, g_ctx, -1);
        const bool is_eog = llama_vocab_is_eog(vocab, new_token);

        if (step < 3) {
            LOGI("Amostra step=%d token=%d is_eog=%d", step, static_cast<int>(new_token), is_eog ? 1 : 0);
        }

        if (is_eog) {
            LOGI("Token EOG (%d) atingido no passo %d", static_cast<int>(new_token), step);
            break;
        }

        int32_t n_piece = llama_token_to_piece(
            vocab,
            new_token,
            piece_buf.data(),
            static_cast<int32_t>(piece_buf.size()),
            0,
            false
        );
        if (n_piece < 0) {
            piece_buf.resize(static_cast<size_t>(-n_piece));
            n_piece = llama_token_to_piece(
                vocab,
                new_token,
                piece_buf.data(),
                static_cast<int32_t>(piece_buf.size()),
                0,
                false
            );
        }

        if (n_piece > 0) {
            pending_utf8.append(piece_buf.data(), static_cast<size_t>(n_piece));
            const size_t valid_len = valid_utf8_prefix_len(pending_utf8);
            if (valid_len > 0) {
                if (!emit_bytes_to_callback(env, callback, on_token_method, pending_utf8.substr(0, valid_len))) {
                    break;
                }
                ++emitted_callbacks;
                pending_utf8.erase(0, valid_len);
            }
        }

        ++generated_count;

        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        const int step_rc = llama_decode(g_ctx, next_batch);
        if (step_rc != 0) {
            LOGE("Erro em llama_decode durante geração (rc=%d, step=%d)", step_rc, step);
            break;
        }
    }

    if (!pending_utf8.empty()) {
        if (emit_bytes_to_callback(env, callback, on_token_method, pending_utf8)) {
            ++emitted_callbacks;
        }
    }

    const auto gen_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - gen_t0
    ).count();
    LOGI("Geração finalizada: tokens=%d, callbacks=%d, tempo=%lld ms",
         generated_count, emitted_callbacks, static_cast<long long>(gen_ms));

    llama_sampler_free(smpl);
    return generated_count;
}

} // extern "C"
