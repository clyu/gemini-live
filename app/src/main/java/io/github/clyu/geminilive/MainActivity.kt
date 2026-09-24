package io.github.clyu.geminilive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.clyu.geminilive.ui.ChatScreen
import io.github.clyu.geminilive.ui.SettingsScreen
import io.github.clyu.geminilive.ui.theme.GeminiLiveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeminiLiveTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    SettingsScreen(onBack = { showSettings = false })
                } else {
                    ChatScreen(onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}
