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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.novaplay.app.download.DownloadUtil
import com.novaplay.app.download.Offline
import com.novaplay.app.download.OfflineItem
import com.novaplay.app.download.OfflineKind
import com.novaplay.app.download.OfflineState
import com.novaplay.app.player.PlayerActivity

private enum class OfflineTab { MOVIES, SERIES, PROGRESS }

private fun playOffline(ctx: android.content.Context, o: OfflineItem) {
    PlayerActivity.start(ctx, o.url, o.title, live = false, resumeKey = o.id, localFile = Offline.file(o).path)
}

/** Espace « Offline » : films et séries déjà téléchargés + téléchargements en cours. Fonctionne sans Internet. */
@Composable
fun OfflineScreen(onBack: () -> Unit, onOpenSeries: (String) -> Unit) {
    val all by Offline.items.collectAsState()
    val done = all.filter { it.state == OfflineState.COMPLETED }
    val movies = done.filter { it.kind == OfflineKind.MOVIE }.sortedByDescending { it.addedAt }
    val series = done.filter { it.kind == OfflineKind.EPISODE }
        .groupBy { it.seriesId ?: it.seriesName ?: it.title }
        .values.sortedBy { it.first().seriesName.orEmpty().lowercase() }
    val pending = all.filter { it.state != OfflineState.COMPLETED }.sortedBy { it.addedAt }

    var tab by rememberSaveable {
        mutableStateOf(if (movies.isEmpty() && series.isEmpty() && pending.isNotEmpty()) OfflineTab.PROGRESS else OfflineTab.MOVIES)
    }
    var dialogFor by remember { mutableStateOf<OfflineItem?>(null) }

    ScreenScaffold("Offline", onBack) {
        Text(
            "${DownloadUtil.formatBytes(Offline.usedBytes())} utilisés · ${DownloadUtil.formatBytes(Offline.freeBytes())} libres",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(selected = tab == OfflineTab.MOVIES, onClick = { tab = OfflineTab.MOVIES }, label = { Text("Films (${movies.size})") }) }
            item { FilterChip(selected = tab == OfflineTab.SERIES, onClick = { tab = OfflineTab.SERIES }, label = { Text("Séries (${series.size})") }) }
            item { FilterChip(selected = tab == OfflineTab.PROGRESS, onClick = { tab = OfflineTab.PROGRESS }, label = { Text("En cours (${pending.size})") }) }
        }

        when (tab) {
            OfflineTab.MOVIES ->
                if (movies.isEmpty()) Centered("Aucun film téléchargé.\nOuvre un film et appuie sur « Télécharger ».")
                else GridOf {
                    items(movies) { m ->
                        PosterCard(m.title, m.poster, onClick = { dialogFor = m }, badge = DownloadUtil.formatBytes(m.downloadedBytes))
                    }
                }

            OfflineTab.SERIES ->
                if (series.isEmpty()) Centered("Aucun épisode téléchargé.")
                else GridOf {
                    items(series) { eps ->
                        val first = eps.first()
                        PosterCard(
                            first.seriesName ?: first.title, first.poster,
                            onClick = { onOpenSeries(first.seriesId ?: first.seriesName ?: first.title) },
                            badge = "${eps.size} ép.",
                        )
                    }
                }

            OfflineTab.PROGRESS ->
                if (pending.isEmpty()) Centered("Aucun téléchargement en cours.")
                else LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(pending, key = { it.id }) { o -> ProgressRow(o) }
                }
        }
    }

    dialogFor?.let { o -> PlayDeleteDialog(o, onDismiss = { dialogFor = null }) }
}

/** Épisodes téléchargés d'une série. */
@Composable
fun OfflineSeriesScreen(groupKey: String, onBack: () -> Unit) {
    val all by Offline.items.collectAsState()
    val eps = all.filter {
        it.kind == OfflineKind.EPISODE && it.state == OfflineState.COMPLETED &&
            (it.seriesId ?: it.seriesName ?: it.title) == groupKey
    }.sortedWith(compareBy({ it.season }, { it.episode }))
    val ctx = LocalContext.current
    var dialogFor by remember { mutableStateOf<OfflineItem?>(null) }

    ScreenScaffold(eps.firstOrNull()?.seriesName ?: "Série", onBack) {
        if (eps.isEmpty()) {
            Centered("Plus aucun épisode téléchargé pour cette série.")
            return@ScreenScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(eps, key = { it.id }) { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FocusBox(onClick = { playOffline(ctx, e) }, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = Accent)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Saison ${e.season} · Épisode ${e.episode}", fontWeight = FontWeight.Bold)
                                Text(DownloadUtil.formatBytes(e.downloadedBytes), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    IconButton(onClick = { dialogFor = e }) { Icon(Icons.Default.Delete, "Supprimer") }
                }
            }
        }
    }

    dialogFor?.let { o -> PlayDeleteDialog(o, onDismiss = { dialogFor = null }, deleteOnly = true) }
}

@Composable
private fun PlayDeleteDialog(o: OfflineItem, onDismiss: () -> Unit, deleteOnly: Boolean = false) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(o.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = { Text(if (deleteOnly) "Supprimer ce fichier de l'appareil ?" else "${DownloadUtil.formatBytes(o.downloadedBytes)} · disponible sans connexion") },
        confirmButton = {
            if (deleteOnly) {
                Button(onClick = { Offline.delete(o.id); onDismiss() }) { Text("Supprimer") }
            } else {
                Button(onClick = { onDismiss(); playOffline(ctx, o) }) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Lire")
                }
            }
        },
        dismissButton = {
            if (deleteOnly) {
                OutlinedButton(onClick = onDismiss) { Text("Annuler") }
            } else {
                OutlinedButton(onClick = { Offline.delete(o.id); onDismiss() }) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Supprimer")
                }
            }
        },
    )
}

@Composable
private fun ProgressRow(o: OfflineItem) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = o.poster, contentDescription = o.title, contentScale = ContentScale.Crop,
            modifier = Modifier.width(54.dp).height(80.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(o.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sizes = if (o.totalBytes > 0) "${DownloadUtil.formatBytes(o.downloadedBytes)} / ${DownloadUtil.formatBytes(o.totalBytes)}"
            else DownloadUtil.formatBytes(o.downloadedBytes)
            Text(
                "${offlineLabel(o)} · $sizes",
                style = MaterialTheme.typography.bodySmall,
                color = if (o.state == OfflineState.FAILED) MaterialTheme.colorScheme.error else Accent,
            )
            o.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3) }
            if (o.state == OfflineState.DOWNLOADING) {
                Spacer(Modifier.height(6.dp))
                if (o.percent >= 0) LinearProgressIndicator(progress = { o.percent / 100f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        when (o.state) {
            OfflineState.DOWNLOADING, OfflineState.QUEUED ->
                IconButton(onClick = { Offline.pause(o.id) }) { Icon(Icons.Default.Pause, "Pause") }
            OfflineState.PAUSED, OfflineState.FAILED ->
                IconButton(onClick = { Offline.resume(ctx, o.id) }) { Icon(Icons.Default.Replay, "Reprendre") }
            OfflineState.COMPLETED -> Unit
        }
        IconButton(onClick = { Offline.delete(o.id) }) { Icon(Icons.Default.Delete, "Annuler") }
    }
}
