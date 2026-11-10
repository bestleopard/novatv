package com.novaplay.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Contenu de la playlist active, gardé en mémoire. */
object Repository {
    var playlist: Playlist? = null
        private set
    private var content: Content? = null
    private val episodeCache = HashMap<String, List<Episode>>()

    fun contentFor(playlistId: String): Content? = content?.takeIf { playlist?.id == playlistId }

    suspend fun load(p: Playlist): Content = withContext(Dispatchers.IO) {
        val c = when (p.type) {
            PlaylistType.XTREAM -> coroutineScope {
                val api = XtreamApi(p)
                val liveCats = async { api.categories("get_live_categories") }
                val live = async { api.live() }
                val vodCats = async { api.categories("get_vod_categories") }
                val vod = async { api.movies() }
                val serCats = async { api.categories("get_series_categories") }
                val ser = async { api.series() }
                Content(liveCats.await(), live.await(), vodCats.await(), vod.await(), serCats.await(), ser.await())
            }
            PlaylistType.M3U -> M3uParser.parse(Http.get(p.m3uUrl))
        }
        synchronized(this@Repository) {
            playlist = p
            content = c
            episodeCache.clear()
        }
        c
    }

    suspend fun episodes(seriesId: String): List<Episode> = withContext(Dispatchers.IO) {
        episodeCache[seriesId] ?: XtreamApi(playlist!!).episodes(seriesId).also { episodeCache[seriesId] = it }
    }
}
