package com.novaplay.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.novaplay.app.download.Offline
import com.novaplay.app.download.OfflineDownloadService
import com.novaplay.app.ui.App
import com.novaplay.app.ui.NovaTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Notification de progression des téléchargements (Android 13+).
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Relance les téléchargements interrompus (app fermée, redémarrage…).
        if (Offline.hasQueued()) runCatching { OfflineDownloadService.start(this) }
        setContent { NovaTheme { App() } }
    }
}
