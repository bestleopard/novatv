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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.novaplay.app.data.Playlist
import com.novaplay.app.data.PlaylistType
import com.novaplay.app.data.Store
import com.novaplay.app.data.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun PlaylistsScreen(onOpen: (Playlist) -> Unit, onDownloads: () -> Unit) {
    var playlists by remember { mutableStateOf(Store.playlists()) }
    var adding by rememberSaveable { mutableStateOf(playlists.isEmpty()) }

    ScreenScaffold(
        title = if (adding) "Ajouter une playlist" else "Mes playlists",
        onBack = if (adding && playlists.isNotEmpty()) ({ adding = false }) else null,
        actions = {
            IconButton(onClick = onDownloads) { Icon(Icons.Default.DownloadDone, "Offline") }
            if (!adding) IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Ajouter") }
        },
    ) {
        if (adding) {
            AddPlaylistForm(onSaved = {
                Store.addPlaylist(it)
                playlists = Store.playlists()
                adding = false
                onOpen(it)
            })
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(playlists) { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FocusBox(onClick = { onOpen(p) }, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (p.type == PlaylistType.XTREAM) "Xtream Codes · ${p.server}" else "M3U · ${p.m3uUrl}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                        }
                        IconButton(onClick = {
                            Store.removePlaylist(p.id)
                            playlists = Store.playlists()
                        }) { Icon(Icons.Default.Delete, "Supprimer") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPlaylistForm(onSaved: (Playlist) -> Unit) {
    var type by rememberSaveable { mutableStateOf(PlaylistType.XTREAM) }
    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var m3u by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == PlaylistType.XTREAM, onClick = { type = PlaylistType.XTREAM }, label = { Text("Xtream Codes") })
                FilterChip(selected = type == PlaylistType.M3U, onClick = { type = PlaylistType.M3U }, label = { Text("Lien M3U") })
            }
        }
        item { Field("Nom de la playlist", name) { name = it } }
        if (type == PlaylistType.XTREAM) {
            item { Field("Adresse du serveur (http://exemple.com:8080)", server, KeyboardType.Uri) { server = it } }
            item { Field("Nom d'utilisateur", user) { user = it } }
            item {
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("Mot de passe") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item { Field("URL de la playlist M3U", m3u, KeyboardType.Uri) { m3u = it } }
        }
        error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy,
                    onClick = {
                        error = null
                        val p = Playlist(
                            id = UUID.randomUUID().toString(),
                            name = name.ifBlank { if (type == PlaylistType.XTREAM) "Xtream" else "M3U" },
                            type = type,
                            server = server.trim(),
                            username = user.trim(),
                            password = pass.trim(),
                            m3uUrl = m3u.trim(),
                        )
                        if (type == PlaylistType.XTREAM && (p.server.isBlank() || p.username.isBlank() || p.password.isBlank())) {
                            error = "Remplis le serveur, l'utilisateur et le mot de passe."
                            return@Button
                        }
                        if (type == PlaylistType.M3U && !p.m3uUrl.startsWith("http")) {
                            error = "L'URL M3U doit commencer par http:// ou https://"
                            return@Button
                        }
                        if (type == PlaylistType.M3U) { onSaved(p); return@Button }
                        busy = true
                        scope.launch {
                            val r = runCatching { withContext(Dispatchers.IO) { XtreamApi(p).authenticate() } }
                            busy = false
                            r.onSuccess { onSaved(p) }
                                .onFailure { error = "Connexion échouée : ${it.message}" }
                        }
                    },
                ) { Text(if (type == PlaylistType.XTREAM) "Se connecter" else "Ajouter") }
                if (busy) {
                    Spacer(Modifier.width(16.dp))
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}
