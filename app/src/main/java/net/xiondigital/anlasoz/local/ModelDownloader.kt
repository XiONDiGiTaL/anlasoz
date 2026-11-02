package net.xiondigital.anlasoz.local

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface DownloadState {
    data class Running(val received: Long, val total: Long?) : DownloadState {
        val fraction: Float?
            get() = total?.takeIf { it > 0 }?.let { (received.toDouble() / it.toDouble()).toFloat() }
    }

    data class Done(val file: LocalModelFile) : DownloadState

    data class Failed(val message: String) : DownloadState
}

/**
 * Downloads one model at a time per source, straight into private storage.
 *
 * The file is hashed while it streams: the download only becomes a usable model
 * when both the byte count and — where the publisher states one — the SHA-256
 * match, so an interrupted or substituted transfer can never be loaded. A
 * network download of 2 GB survives neither a process death nor an app swipe,
 * which is why Settings warns to keep the app open.
 */
class ModelDownloader(private val store: ModelStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.MINUTES)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    fun start(source: LocalModelSource) {
        if (jobs[source.id]?.isActive == true) return
        jobs[source.id] = scope.launch { download(source) }
    }

    fun cancel(sourceId: String) {
        jobs[sourceId]?.cancel()
        jobs.remove(sourceId)
        _states.value = _states.value - sourceId
        File(store.directory, "${ModelCatalog.byId(sourceId)?.fileName ?: return}.part").delete()
    }

    private suspend fun download(source: LocalModelSource) {
        val target = store.file(source.fileName)
        val partial = File(store.directory, "${source.fileName}.part")

        if (store.has(source.fileName)) {
            _states.value += source.id to DownloadState.Done(LocalModelFile(source.fileName, target))
            return
        }
        if (source.bytes != null && source.bytes > store.freeBytes()) {
            _states.value += source.id to DownloadState.Failed(
                "Not enough storage: ${ModelCatalog.formatBytes(source.bytes)} needed",
            )
            return
        }

        _states.value += source.id to DownloadState.Running(0, source.bytes)
        try {
            val response = client.newCall(
                Request.Builder().url(source.url).header("Accept", "*/*").build(),
            ).execute()
            response.use { result ->
                if (!result.isSuccessful) {
                    _states.value += source.id to DownloadState.Failed(
                        "The server answered ${result.code}",
                    )
                    return
                }
                val body = result.body ?: run {
                    _states.value += source.id to DownloadState.Failed("The download was empty")
                    return
                }
                val total = source.bytes ?: body.contentLength().takeIf { it > 0 }
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var lastPublish = 0L

                withContext(Dispatchers.IO) {
                    partial.delete()
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(1 shl 16)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                received += read
                                val now = System.currentTimeMillis()
                                if (now - lastPublish > 250 || (total != null && received >= total)) {
                                    lastPublish = now
                                    _states.value += source.id to DownloadState.Running(received, total)
                                }
                            }
                        }
                    }
                }

                if (source.bytes != null && received != source.bytes) {
                    partial.delete()
                    _states.value += source.id to DownloadState.Failed(
                        "The download was incomplete (${ModelCatalog.formatBytes(received)})",
                    )
                    return
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (source.sha256 != null && !actual.equals(source.sha256, ignoreCase = true)) {
                    partial.delete()
                    _states.value += source.id to DownloadState.Failed(
                        "The file did not match the published checksum",
                    )
                    return
                }
                if (!store.looksLikeGguf(partial)) {
                    partial.delete()
                    _states.value += source.id to DownloadState.Failed("The download is not a GGUF model")
                    return
                }

                val installed = File(store.directory, source.fileName)
                if (!partial.renameTo(installed)) {
                    partial.delete()
                    _states.value += source.id to DownloadState.Failed("The file could not be saved")
                    return
                }
                _states.value += source.id to DownloadState.Done(
                    LocalModelFile(source.fileName, installed),
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            partial.delete()
            _states.value = _states.value - source.id
            throw e
        } catch (e: Exception) {
            partial.delete()
            _states.value += source.id to DownloadState.Failed(
                e.message?.take(120) ?: "The download failed",
            )
        } finally {
            jobs.remove(source.id)
        }
    }
}
