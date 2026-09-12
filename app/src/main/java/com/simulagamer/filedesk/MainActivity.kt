package com.simulagamer.filedesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkMode by remember { mutableStateOf(false) }
            MaterialTheme(
                colorScheme = if (darkMode) androidx.compose.material3.darkColorScheme()
                else androidx.compose.material3.lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ExplorerScreen(
                        darkMode = darkMode,
                        onToggleTheme = { darkMode = !darkMode }
                    )
                }
            }
        }
    }
}
