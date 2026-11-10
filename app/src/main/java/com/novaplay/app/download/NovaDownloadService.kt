package com.novaplay.app.download

import android.app.Notification
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.novaplay.app.R

/** Service au premier plan qui poursuit les téléchargements même si l'app est fermée. */
class NovaDownloadService : DownloadService(
    1001,
    DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadUtil.CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    private val helper by lazy { DownloadNotificationHelper(this, DownloadUtil.CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = DownloadUtil.manager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification = helper.buildProgressNotification(
        this, R.drawable.ic_download, null, null, downloads, notMetRequirements,
    )
}
