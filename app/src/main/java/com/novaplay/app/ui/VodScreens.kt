package com.novaplay.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novaplay.app.data.Category
import com.novaplay.app.data.Episode
import com.novaplay.app.data.Repository
import com.novaplay.app.data.Store
import com.novaplay.app.download.DownloadUtil
import com.novaplay.app.download.Offline
import com.novaplay.app.download.OfflineItem
import com.novaplay.app.download.OfflineKind
import com.novaplay.app.download.OfflineState
import com.novaplay.app.player.PlayerActivity

private const val ALL_VOD = "__all"

fun movieKey(movieId: String) = "mv_${Repository.playlist?.id}_$movieId"
fun episodeKey(episodeId: String) = "ep_${Repository.playlist?.id}_$episodeId"

/** État hors ligne d'un film / épisode (mis à jour en direct). */
@Composable
fun rememberOffline(id: String): OfflineItem? {
    val all by Offline.items.collectAsState()
    return all.firstOrNull { it.id == id }
}

fun offlineLabel(o: OfflineItem): String = when (o.state) {
    OfflineState.QUEUED -> "En attente"
    OfflineState.DOWNLOADING -> buildString {
        append(if (o.percent >= 0) "${o.percent} %" else DownloadUtil.formatBytes(o.downloadedBytes))
        if (o.speedBps > 0) append(" · ").append(DownloadUtil.formatSpeed(o.speedBps))
    }
    OfflineState.PAUSED -> "En pause"
    OfflineState.COMPLETED -> "Disponible offline"
    OfflineState.FAILED -> "Échec"
}

@Composable
fun MoviesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    ScreenScaffold("Films", onBack) {
        WithContent { c ->
            val cats = remember(c) { listOf(Category(ALL_VOD, "Tous les films")) + c.vodCategories }
            var sel by rememberSaveable { mutableStateOf(ALL_VOD) }
            var query by rememberSaveable { mutableStateOf("") }
            val list = remember(c, sel, query) {
                c.movies.filter {
                    (sel == ALL_VOD || it.categoryId == sel) &&
                        (query.isBlank() || it.name.contains(query, ignoreCase = true))
                }
            }
            CategoryLayout(cats, sel, { sel = it }) {
                Column(Modifier.fillMaxSize()) {
                    SearchField(query, { query = it })
                    if (list.isEmpty()) Centered("Aucun film") else GridOf {
                        items(list) { m ->
                            val resumed = Store.position(movieKey(m.id)) > 0
                            PosterCard(m.name, m.poster, onClick = { onOpen(m.id) }, badge = if (resumed) "En cours" else m.rating)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MovieDetailScreen(movieId: String, onBack: () -> Unit) {
    ScreenScaffold("Film", onBack) {
        WithContent { c ->
            val m = remember(c, movieId) { c.movies.firstOrNull { it.id == movieId } }
            if (m == null) { Centered("Film introuvable"); return@WithContent }
            val ctx = LocalContext.current
            val key = movieKey(m.id)
            val o = rememberOffline(key)
            val done = o?.state == OfflineState.COMPLETED
            val localPath = if (done) Offline.file(o!!).path else null
            val resumePos = remember { Store.position(key) }

            Row(Modifier.fillMaxSize().padding(20.dp)) {
                AsyncImage(
                    model = m.poster, contentDescription = m.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(180.dp).height(270.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(m.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    m.rating?.let { Text("Note : $it", style = MaterialTheme.typography.bodyMedium) }
                    if (resumePos > 0) Text("Reprise à ${formatTime(resumePos)}", color = Accent)
                    Button(onClick = {
                        PlayerActivity.start(ctx, m.url, m.name, live = false, resumeKey = key, localFile = localPath)
                    }) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp))
                        Text(if (done) "Lire (hors ligne)" else if (resumePos > 0) "Reprendre" else "Lire")
                    }
                    if (resumePos > 0) OutlinedButton(onClick = {
                        PlayerActivity.start(ctx, m.url, m.name, live = false, resumeKey = key, localFile = localPath, fromStart = true)
                    }) { Icon(Icons.Default.Replay, null); Spacer(Modifier.width(6.dp)); Text("Depuis le début") }
                    DownloadButton(o, onStart = {
                        Offline.enqueue(
                            ctx,
                            OfflineItem(
                                id = key, kind = OfflineKind.MOVIE, title = m.name, url = m.url, poster = m.poster,
                                fileName = "$key.${Offline.extensionOf(m.url)}",
                            ),
                        )
                    })
                }
            }
        }
    }
}

@Composable
fun DownloadButton(o: OfflineItem?, onStart: () -> Unit) {
    val ctx = LocalContext.current
    if (o == null) {
        OutlinedButton(onClick = onStart) {
            Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Télécharger")
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (o.state) {
            OfflineState.COMPLETED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("✓ Disponible offline · ${DownloadUtil.formatBytes(o.downloadedBytes)}", color = Accent)
            }
            OfflineState.DOWNLOADING, OfflineState.QUEUED -> {
                Text(offlineLabel(o) + if (o.totalBytes > 0) " · ${DownloadUtil.formatBytes(o.downloadedBytes)} / ${DownloadUtil.formatBytes(o.totalBytes)}" else "", color = Accent)
                if (o.percent >= 0) LinearProgressIndicator(progress = { o.percent / 100f }, modifier = Modifier.width(260.dp))
                else LinearProgressIndicator(modifier = Modifier.width(260.dp))
            }
            OfflineState.PAUSED -> Text("En pause · ${DownloadUtil.formatBytes(o.downloadedBytes)}", color = Accent)
            OfflineState.FAILED -> Text("Échec : ${o.error ?: "erreur inconnue"}", color = MaterialTheme.colorScheme.error)
        }
        o.error?.takeIf { o.state == OfflineState.DOWNLOADING }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (o.state) {
                OfflineState.DOWNLOADING, OfflineState.QUEUED ->
                    OutlinedButton(onClick = { Offline.pause(o.id) }) { Icon(Icons.Default.Pause, null); Spacer(Modifier.width(6.dp)); Text("Pause") }
                OfflineState.PAUSED, OfflineState.FAILED ->
                    OutlinedButton(onClick = { Offline.resume(ctx, o.id) }) { Icon(Icons.Default.Replay, null); Spacer(Modifier.width(6.dp)); Text("Reprendre") }
                OfflineState.COMPLETED -> Unit
            }
            OutlinedButton(onClick = { Offline.delete(o.id) }) {
                Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp))
                Text(if (o.state == OfflineState.COMPLETED) "Supprimer" else "Annuler")
            }
        }
    }
}

@Composable
fun SeriesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    ScreenScaffold("Séries", onBack) {
        WithContent { c ->
            if (c.series.isEmpty()) {
                Centered("Aucune série dans cette playlist.\n(Les séries nécessitent un compte Xtream Codes.)")
                return@WithContent
            }
            val cats = remember(c) { listOf(Category(ALL_VOD, "Toutes les séries")) + c.seriesCategories }
            var sel by rememberSaveable { mutableStateOf(ALL_VOD) }
            var query by rememberSaveable { mutableStateOf("") }
            val list = remember(c, sel, query) {
                c.series.filter {
                    (sel == ALL_VOD || it.categoryId == sel) &&
                        (query.isBlank() || it.name.contains(query, ignoreCase = true))
                }
            }
            CategoryLayout(cats, sel, { sel = it }) {
                Column(Modifier.fillMaxSize()) {
                    SearchField(query, { query = it })
                    if (list.isEmpty()) Centered("Aucune série") else GridOf {
                        items(list) { s -> PosterCard(s.name, s.cover, onClick = { onOpen(s.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(seriesId: String, onBack: () -> Unit) {
    ScreenScaffold("Série", onBack) {
        WithContent { c ->
            val s = remember(c, seriesId) { c.series.firstOrNull { it.id == seriesId } }
            if (s == null) { Centered("Série introuvable"); return@WithContent }
            var result by remember { mutableStateOf<Result<List<Episode>>?>(null) }
            var attempt by remember { mutableStateOf(0) }
            LaunchedEffect(seriesId, attempt) { result = runCatching { Repository.episodes(seriesId) } }

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    AsyncImage(
                        model = s.cover, contentDescription = s.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        s.plot?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                    }
                }
                val r = result
                when {
                    r == null -> Centered("Chargement des épisodes…") { CircularProgressIndicator() }
                    r.isFailure -> Centered("Erreur : ${r.exceptionOrNull()?.message}") {
                        Button(onClick = { result = null; attempt++ }) { Text("Réessayer") }
                    }
                    else -> EpisodeList(s.id, s.name, s.cover, r.getOrThrow())
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(seriesId: String, seriesName: String, seriesCover: String?, episodes: List<Episode>) {
    if (episodes.isEmpty()) { Centered("Aucun épisode"); return }
    val ctx = LocalContext.current
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    var season by rememberSaveable { mutableStateOf(seasons.first()) }
    val list = remember(episodes, season) { episodes.filter { it.season == season } }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons) { n ->
            FilterChip(selected = n == season, onClick = { season = n }, label = { Text("Saison $n") })
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list) { e ->
            val key = episodeKey(e.id)
            val o = rememberOffline(key)
            val done = o?.state == OfflineState.COMPLETED
            val title = "$seriesName · S${e.season}E${e.number}"
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusBox(
                    onClick = { PlayerActivity.start(ctx, e.url, title, live = false, resumeKey = key, localFile = if (done) Offline.file(o!!).path else null) },
                    modifier = Modifier.weight(1f),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Default.DownloadDone else Icons.Default.PlayArrow, null, tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.fillMaxWidth()) {
                            Text("${e.number}. ${e.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val pos = Store.position(key)
                            val status = listOfNotNull(
                                if (pos > 0) "Reprise à ${formatTime(pos)}" else null,
                                o?.let { offlineLabel(it) },
                            ).joinToString(" · ")
                            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = Accent)
                        }
                    }
                }
                when (o?.state) {
                    null ->
                        IconButton(onClick = {
                            Offline.enqueue(
                                ctx,
                                OfflineItem(
                                    id = key, kind = OfflineKind.EPISODE, title = title, url = e.url,
                                    poster = seriesCover ?: e.cover, fileName = "$key.${Offline.extensionOf(e.url)}",
                                    seriesId = "${Repository.playlist?.id}_$seriesId", seriesName = seriesName,
                                    season = e.season, episode = e.number,
                                ),
                            )
                        }) { Icon(Icons.Default.Download, "Télécharger") }
                    OfflineState.DOWNLOADING, OfflineState.QUEUED ->
                        IconButton(onClick = { Offline.pause(key) }) { Icon(Icons.Default.Pause, "Pause") }
                    OfflineState.PAUSED, OfflineState.FAILED ->
                        IconButton(onClick = { Offline.resume(ctx, key) }) { Icon(Icons.Default.Replay, "Reprendre") }
                    OfflineState.COMPLETED -> Unit
                }
                if (o != null) {
                    IconButton(onClick = { Offline.delete(key) }) { Icon(Icons.Default.Delete, "Supprimer") }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
}
