package com.simulagamer.filedesk

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val incomingUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri.value = extractIncomingUri(intent)

        setContent {
            var darkMode = androidx.compose.runtime.remember { mutableStateOf(false) }
            MaterialTheme(
                colorScheme = if (darkMode.value) androidx.compose.material3.darkColorScheme()
                else androidx.compose.material3.lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ExplorerScreen(
                        darkMode = darkMode.value,
                        onToggleTheme = { darkMode.value = !darkMode.value },
                        incomingUri = incomingUri.value,
                        onIncomingHandled = { incomingUri.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = extractIncomingUri(intent)
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
