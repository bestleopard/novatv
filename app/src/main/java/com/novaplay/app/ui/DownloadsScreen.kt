package com.novaplay.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import coil.compose.AsyncImage
import com.novaplay.app.download.DownloadUtil
import com.novaplay.app.player.PlayerActivity
import kotlinx.coroutines.delay

/** Liste des téléchargements, utilisable sans connexion. */
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var downloads by remember { mutableStateOf<List<Download>?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            downloads = runCatching { DownloadUtil.all(ctx) }.getOrDefault(emptyList())
            delay(1000)
        }
    }

    ScreenScaffold("Téléchargements", onBack) {
        val list = downloads
        when {
            list == null -> Centered("Chargement…")
            list.isEmpty() -> Centered("Aucun téléchargement.\nOuvre un film ou un épisode et appuie sur « Télécharger ».")
            else -> {
                val total = list.filter { it.state == Download.STATE_COMPLETED }.sumOf { it.bytesDownloaded }
                Text(
                    "${list.size} élément(s) · ${DownloadUtil.formatBytes(total)} utilisés",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.request.id }) { d ->
                        val title = DownloadUtil.title(d)
                        val done = d.state == Download.STATE_COMPLETED
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FocusBox(
                                onClick = {
                                    if (done) PlayerActivity.start(
                                        ctx, d.request.uri.toString(), title, live = false,
                                        resumeKey = d.request.id, downloadId = d.request.id,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = DownloadUtil.poster(d), contentDescription = title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(54.dp).height(80.dp).clip(RoundedCornerShape(6.dp)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            DownloadUtil.stateLabel(d) + if (done) " · ${DownloadUtil.formatBytes(d.bytesDownloaded)} · prêt hors ligne" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (d.state == Download.STATE_FAILED) MaterialTheme.colorScheme.error else Accent,
                                        )
                                        if (d.state == Download.STATE_DOWNLOADING && d.percentDownloaded >= 0f) {
                                            Spacer(Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { d.percentDownloaded / 100f },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                            if (d.state == Download.STATE_FAILED) {
                                IconButton(onClick = { DownloadUtil.retry(ctx, d) }) { Icon(Icons.Default.Replay, "Réessayer") }
                            }
                            IconButton(onClick = { DownloadUtil.remove(ctx, d.request.id) }) { Icon(Icons.Default.Delete, "Supprimer") }
                        }
                    }
                }
            }
        }
    }
}
