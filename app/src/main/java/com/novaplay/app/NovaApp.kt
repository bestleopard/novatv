package com.novaplay.app

import android.app.Application
import com.novaplay.app.data.Store
import com.novaplay.app.download.Offline
import java.io.File

class NovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Offline.init(this)
        // Nettoyage de l'ancien système de téléchargement (version 1.0).
        Thread {
            runCatching { File(getExternalFilesDir(null) ?: filesDir, "downloads").deleteRecursively() }
            runCatching { deleteDatabase("exoplayer_internal.db") }
        }.start()
    }
}
