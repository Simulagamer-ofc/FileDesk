package com.simulagamer.filedesk

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val incomingUri = mutableStateOf<Uri?>(null)
    private val storageAccess = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri.value = extractIncomingUri(intent)
        refreshStorageAccess()

        setContent {
            val darkMode = androidx.compose.runtime.remember { mutableStateOf(false) }
            MaterialTheme(
                colorScheme = if (darkMode.value) androidx.compose.material3.darkColorScheme()
                else androidx.compose.material3.lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ExplorerScreen(
                        darkMode = darkMode.value,
                        onToggleTheme = { darkMode.value = !darkMode.value },
                        incomingUri = incomingUri.value,
                        onIncomingHandled = { incomingUri.value = null },
                        allFilesAccess = storageAccess.value,
                        onRequestAllFilesAccess = { requestAllFilesAccess() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStorageAccess()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = extractIncomingUri(intent)
    }

    private fun refreshStorageAccess() {
        storageAccess.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appUri = Uri.parse("package:$packageName")
            val direct = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, appUri)
            val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            runCatching { startActivity(direct) }.onFailure { startActivity(fallback) }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
    }
}
