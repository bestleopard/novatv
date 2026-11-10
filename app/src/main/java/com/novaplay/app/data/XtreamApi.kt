package com.novaplay.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Client de l'API Xtream Codes (player_api.php). */
class XtreamApi(private val p: Playlist) {

    private val base: String = p.server.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String? = null, extra: String = ""): String {
        val a = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=${enc(p.username)}&password=${enc(p.password)}$a$extra"
    }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    private fun array(text: String): List<JSONObject> {
        val t = text.trim()
        if (!t.startsWith("[")) return emptyList()
        val a = JSONArray(t)
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    fun authenticate() {
        val o = try {
            JSONObject(Http.get(api()))
        } catch (e: org.json.JSONException) {
            throw IOException("Réponse invalide : vérifie l'adresse du serveur")
        }
        val info = o.optJSONObject("user_info") ?: throw IOException("Réponse invalide du serveur")
        if (info.optInt("auth", 0) != 1) throw IOException("Identifiants refusés par le serveur")
    }

    fun categories(action: String): List<Category> =
        array(Http.get(api(action))).map { Category(it.str("category_id"), it.str("category_name")) }

    fun live(): List<Channel> = array(Http.get(api("get_live_streams"))).map {
        val id = it.str("stream_id")
        Channel(
            id = id,
            name = it.str("name"),
            logo = it.str("stream_icon").ifBlank { null },
            url = "$base/live/${p.username}/${p.password}/$id.ts",
            categoryId = it.str("category_id"),
        )
    }

    fun movies(): List<Movie> = array(Http.get(api("get_vod_streams"))).map {
        val id = it.str("stream_id")
        val ext = it.str("container_extension").ifBlank { "mp4" }
        Movie(
            id = id,
            name = it.str("name"),
            poster = it.str("stream_icon").ifBlank { null },
            url = "$base/movie/${p.username}/${p.password}/$id.$ext",
            categoryId = it.str("category_id"),
            rating = it.str("rating").ifBlank { null },
        )
    }

    fun series(): List<Series> = array(Http.get(api("get_series"))).map {
        Series(
            id = it.str("series_id"),
            name = it.str("name"),
            cover = it.str("cover").ifBlank { null },
            categoryId = it.str("category_id"),
            plot = it.str("plot").ifBlank { null },
        )
    }

    fun episodes(seriesId: String): List<Episode> {
        val o = JSONObject(Http.get(api("get_series_info", "&series_id=${enc(seriesId)}")))
        val out = mutableListOf<Episode>()

        fun addEpisode(e: JSONObject, seasonHint: Int, index: Int) {
            val id = e.str("id")
            val ext = e.str("container_extension").ifBlank { "mp4" }
            val info = e.optJSONObject("info")
            out += Episode(
                id = id,
                title = e.str("title").ifBlank { "Épisode ${index + 1}" },
                season = e.optInt("season", seasonHint).takeIf { it > 0 } ?: seasonHint,
                number = e.optInt("episode_num", index + 1),
                url = "$base/series/${p.username}/${p.password}/$id.$ext",
                cover = info?.let { if (it.isNull("movie_image")) null else it.optString("movie_image").ifBlank { null } },
            )
        }

        when (val raw = o.opt("episodes")) {
            is JSONObject -> raw.keys().forEach { season ->
                val a = raw.optJSONArray(season) ?: return@forEach
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { addEpisode(it, season.toIntOrNull() ?: 1, i) }
            }
            is JSONArray -> for (s in 0 until raw.length()) {
                val a = raw.optJSONArray(s) ?: continue
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { addEpisode(it, s + 1, i) }
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
    }
}
