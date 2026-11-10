package com.novaplay.app.data

enum class PlaylistType { XTREAM, M3U }

data class Playlist(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
)

data class Category(val id: String, val name: String)

data class Channel(
    val id: String,
    val name: String,
    val logo: String?,
    val url: String,
    val categoryId: String,
)

data class Movie(
    val id: String,
    val name: String,
    val poster: String?,
    val url: String,
    val categoryId: String,
    val rating: String? = null,
)

data class Series(
    val id: String,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val plot: String? = null,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val cover: String?,
)

data class Content(
    val liveCategories: List<Category>,
    val channels: List<Channel>,
    val vodCategories: List<Category>,
    val movies: List<Movie>,
    val seriesCategories: List<Category>,
    val series: List<Series>,
)
