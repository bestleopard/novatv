package com.novaplay.app.download

import androidx.media3.datasource.DefaultHttpDataSource
import com.novaplay.app.data.Http
import java.util.Locale

/** Petits utilitaires réseau / affichage partagés par le lecteur et les téléchargements. */
object DownloadUtil {

    fun httpFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

    fun formatBytes(b: Long): String = when {
        b < 0 -> "?"
        b >= 1L shl 30 -> String.format(Locale.FRANCE, "%.2f Go", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(Locale.FRANCE, "%.0f Mo", b / (1L shl 20).toDouble())
        else -> "${b / 1024} Ko"
    }

    fun formatSpeed(bps: Long): String = if (bps <= 0) "" else "${formatBytes(bps)}/s"
}
