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
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Windows11LightColors = lightColorScheme(
    primary = Color(0xFF0067C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EBF7),
    onPrimaryContainer = Color(0xFF003B5E),
    secondary = Color(0xFF005A9E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5F3FF),
    onSecondaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFF3F3F3),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF9F9F9),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEFEFEF),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFFD6D6D6)
)

private val Windows11DarkColors = darkColorScheme(
    primary = Color(0xFF60CDFF),
    onPrimary = Color(0xFF00364A),
    primaryContainer = Color(0xFF164A63),
    onPrimaryContainer = Color(0xFFD7F3FF),
    secondary = Color(0xFF60CDFF),
    onSecondary = Color(0xFF00364A),
    secondaryContainer = Color(0xFF243F4D),
    onSecondaryContainer = Color(0xFFE8F7FF),
    background = Color(0xFF202020),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF272727),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF323232),
    onSurfaceVariant = Color(0xFFD2D2D2),
    outline = Color(0xFF9A9A9A),
    outlineVariant = Color(0xFF474747)
)

private val Windows11Shapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
)

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
                colorScheme = if (darkMode.value) Windows11DarkColors else Windows11LightColors,
                shapes = Windows11Shapes
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
