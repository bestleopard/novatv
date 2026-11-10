package com.novaplay.app.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.novaplay.app.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Téléchargements hors ligne (films / épisodes) basés sur le DownloadManager de Media3.
 * Les fichiers sont stockés dans le dossier privé de l'app et lus ensuite sans réseau.
 */
object DownloadUtil {
    const val CHANNEL_ID = "downloads"

    private var databaseProvider: DatabaseProvider? = null
    private var cache: Cache? = null
    private var manager: DownloadManager? = null

    fun httpFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

    @Synchronized
    private fun db(ctx: Context): DatabaseProvider =
        databaseProvider ?: StandaloneDatabaseProvider(ctx.applicationContext).also { databaseProvider = it }

    @Synchronized
    fun cache(ctx: Context): Cache = cache ?: run {
        val app = ctx.applicationContext
        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads")
        SimpleCache(dir, NoOpCacheEvictor(), db(app)).also { cache = it }
    }

    /** À appeler depuis le thread principal. */
    @Synchronized
    fun manager(ctx: Context): DownloadManager = manager ?: run {
        val app = ctx.applicationContext
        DownloadManager(app, db(app), cache(app), httpFactory(), Executors.newFixedThreadPool(3))
            .apply { maxParallelDownloads = 2 }
            .also { manager = it }
    }

    /** Lecture : utilise le fichier téléchargé s'il existe, sinon le réseau (sans écrire dans le cache). */
    fun playbackFactory(ctx: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(ctx))
            .setUpstreamDataSourceFactory(httpFactory())
            .setCacheWriteDataSinkFactory(null)

    fun start(ctx: Context, id: String, url: String, title: String, poster: String?) {
        val meta = JSONObject().put("title", title).put("poster", poster ?: "").toString().toByteArray()
        val builder = DownloadRequest.Builder(id, Uri.parse(url)).setData(meta)
        if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        DownloadService.sendAddDownload(ctx, NovaDownloadService::class.java, builder.build(), false)
    }

    fun remove(ctx: Context, id: String) {
        DownloadService.sendRemoveDownload(ctx, NovaDownloadService::class.java, id, false)
    }

    fun retry(ctx: Context, d: Download) {
        DownloadService.sendAddDownload(ctx, NovaDownloadService::class.java, d.request, false)
    }

    fun title(d: Download): String =
        runCatching { JSONObject(String(d.request.data)).optString("title") }.getOrNull()
            ?.ifBlank { null } ?: d.request.id

    fun poster(d: Download): String? =
        runCatching { JSONObject(String(d.request.data)).optString("poster") }.getOrNull()?.ifBlank { null }

    /** Doit être appelé depuis le thread principal (coroutine Main). */
    suspend fun find(ctx: Context, id: String): Download? {
        val m = manager(ctx)
        m.currentDownloads.firstOrNull { it.request.id == id }?.let { return it }
        return withContext(Dispatchers.IO) { m.downloadIndex.getDownload(id) }
    }

    /** Doit être appelé depuis le thread principal (coroutine Main). */
    suspend fun all(ctx: Context): List<Download> {
        val m = manager(ctx)
        val live = m.currentDownloads.associateBy { it.request.id }
        val stored = withContext(Dispatchers.IO) {
            val out = mutableListOf<Download>()
            m.downloadIndex.getDownloads().use { c -> while (c.moveToNext()) out += c.download }
            out
        }
        return stored.map { live[it.request.id] ?: it }.sortedByDescending { it.startTimeMs }
    }

    fun stateLabel(d: Download): String = when (d.state) {
        Download.STATE_QUEUED -> "En attente"
        Download.STATE_DOWNLOADING -> "Téléchargement ${percent(d)}"
        Download.STATE_COMPLETED -> "Téléchargé"
        Download.STATE_FAILED -> "Échec"
        Download.STATE_STOPPED -> "En pause"
        Download.STATE_REMOVING -> "Suppression…"
        Download.STATE_RESTARTING -> "Redémarrage…"
        else -> ""
    }

    fun percent(d: Download): String {
        val p = d.percentDownloaded
        return if (p >= 0f) "${p.toInt()} %" else formatBytes(d.bytesDownloaded)
    }

    fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format("%.2f Go", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format("%.0f Mo", b / (1L shl 20).toDouble())
        else -> "${b / 1024} Ko"
    }
}
