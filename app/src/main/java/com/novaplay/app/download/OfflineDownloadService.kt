package com.novaplay.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.novaplay.app.MainActivity
import com.novaplay.app.R
import com.novaplay.app.data.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Télécharge les films / épisodes un par un, avec UNE seule connexion HTTP par fichier
 * (les serveurs IPTV limitent souvent le nombre de connexions), reprise après coupure
 * via l'en-tête Range, et redirections http <-> https.
 */
class OfflineDownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "offline"
        private const val NOTIF_ID = 2001
        private const val MAX_ATTEMPTS = 5

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, OfflineDownloadService::class.java))
        }
    }

    private class Stopped : Exception()
    private class Fatal(msg: String) : IOException(msg)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovaPlay:download")
            .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        @Suppress("DEPRECATION")
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "NovaPlay:download")
            .apply { setReferenceCounted(false); acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, progressNotification("Préparation…", 0, 0, 0), type)
        if (job?.isActive != true) {
            job = scope.launch {
                runQueue()
                withContext(Dispatchers.Main) {
                    ServiceCompat.stopForeground(this@OfflineDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 : limite de durée des services « dataSync » atteinte → on met en pause proprement. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        job?.cancel()
        Offline.requeueActive()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        Offline.requeueActive()
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        super.onDestroy()
    }

    // ---------------- file d'attente ----------------

    private suspend fun runQueue() {
        while (true) {
            currentCoroutineContext().ensureActive()
            val next = Offline.items.value.firstOrNull { it.state == OfflineState.QUEUED } ?: return
            download(next.id)
        }
    }

    private suspend fun download(id: String) {
        Offline.update(id) { it.copy(state = OfflineState.DOWNLOADING, error = null, speedBps = 0) }
        var attempt = 0
        while (true) {
            val before = Offline.get(id)?.downloadedBytes ?: return
            try {
                transfer(id)
                Offline.get(id)?.let { notifyDone(it) }
                return
            } catch (e: Stopped) {
                // Pause ou suppression demandée par l'utilisateur (le fichier est déjà supprimé dans ce cas).
                return
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                val cur = Offline.get(id) ?: return
                if (cur.state != OfflineState.DOWNLOADING) return
                attempt = if (cur.downloadedBytes > before) 1 else attempt + 1
                if (e is Fatal || attempt >= MAX_ATTEMPTS) {
                    Offline.update(id) {
                        it.copy(state = OfflineState.FAILED, speedBps = 0, error = e.message ?: e.javaClass.simpleName)
                    }
                    return
                }
                Offline.update(id, persist = false) {
                    it.copy(speedBps = 0, error = "Nouvelle tentative ($attempt/$MAX_ATTEMPTS) : ${e.message}")
                }
                delay(3_000L * attempt)
            }
        }
    }

    private suspend fun transfer(id: String) {
        val item = Offline.get(id) ?: throw Stopped()
        val file = Offline.file(item)
        var existing = if (file.exists()) file.length() else 0L

        val conn = open(item.url, existing)
        try {
            val code = conn.responseCode
            if (code == 416) {
                // Le serveur dit qu'il n'y a plus rien à envoyer : fichier déjà complet ?
                if (item.totalBytes > 0 && existing >= item.totalBytes) {
                    Offline.update(id) { it.copy(state = OfflineState.COMPLETED, downloadedBytes = existing, speedBps = 0, error = null) }
                    return
                }
                file.delete()
                throw IOException("Reprise impossible, redémarrage du fichier")
            }
            if (code !in 200..299) {
                val hint = when (code) {
                    401, 403, 458, 509 -> " — le serveur refuse : ton abonnement n'autorise peut-être qu'une connexion à la fois (arrête la lecture en cours)"
                    404 -> " — fichier introuvable sur le serveur"
                    else -> ""
                }
                throw IOException("HTTP $code$hint")
            }

            val append = code == 206 && existing > 0
            if (!append) existing = 0L
            val length = conn.getHeaderField("Content-Length")?.trim()?.toLongOrNull() ?: -1L
            val total = if (length > 0) existing + length else -1L

            if (length > 0 && Offline.dir().usableSpace < length + 50L * 1024 * 1024) {
                throw Fatal("Espace insuffisant : il faut ${DownloadUtil.formatBytes(length)} libres")
            }
            Offline.update(id) { it.copy(totalBytes = total, downloadedBytes = existing) }

            var done = existing
            var lastTick = 0L
            var lastSave = SystemClock.elapsedRealtime()
            var windowStart = SystemClock.elapsedRealtime()
            var windowBytes = 0L
            var speed = 0L

            FileOutputStream(file, append).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        windowBytes += n

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastTick >= 700) {
                            currentCoroutineContext().ensureActive()
                            val cur = Offline.get(id)
                            if (cur == null || cur.state != OfflineState.DOWNLOADING) throw Stopped()
                            if (now - windowStart >= 1500) {
                                speed = windowBytes * 1000 / (now - windowStart)
                                windowStart = now
                                windowBytes = 0
                            }
                            val persist = now - lastSave > 5_000
                            if (persist) lastSave = now
                            val d = done
                            val s = speed
                            Offline.update(id, persist) { it.copy(downloadedBytes = d, speedBps = s, error = null) }
                            notifyProgress(cur.title, done, total, speed)
                            lastTick = now
                        }
                    }
                }
            }

            if (total > 0 && done < total) throw IOException("Connexion interrompue à ${DownloadUtil.formatBytes(done)}")
            if (done == 0L) throw IOException("Le serveur n'a envoyé aucune donnée")

            val finalSize = done
            Offline.update(id) {
                it.copy(
                    state = OfflineState.COMPLETED,
                    downloadedBytes = finalSize,
                    totalBytes = if (total > 0) total else finalSize,
                    speedBps = 0,
                    error = null,
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Ouvre la connexion en suivant les redirections (y compris http → https). */
    private fun open(startUrl: String, from: Long): HttpURLConnection {
        var url = startUrl
        repeat(8) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            c.setRequestProperty("Accept", "*/*")
            if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirection invalide (HTTP $code)")
                url = URL(URL(url), location).toString()
            } else {
                return c
            }
        }
        throw IOException("Trop de redirections")
    }

    // ---------------- notifications ----------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(title: String, done: Long, total: Long, speed: Long): Notification {
        val pct = if (total > 0) ((done * 100) / total).toInt() else 0
        val text = buildString {
            append(DownloadUtil.formatBytes(done))
            if (total > 0) append(" / ").append(DownloadUtil.formatBytes(total))
            if (speed > 0) append(" · ").append(DownloadUtil.formatSpeed(speed))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(if (done > 0) text else "Connexion au serveur…")
            .setProgress(100, pct, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun notifyProgress(title: String, done: Long, total: Long, speed: Long) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, progressNotification(title, done, total, speed))
        }
    }

    private fun notifyDone(item: OfflineItem) {
        if (item.state != OfflineState.COMPLETED) return
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Disponible offline")
            .setContentText(item.title)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(item.id.hashCode(), n) }
    }
}
