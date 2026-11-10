package com.novaplay.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novaplay.app.data.Category
import com.novaplay.app.data.Content
import com.novaplay.app.data.Repository
import com.novaplay.app.data.Store

val Accent = Color(0xFF4FC3F7)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF00141F),
            secondary = Color(0xFFFFB74D),
            background = Color(0xFF0E1117),
            surface = Color(0xFF161B22),
            surfaceVariant = Color(0xFF1F2630),
            onBackground = Color(0xFFE6EDF3),
            onSurface = Color(0xFFE6EDF3),
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}

/** Élément cliquable bien visible quand il a le focus de la télécommande. */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    selected: Boolean = false,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "focusScale")
    val borderColor = when {
        focused -> Accent
        selected -> Accent.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .border(BorderStroke(if (focused) 3.dp else 1.dp, borderColor), shape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) { content(focused) }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        body()
    }
}

@Composable
fun Centered(text: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text("Rechercher") },
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * Catégories à gauche sur grand écran (TV / tablette), en bandeau horizontal sur téléphone.
 */
@Composable
fun CategoryLayout(
    categories: List<Category>,
    selected: String,
    onSelect: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.width(240.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(categories) { c ->
                        FocusBox(
                            onClick = { onSelect(c.id) },
                            selected = c.id == selected,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                c.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = if (c.id == selected) Accent else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories) { c ->
                        FilterChip(
                            selected = c.id == selected,
                            onClick = { onSelect(c.id) },
                            label = { Text(c.name, maxLines = 1) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

@Composable
fun PosterCard(title: String, image: String?, onClick: () -> Unit, badge: String? = null) {
    FocusBox(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(190.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    AsyncImage(
                        model = image, contentDescription = title,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(title.take(1).uppercase(), style = MaterialTheme.typography.displaySmall, color = Accent)
                }
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp).height(40.dp),
            )
        }
    }
}

@Composable
fun Logo(url: String?, name: String, size: Int = 48) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            Text(name.take(2).uppercase(), color = Accent, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Charge le contenu de la playlist active (ou réutilise celui en mémoire).
 * En cas d'échec (ex. pas de réseau), propose d'ouvrir les téléchargements hors ligne.
 */
@Composable
fun WithContent(
    reloadKey: Int = 0,
    onDownloads: (() -> Unit)? = null,
    onPlaylists: (() -> Unit)? = null,
    body: @Composable (Content) -> Unit,
) {
    val playlist = remember { Store.activePlaylist() }
    if (playlist == null) {
        Centered("Aucune playlist active") {
            if (onPlaylists != null) Button(onClick = onPlaylists) { Text("Choisir une playlist") }
        }
        return
    }
    var retry by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf(Repository.contentFor(playlist.id)?.let { Result.success(it) }) }

    LaunchedEffect(playlist.id, reloadKey, retry) {
        if (reloadKey == 0 && retry == 0 && result?.isSuccess == true) return@LaunchedEffect
        result = null
        result = runCatching { Repository.load(playlist) }
    }

    val r = result
    when {
        r == null -> Centered("Chargement de « ${playlist.name} »…") { CircularProgressIndicator() }
        r.isFailure -> Centered("Impossible de charger la playlist :\n${r.exceptionOrNull()?.message ?: "erreur inconnue"}") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { retry++ }) { Text("Réessayer") }
                if (onDownloads != null) OutlinedButton(onClick = onDownloads) { Text("Mes téléchargements") }
                if (onPlaylists != null) OutlinedButton(onClick = onPlaylists) { Text("Playlists") }
            }
        }
        else -> body(r.getOrThrow())
    }
}
