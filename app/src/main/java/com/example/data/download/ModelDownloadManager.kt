package com.example.data.download

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.util.NucleoLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    ERROR
}

data class ModelDownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val progress: Float = 0f,
    val speedBytesPerSec: Long = 0L,
    val errorMessage: String? = null
)

class ModelDownloadManager(context: Context) {

    private val appContext = context.applicationContext

    private val modelsDir: File = File(appContext.filesDir, "models").apply {
        if (!exists()) mkdirs()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    fun startDownload(
        modelId: String,
        url: String,
        onCompleted: suspend (localPath: String, totalBytes: Long) -> Unit
    ) {
        if (activeJobs[modelId]?.isActive == true) return

        val safeName = modelId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val partFile = File(modelsDir, "$safeName.gguf.part")
        val finalFile = File(modelsDir, "$safeName.gguf")

        NucleoLogger.i("Download", "Iniciando download: modelId=$modelId, url=$url")

        updateState(
            modelId,
            ModelDownloadState(
                status = DownloadStatus.DOWNLOADING,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                progress = 0f
            )
        )

        val job = scope.launch {
            try {
                if (partFile.exists()) partFile.delete()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NucleoChat-Android/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}: falha ao baixar arquivo GGUF")
                    }
                    val body = response.body ?: throw IllegalStateException("Resposta vazia do servidor")
                    val totalBytes = body.contentLength()
                    NucleoLogger.i("Download", "Resposta HTTP ${response.code} para $modelId, contentLength=$totalBytes")
                    val buffer = ByteArray(32 * 1024)
                    var downloaded = 0L
                    var lastEmitTime = System.currentTimeMillis()
                    var bytesSinceLastEmit = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            while (isActive) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                bytesSinceLastEmit += read

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastEmitTime
                                if (elapsed >= 180L) {
                                    val speed = if (elapsed > 0) (bytesSinceLastEmit * 1000L) / elapsed else 0L
                                    val progress = if (totalBytes > 0L) {
                                        (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        -1f
                                    }
                                    updateState(
                                        modelId,
                                        ModelDownloadState(
                                            status = DownloadStatus.DOWNLOADING,
                                            bytesDownloaded = downloaded,
                                            totalBytes = totalBytes,
                                            progress = progress,
                                            speedBytesPerSec = speed
                                        )
                                    )
                                    lastEmitTime = now
                                    bytesSinceLastEmit = 0L
                                }
                            }
                        }
                    }

                    if (!isActive) {
                        throw CancellationException("Download cancelado")
                    }

                    if (!hasGgufMagicHeader(partFile)) {
                        throw IllegalStateException("O arquivo baixado não possui cabeçalho GGUF válido.")
                    }

                    if (finalFile.exists()) finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        partFile.copyTo(finalFile, overwrite = true)
                        partFile.delete()
                    }

                    val finalSize = finalFile.length()
                    NucleoLogger.i("Download", "Download concluído: $modelId -> ${finalFile.absolutePath} ($finalSize bytes)")
                    onCompleted(finalFile.absolutePath, finalSize)
                    updateState(
                        modelId,
                        ModelDownloadState(
                            status = DownloadStatus.COMPLETED,
                            bytesDownloaded = finalSize,
                            totalBytes = finalSize,
                            progress = 1f
                        )
                    )
                }
            } catch (_: CancellationException) {
                if (partFile.exists()) partFile.delete()
                NucleoLogger.i("Download", "Download cancelado: $modelId")
                removeState(modelId)
            } catch (t: Throwable) {
                if (partFile.exists()) partFile.delete()
                NucleoLogger.e("Download", "Erro no download de $modelId", t)
                updateState(
                    modelId,
                    ModelDownloadState(
                        status = DownloadStatus.ERROR,
                        errorMessage = t.message ?: "Erro de rede ao baixar GGUF"
                    )
                )
            } finally {
                activeJobs.remove(modelId)
            }
        }

        activeJobs[modelId] = job
    }

    fun importLocalGguf(
        modelId: String,
        sourceUri: Uri,
        onCompleted: suspend (localPath: String, totalBytes: Long) -> Unit,
        onError: suspend (String) -> Unit
    ) {
        if (activeJobs[modelId]?.isActive == true) return

        val safeName = modelId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val partFile = File(modelsDir, "$safeName.gguf.part")
        val finalFile = File(modelsDir, "$safeName.gguf")

        NucleoLogger.i("ModelImport", "Iniciando importação local: modelId=$modelId, uri=$sourceUri")

        val totalBytes = queryUriSize(sourceUri)
        updateState(
            modelId,
            ModelDownloadState(
                status = DownloadStatus.DOWNLOADING,
                bytesDownloaded = 0L,
                totalBytes = totalBytes,
                progress = if (totalBytes > 0L) 0f else -1f
            )
        )

        val job = scope.launch {
            try {
                if (partFile.exists()) partFile.delete()

                val stream = appContext.contentResolver.openInputStream(sourceUri)
                    ?: throw IllegalStateException("Não foi possível abrir o arquivo selecionado.")

                stream.use { input ->
                    val magic = ByteArray(4)
                    val headRead = input.read(magic)
                    if (headRead < 4 ||
                        magic[0] != 'G'.code.toByte() ||
                        magic[1] != 'G'.code.toByte() ||
                        magic[2] != 'U'.code.toByte() ||
                        magic[3] != 'F'.code.toByte()
                    ) {
                        throw IllegalStateException("Arquivo inválido: assinatura GGUF não encontrada no cabeçalho.")
                    }

                    FileOutputStream(partFile).use { output ->
                        output.write(magic, 0, headRead)
                        var copied = headRead.toLong()
                        var lastEmitTime = System.currentTimeMillis()
                        var bytesSinceLastEmit = headRead.toLong()
                        val buffer = ByteArray(64 * 1024)

                        while (isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            bytesSinceLastEmit += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastEmitTime
                            if (elapsed >= 160L) {
                                val speed = if (elapsed > 0) (bytesSinceLastEmit * 1000L) / elapsed else 0L
                                val progress = if (totalBytes > 0L) {
                                    (copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    -1f
                                }
                                updateState(
                                    modelId,
                                    ModelDownloadState(
                                        status = DownloadStatus.DOWNLOADING,
                                        bytesDownloaded = copied,
                                        totalBytes = totalBytes,
                                        progress = progress,
                                        speedBytesPerSec = speed
                                    )
                                )
                                lastEmitTime = now
                                bytesSinceLastEmit = 0L
                            }
                        }
                    }
                }

                if (!isActive) {
                    throw CancellationException("Importação cancelada")
                }

                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                val finalSize = finalFile.length()
                NucleoLogger.i("ModelImport", "Importação concluída: $modelId -> ${finalFile.absolutePath} ($finalSize bytes)")
                onCompleted(finalFile.absolutePath, finalSize)
                updateState(
                    modelId,
                    ModelDownloadState(
                        status = DownloadStatus.COMPLETED,
                        bytesDownloaded = finalSize,
                        totalBytes = finalSize,
                        progress = 1f
                    )
                )
            } catch (_: CancellationException) {
                if (partFile.exists()) partFile.delete()
                NucleoLogger.i("ModelImport", "Importação cancelada: $modelId")
                removeState(modelId)
            } catch (t: Throwable) {
                if (partFile.exists()) partFile.delete()
                val msg = t.message ?: "Erro ao importar arquivo GGUF"
                NucleoLogger.e("ModelImport", "Falha na importação de $modelId", t)
                removeState(modelId)
                onError(msg)
            } finally {
                activeJobs.remove(modelId)
            }
        }

        activeJobs[modelId] = job
    }

    fun cancelDownload(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        val safeName = modelId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        File(modelsDir, "$safeName.gguf.part").delete()
        removeState(modelId)
    }

    fun clearDownloadState(modelId: String) {
        removeState(modelId)
    }

    fun deleteLocalFile(localFilePath: String?) {
        if (!localFilePath.isNullOrBlank()) {
            runCatching { File(localFilePath).delete() }
        }
    }

    private fun queryUriSize(uri: Uri): Long {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) {
                    cursor.getLong(idx)
                } else {
                    -1L
                }
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun hasGgufMagicHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        return runCatching {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                val read = fis.read(magic)
                read == 4 &&
                    magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    }

    private fun updateState(modelId: String, state: ModelDownloadState) {
        _downloadStates.update { current -> current + (modelId to state) }
    }

    private fun removeState(modelId: String) {
        _downloadStates.update { current -> current - modelId }
    }
}
