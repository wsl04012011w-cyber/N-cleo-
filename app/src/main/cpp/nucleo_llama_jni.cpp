#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <type_traits>
#include <vector>

#include "llama.h"

#define LOG_TAG "NucleoLlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

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
        llama_backend_init();
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
    return std::clamp(cores / 2, 2, 4);
}

int recommended_prefill_threads() {
    int cores = static_cast<int>(std::thread::hardware_concurrency());
    if (cores <= 0) return 4;
    return std::clamp(cores - 2, 2, 6);
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

void emit_bytes_to_callback(JNIEnv * env, jobject callback, jmethodID on_token_method, const std::string & chunk) {
    if (chunk.empty()) return;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(chunk.size()));
    if (arr == nullptr) return;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(chunk.size()), reinterpret_cast<const jbyte *>(chunk.data()));
    env->CallVoidMethod(callback, on_token_method, arr);
    env->DeleteLocalRef(arr);
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

JNIEXPORT jboolean JNICALL
Java_com_example_engine_LlamaCppNativeBridge_nativeLoadModel(
    JNIEnv * env,
    jobject /* thiz */,
    jstring jmodel_path,
    jint n_ctx_requested
) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    ensure_backend_init();

    const std::string model_path = jstring_to_std(env, jmodel_path);
    if (model_path.empty()) return JNI_FALSE;

    const int32_t target_ctx = std::clamp(static_cast<int32_t>(n_ctx_requested), 512, 8192);
    if (g_model != nullptr && g_ctx != nullptr &&
        g_loaded_model_path == model_path && g_loaded_ctx_size == target_ctx) {
        return JNI_TRUE;
    }

    unload_locked();

    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap = true;
    mparams.use_mlock = false;
    mparams.n_gpu_layers = 99; // usa GPU se Vulkan estiver compilado; caso contrário CPU otimizado

    g_model = llama_model_load_from_file(model_path.c_str(), mparams);
    if (g_model == nullptr) {
        LOGE("Falha ao carregar modelo GGUF: %s", model_path.c_str());
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
        LOGE("Falha ao criar contexto llama.cpp");
        unload_locked();
        return JNI_FALSE;
    }

    g_loaded_model_path = model_path;
    g_loaded_ctx_size = target_ctx;
    LOGI("Modelo GGUF carregado com sucesso (%s), n_ctx=%d, threads=%d/%d",
         model_path.c_str(), target_ctx, cparams.n_threads, cparams.n_threads_batch);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_engine_LlamaCppNativeBridge_nativeUnloadModel(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    unload_locked();
}

JNIEXPORT void JNICALL
Java_com_example_engine_LlamaCppNativeBridge_nativeCancel(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    g_cancel_requested.store(true);
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
    if (formatted_len > 0) {
        prompt_text.assign(formatted.data(), static_cast<size_t>(formatted_len));
    } else {
        // Fallback ChatML caso o GGUF não tenha metadado de chat_template
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
        LOGE("Falha ao calcular tokens do prompt");
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

    // Prefill em chunks de até 512 tokens (n_batch)
    const int32_t chunk_size = 512;
    for (int32_t offset = 0; offset < n_prompt_tokens; offset += chunk_size) {
        if (g_cancel_requested.load()) return 0;
        const int32_t count = std::min(chunk_size, n_prompt_tokens - offset);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + offset, count);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Erro em llama_decode durante prefill");
            return -4;
        }
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, repeat_penalty, 0.0f, 0.0f));
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
    std::string pending_utf8;
    std::vector<char> piece_buf(256);

    for (int32_t step = 0; step < safe_max_tokens; ++step) {
        if (g_cancel_requested.load()) break;

        llama_token new_token = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token)) {
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
                emit_bytes_to_callback(env, callback, on_token_method, pending_utf8.substr(0, valid_len));
                pending_utf8.erase(0, valid_len);
            }
        }

        ++generated_count;

        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, next_batch) != 0) {
            LOGE("Erro em llama_decode durante geração");
            break;
        }
    }

    if (!pending_utf8.empty()) {
        emit_bytes_to_callback(env, callback, on_token_method, pending_utf8);
    }

    llama_sampler_free(smpl);
    return generated_count;
}

} // extern "C"
