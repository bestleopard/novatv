package com.novaplay.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Stockage local : playlists, playlist active, favoris, positions de reprise. */
object Store {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("novaplay", Context.MODE_PRIVATE)
    }

    fun playlists(): List<Playlist> {
        val arr = JSONArray(sp.getString("playlists", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                type = PlaylistType.valueOf(o.getString("type")),
                server = o.optString("server"),
                username = o.optString("username"),
                password = o.optString("password"),
                m3uUrl = o.optString("m3uUrl"),
            )
        }
    }

    private fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("type", it.type.name)
                    .put("server", it.server).put("username", it.username)
                    .put("password", it.password).put("m3uUrl", it.m3uUrl)
            )
        }
        sp.edit().putString("playlists", arr.toString()).apply()
    }

    fun addPlaylist(p: Playlist) = savePlaylists(playlists() + p)

    fun removePlaylist(id: String) {
        savePlaylists(playlists().filterNot { it.id == id })
        if (activeId == id) activeId = null
    }

    var activeId: String?
        get() = sp.getString("active", null)
        set(value) { sp.edit().putString("active", value).apply() }

    fun activePlaylist(): Playlist? = activeId?.let { id -> playlists().firstOrNull { it.id == id } }

    fun favorites(playlistId: String): Set<String> =
        sp.getStringSet("fav_$playlistId", emptySet())!!.toSet()

    fun toggleFavorite(playlistId: String, channelId: String) {
        val s = favorites(playlistId).toMutableSet()
        if (!s.add(channelId)) s.remove(channelId)
        sp.edit().putStringSet("fav_$playlistId", s).apply()
    }

    fun position(key: String): Long = sp.getLong("pos_$key", 0L)

    fun savePosition(key: String, positionMs: Long) {
        sp.edit().putLong("pos_$key", positionMs).apply()
    }
}
