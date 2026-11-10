package com.novaplay.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novaplay.app.data.Store

@Composable
fun App() {
    val nav = rememberNavController()
    val start = remember { if (Store.activePlaylist() != null) "home" else "playlists" }

    fun NavHostController.resetTo(route: String) = navigate(route) {
        popUpTo(graph.id) { inclusive = true }
    }

    NavHost(navController = nav, startDestination = start) {
        composable("playlists") {
            PlaylistsScreen(
                onOpen = { p -> Store.activeId = p.id; nav.resetTo("home") },
                onDownloads = { nav.navigate("offline") },
            )
        }
        composable("home") {
            HomeScreen(
                onLive = { nav.navigate("live") },
                onMovies = { nav.navigate("movies") },
                onSeries = { nav.navigate("series") },
                onDownloads = { nav.navigate("offline") },
                onPlaylists = { nav.resetTo("playlists") },
            )
        }
        composable("live") { LiveScreen(onBack = { nav.popBackStack() }) }
        composable("movies") {
            MoviesScreen(onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("movie/${Uri.encode(id)}") })
        }
        composable("movie/{id}") { e ->
            MovieDetailScreen(e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable("series") {
            SeriesScreen(onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("series/${Uri.encode(id)}") })
        }
        composable("series/{id}") { e ->
            SeriesDetailScreen(e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable("offline") {
            OfflineScreen(onBack = { nav.popBackStack() }, onOpenSeries = { key -> nav.navigate("offline/series/${Uri.encode(key)}") })
        }
        composable("offline/series/{key}") { e ->
            OfflineSeriesScreen(e.arguments?.getString("key").orEmpty(), onBack = { nav.popBackStack() })
        }
    }
}
