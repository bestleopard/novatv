package com.novaplay.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novaplay.app.data.Category
import com.novaplay.app.data.Repository
import com.novaplay.app.data.Store
import com.novaplay.app.download.Offline
import com.novaplay.app.download.OfflineKind
import com.novaplay.app.download.OfflineState
import com.novaplay.app.player.PlayerActivity

private data class Tile(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun HomeScreen(
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onDownloads: () -> Unit,
    onPlaylists: () -> Unit,
) {
    var reload by remember { mutableIntStateOf(0) }
    val name = remember { Store.activePlaylist()?.name ?: "NovaPlay" }
    ScreenScaffold(
        title = name,
        onBack = null,
        actions = {
            IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, "Actualiser") }
            IconButton(onClick = onPlaylists) { Icon(Icons.Default.SwapHoriz, "Changer de playlist") }
        },
    ) {
        WithContent(reloadKey = reload, onDownloads = onDownloads, onPlaylists = onPlaylists) { c ->
            val offline by Offline.items.collectAsState()
            val offMovies = offline.count { it.state == OfflineState.COMPLETED && it.kind == OfflineKind.MOVIE }
            val offSeries = offline.filter { it.state == OfflineState.COMPLETED && it.kind == OfflineKind.EPISODE }
                .map { it.seriesId ?: it.seriesName }.distinct().size
            val offActive = offline.count { it.state == OfflineState.DOWNLOADING || it.state == OfflineState.QUEUED }
            val tiles = listOf(
                Tile("TV en direct", "${c.channels.size} chaînes", Icons.Default.LiveTv, onLive),
                Tile("Films", "${c.movies.size} films", Icons.Default.Movie, onMovies),
                Tile("Séries", "${c.series.size} séries", Icons.Default.VideoLibrary, onSeries),
                Tile("Offline", "$offMovies films · $offSeries séries" + if (offActive > 0) " · $offActive en cours" else "", Icons.Default.DownloadDone, onDownloads),
            )
            val first = remember { FocusRequester() }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(tiles.size) { i ->
                    val t = tiles[i]
                    FocusBox(
                        onClick = t.onClick,
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    ) { focused ->
                        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(t.icon, null, tint = if (focused) Accent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                            Column {
                                Text(t.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(t.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        }
    }
}

private const val FAV = "__fav"
private const val ALL = "__all"

@Composable
fun LiveScreen(onBack: () -> Unit) {
    ScreenScaffold("TV en direct", onBack) {
        WithContent { c ->
            val ctx = LocalContext.current
            val pid = Repository.playlist?.id.orEmpty()
            var favs by remember { mutableStateOf(Store.favorites(pid)) }
            val cats = remember(c) { listOf(Category(FAV, "★ Favoris"), Category(ALL, "Toutes les chaînes")) + c.liveCategories }
            var sel by rememberSaveable { mutableStateOf(ALL) }
            var query by rememberSaveable { mutableStateOf("") }
            val list = remember(c, sel, query, favs) {
                c.channels.filter {
                    val inCat = when (sel) {
                        FAV -> it.id in favs
                        ALL -> true
                        else -> it.categoryId == sel
                    }
                    inCat && (query.isBlank() || it.name.contains(query, ignoreCase = true))
                }
            }
            CategoryLayout(cats, sel, { sel = it }) {
                Column(Modifier.fillMaxSize()) {
                    SearchField(query, { query = it })
                    if (list.isEmpty()) {
                        Centered(if (sel == FAV) "Aucun favori. Appuie sur ☆ à côté d'une chaîne." else "Aucune chaîne")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(list) { ch ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FocusBox(
                                        onClick = { PlayerActivity.start(ctx, ch.url, ch.name, live = true) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Logo(ch.logo, ch.name)
                                            Spacer(Modifier.width(12.dp))
                                            Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    IconButton(onClick = {
                                        Store.toggleFavorite(pid, ch.id)
                                        favs = Store.favorites(pid)
                                    }) {
                                        val fav = ch.id in favs
                                        Icon(
                                            if (fav) Icons.Default.Star else Icons.Default.StarBorder,
                                            if (fav) "Retirer des favoris" else "Ajouter aux favoris",
                                            tint = if (fav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GridOf(content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
