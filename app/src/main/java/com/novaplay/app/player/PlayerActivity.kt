package com.novaplay.app.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.novaplay.app.data.Store
import com.novaplay.app.download.DownloadUtil

class PlayerActivity : Activity() {

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LIVE = "live"
        private const val EXTRA_RESUME = "resume"
        private const val EXTRA_DOWNLOAD = "download"
        private const val EXTRA_FROM_START = "fromStart"

        fun start(
            ctx: Context,
            url: String,
            title: String,
            live: Boolean,
            resumeKey: String? = null,
            downloadId: String? = null,
            fromStart: Boolean = false,
        ) {
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_LIVE, live)
                    .putExtra(EXTRA_RESUME, resumeKey)
                    .putExtra(EXTRA_DOWNLOAD, downloadId)
                    .putExtra(EXTRA_FROM_START, fromStart)
            )
        }
    }

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var firstStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setShowNextButton(false)
            setShowPreviousButton(false)
        }
        setContentView(playerView)
        hideSystemUi()
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, playerView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun initPlayer() {
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val live = intent.getBooleanExtra(EXTRA_LIVE, false)
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD)
        val resumeKey = intent.getStringExtra(EXTRA_RESUME)

        // Live : réseau direct. VOD : fichier téléchargé si présent, sinon réseau.
        val dataSourceFactory = if (live) DownloadUtil.httpFactory() else DownloadUtil.playbackFactory(this)

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        val downloaded = downloadId?.let {
            runCatching { DownloadUtil.manager(this).downloadIndex.getDownload(it) }.getOrNull()
        }
        val item = downloaded?.request?.toMediaItem() ?: MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (url.contains(".m3u8", ignoreCase = true)) MimeTypes.APPLICATION_M3U8 else null)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

        p.setMediaItem(item)

        if (!live && resumeKey != null) {
            if (firstStart && intent.getBooleanExtra(EXTRA_FROM_START, false)) Store.savePosition(resumeKey, 0L)
            val pos = Store.position(resumeKey)
            if (pos > 0) p.seekTo(pos)
        }
        firstStart = false

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (live && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    p.seekToDefaultPosition()
                    p.prepare()
                    return
                }
                Toast.makeText(
                    this@PlayerActivity,
                    "Lecture impossible : ${error.errorCodeName}",
                    Toast.LENGTH_LONG,
                ).show()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && resumeKey != null) Store.savePosition(resumeKey, 0L)
            }
        })

        p.playWhenReady = true
        p.prepare()
        playerView.player = p
        player = p
    }

    private fun releasePlayer() {
        val p = player ?: return
        val resumeKey = intent.getStringExtra(EXTRA_RESUME)
        val live = intent.getBooleanExtra(EXTRA_LIVE, false)
        if (!live && resumeKey != null && p.playbackState != Player.STATE_ENDED) {
            val duration = p.duration
            val pos = p.currentPosition
            // Si on est à moins de 2 minutes de la fin, on considère le contenu comme vu.
            val nearEnd = duration > 0 && pos > duration - 120_000
            Store.savePosition(resumeKey, if (nearEnd) 0L else pos)
        }
        playerView.player = null
        p.release()
        player = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Laisse la télécommande (D-pad, play/pause) piloter les contrôles du lecteur.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }
}
