package com.example.util

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Logger central do Núcleo Chat.
 * Grava logs detalhados (JVM + JNI/llama.cpp + crashes) em:
 * - Espelho interno privado: filesDir/logs/
 * - Pasta pública do usuário: Downloads/NucleoChat/ (via MediaStore no Android 10+)
 */
object NucleoLogger {

    private const val TAG = "NucleoChat"
    private const val SUBFOLDER_NAME = "NucleoChat"

    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NucleoLogger-IO").apply { isDaemon = true }
    }

    private val timestampFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var internalLogFile: File? = null

    @Volatile
    private var publicDownloadsUri: Uri? = null

    @Volatile
    private var publicLegacyFile: File? = null

    @Volatile
    private var sessionFileName: String = "nucleo_log.txt"

    @Volatile
    private var initialized = false

    private val pendingPublicBuffer = StringBuilder()
    private val bufferLock = Any()

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appCtx = context.applicationContext
            appContext = appCtx
            val stamp = fileDateFormat.format(Date())
            sessionFileName = "nucleo_log_$stamp.txt"

            val internalDir = File(appCtx.filesDir, "logs").apply { mkdirs() }
            internalLogFile = File(internalDir, sessionFileName)
            initialized = true
        }

        installUncaughtExceptionHandler()
        i("App", "=== Núcleo Chat iniciado ===")
        logDeviceSummary()

        ioExecutor.execute {
            ensurePublicDownloadsTarget()
            flushPendingToPublic()
        }
    }

    fun getPublicFolderDisplayPath(): String = "Downloads/$SUBFOLDER_NAME/$sessionFileName"

    fun getPublicLogUri(): Uri? = publicDownloadsUri

    fun getInternalLogFile(): File? = internalLogFile

    fun i(tag: String, message: String) {
        runCatching { Log.i("$TAG/$tag", message) }
        enqueue("INFO", tag, message)
    }

    fun d(tag: String, message: String) {
        runCatching { Log.d("$TAG/$tag", message) }
        enqueue("DEBUG", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        runCatching { Log.w("$TAG/$tag", message, throwable) }
        val full = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        enqueue("WARN", tag, full)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        runCatching { Log.e("$TAG/$tag", message, throwable) }
        val full = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        enqueue("ERROR", tag, full)
    }

    fun native(line: String) {
        val clean = line.trimEnd('\r', '\n')
        if (clean.isBlank()) return
        runCatching { Log.i("$TAG/llama.cpp", clean) }
        enqueue("NATIVE", "llama.cpp", clean)
    }

    fun flushNowBlocking(timeoutMs: Long = 1200L) {
        val future = ioExecutor.submit {
            ensurePublicDownloadsTarget()
            flushPendingToPublic()
        }
        runCatching {
            future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    private fun enqueue(level: String, tag: String, message: String) {
        val ts = timestampFormat.get()?.format(Date()) ?: "--:--:--"
        val line = "[$ts] [$level] [$tag] $message\n"

        synchronized(bufferLock) {
            pendingPublicBuffer.append(line)
        }

        ioExecutor.execute {
            runCatching {
                internalLogFile?.let { file ->
                    FileOutputStream(file, true).use { fos ->
                        fos.write(line.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            ensurePublicDownloadsTarget()
            flushPendingToPublic()
        }
    }

    private fun ensurePublicDownloadsTarget() {
        val ctx = appContext ?: return
        if (publicDownloadsUri != null || publicLegacyFile != null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sessionFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER_NAME"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    publicDownloadsUri = uri
                }
            }.onFailure { err ->
                Log.w(TAG, "Falha ao criar arquivo de log via MediaStore.Downloads: ${err.message}")
            }
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsRoot, SUBFOLDER_NAME)
                if ((targetDir.exists() || targetDir.mkdirs()) && targetDir.canWrite()) {
                    publicLegacyFile = File(targetDir, sessionFileName)
                } else {
                    val appDownloads = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    if (appDownloads != null) {
                        val fallbackDir = File(appDownloads, SUBFOLDER_NAME).apply { mkdirs() }
                        publicLegacyFile = File(fallbackDir, sessionFileName)
                    }
                }
            }
        }
    }

    private fun flushPendingToPublic() {
        val chunk: String
        synchronized(bufferLock) {
            if (pendingPublicBuffer.isEmpty()) return
            chunk = pendingPublicBuffer.toString()
            pendingPublicBuffer.setLength(0)
        }

        val ctx = appContext
        val uri = publicDownloadsUri
        if (ctx != null && uri != null) {
            val bytes = chunk.toByteArray(Charsets.UTF_8)
            val appended = runCatching {
                ctx.contentResolver.openOutputStream(uri, "wa")?.use { out ->
                    out.write(bytes)
                    out.flush()
                    true
                } ?: false
            }.getOrDefault(false)

            if (!appended) {
                // Fallback seguro para ROMs que não aceitam modo "wa" em MediaStore
                runCatching {
                    val internal = internalLogFile
                    if (internal != null && internal.exists()) {
                        ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            internal.inputStream().use { input -> input.copyTo(out) }
                            out.flush()
                        }
                    }
                }
            }
            return
        }

        val legacy = publicLegacyFile
        if (legacy != null) {
            runCatching {
                FileOutputStream(legacy, true).use { fos ->
                    fos.write(chunk.toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
            }
        }
    }

    private fun logDeviceSummary() {
        val ctx = appContext ?: return
        val memInfo = ActivityManager.MemoryInfo()
        runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.getMemoryInfo(memInfo)
        }
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
        i(
            "Device",
            "Model=${Build.MANUFACTURER} ${Build.MODEL} | Android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) | ABIs=[$abis] | Cores=${Runtime.getRuntime().availableProcessors()} | RAM=${availRamMb}MB/${totalRamMb}MB livre"
        )
    }

    private fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("Crash", "Exceção não tratada na thread '${thread.name}'", throwable)
            flushNowBlocking(1500L)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
