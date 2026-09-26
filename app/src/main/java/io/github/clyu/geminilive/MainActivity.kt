package io.github.clyu.geminilive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.clyu.geminilive.ui.ChatScreen
import io.github.clyu.geminilive.ui.ChatViewModel
import io.github.clyu.geminilive.ui.SettingsScreen
import io.github.clyu.geminilive.ui.theme.GeminiLiveTheme

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeminiLiveTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    SettingsScreen(onBack = { showSettings = false })
                } else {
                    ChatScreen(onOpenSettings = { showSettings = true }, viewModel = chatViewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Once in the background the process can be killed without any further callback, for
        // example when the app is swiped away from the recent apps screen.
        chatViewModel.saveTranscript()
    }
}
