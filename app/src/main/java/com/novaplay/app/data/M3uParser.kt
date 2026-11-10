package com.novaplay.app.data

/**
 * Lit une playlist M3U / M3U8.
 * Les entrées .mp4/.mkv ou /movie/ /series/ sont rangées dans « Films » (VOD), le reste en TV en direct.
 */
object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val vodExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm")

    fun parse(text: String): Content {
        val channels = mutableListOf<Channel>()
        val movies = mutableListOf<Movie>()
        val liveCats = LinkedHashMap<String, Category>()
        val vodCats = LinkedHashMap<String, Category>()

        var info: String? = null
        var index = 0

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> info = line
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val inf = info ?: ""
                    info = null
                    val attrs = attrRegex.findAll(inf).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val tail = inf.substring(inf.lastIndexOf('"').coerceAtLeast(0))
                    val name = tail.substringAfter(',', "").trim()
                        .ifEmpty { attrs["tvg-name"].orEmpty() }
                        .ifEmpty { "Sans nom" }
                    val group = attrs["group-title"].orEmpty().ifBlank { "Autres" }
                    val logo = attrs["tvg-logo"]?.ifBlank { null }
                    val id = "m${index++}"

                    val path = line.lowercase().substringBefore('?')
                    val isVod = "/movie/" in path || "/series/" in path || vodExtensions.any { path.endsWith(it) }

                    if (isVod) {
                        val cat = vodCats.getOrPut(group) { Category("v${vodCats.size}", group) }
                        movies += Movie(id, name, logo, line, cat.id)
                    } else {
                        val cat = liveCats.getOrPut(group) { Category("l${liveCats.size}", group) }
                        channels += Channel(id, name, logo, line, cat.id)
                    }
                }
            }
        }
        return Content(liveCats.values.toList(), channels, vodCats.values.toList(), movies, emptyList(), emptyList())
    }
}
