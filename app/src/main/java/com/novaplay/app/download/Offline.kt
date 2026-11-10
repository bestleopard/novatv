package com.novaplay.app.download

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class OfflineState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }
enum class OfflineKind { MOVIE, EPISODE }

data class OfflineItem(
    val id: String,
    val kind: OfflineKind,
    val title: String,
    val url: String,
    val poster: String?,
    val fileName: String,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val state: OfflineState = OfflineState.QUEUED,
    val error: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val speedBps: Long = 0,
) {
    val percent: Int get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
}

/**
 * Bibliothèque hors ligne : liste des films / épisodes téléchargés ou en cours.
 * Sauvegardée dans offline.json, fichiers vidéo dans Android/data/<app>/files/offline/.
 */
object Offline {
    private lateinit var app: Context
    private val _items = MutableStateFlow<List<OfflineItem>>(emptyList())
    val items: StateFlow<List<OfflineItem>> = _items

    fun init(ctx: Context) {
        app = ctx.applicationContext
        // Un téléchargement interrompu (app fermée) repart en file d'attente.
        _items.value = load().map {
            if (it.state == OfflineState.DOWNLOADING) it.copy(state = OfflineState.QUEUED, speedBps = 0) else it
        }
    }

    fun dir(): File = File(app.getExternalFilesDir(null) ?: app.filesDir, "offline").apply { mkdirs() }
    fun file(item: OfflineItem): File = File(dir(), item.fileName)
    fun get(id: String): OfflineItem? = _items.value.firstOrNull { it.id == id }
    fun hasQueued(): Boolean = _items.value.any { it.state == OfflineState.QUEUED }

    fun extensionOf(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ext else "mp4"
    }

    @Synchronized
    fun update(id: String, persist: Boolean = true, f: (OfflineItem) -> OfflineItem) {
        _items.value = _items.value.map { if (it.id == id) f(it) else it }
        if (persist) save()
    }

    @Synchronized
    private fun put(item: OfflineItem) {
        _items.value = _items.value.filterNot { it.id == item.id } + item
        save()
    }

    @Synchronized
    private fun drop(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
        save()
    }

    /** Ajoute (ou relance) un téléchargement puis démarre le service. */
    fun enqueue(ctx: Context, item: OfflineItem) {
        val existing = get(item.id)
        when {
            existing == null -> put(item)
            existing.state == OfflineState.COMPLETED -> return
            else -> update(item.id) { it.copy(state = OfflineState.QUEUED, error = null, url = item.url) }
        }
        OfflineDownloadService.start(ctx)
    }

    fun pause(id: String) = update(id) {
        if (it.state == OfflineState.COMPLETED) it else it.copy(state = OfflineState.PAUSED, speedBps = 0)
    }

    fun resume(ctx: Context, id: String) {
        update(id) { if (it.state == OfflineState.COMPLETED) it else it.copy(state = OfflineState.QUEUED, error = null) }
        OfflineDownloadService.start(ctx)
    }

    fun delete(id: String) {
        val item = get(id) ?: return
        drop(id)
        file(item).delete()
    }

    /** Remet en file d'attente ce qui était en cours (arrêt du service). */
    fun requeueActive() {
        _items.value.filter { it.state == OfflineState.DOWNLOADING }.forEach {
            update(it.id) { x -> x.copy(state = OfflineState.QUEUED, speedBps = 0) }
        }
    }

    fun usedBytes(): Long = _items.value.sumOf { it.downloadedBytes.coerceAtLeast(0) }
    fun freeBytes(): Long = dir().usableSpace

    // ---------- persistance ----------

    private val jsonFile: File get() = File(app.filesDir, "offline.json")

    private fun save() {
        val arr = JSONArray()
        _items.value.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("kind", it.kind.name).put("title", it.title)
                    .put("url", it.url).put("poster", it.poster ?: "").put("fileName", it.fileName)
                    .put("seriesId", it.seriesId ?: "").put("seriesName", it.seriesName ?: "")
                    .put("season", it.season).put("episode", it.episode)
                    .put("totalBytes", it.totalBytes).put("downloadedBytes", it.downloadedBytes)
                    .put("state", it.state.name).put("error", it.error ?: "").put("addedAt", it.addedAt)
            )
        }
        runCatching {
            val tmp = File(jsonFile.path + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(jsonFile)
        }
    }

    private fun load(): List<OfflineItem> = runCatching {
        if (!jsonFile.exists()) return emptyList()
        val arr = JSONArray(jsonFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            OfflineItem(
                id = o.getString("id"),
                kind = OfflineKind.valueOf(o.getString("kind")),
                title = o.getString("title"),
                url = o.getString("url"),
                poster = o.optString("poster").ifBlank { null },
                fileName = o.getString("fileName"),
                seriesId = o.optString("seriesId").ifBlank { null },
                seriesName = o.optString("seriesName").ifBlank { null },
                season = o.optInt("season"),
                episode = o.optInt("episode"),
                totalBytes = o.optLong("totalBytes", -1),
                downloadedBytes = o.optLong("downloadedBytes"),
                state = OfflineState.valueOf(o.getString("state")),
                error = o.optString("error").ifBlank { null },
                addedAt = o.optLong("addedAt"),
            )
        }
    }.getOrDefault(emptyList())
}
