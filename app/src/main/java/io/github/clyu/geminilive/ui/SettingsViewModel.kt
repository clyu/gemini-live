package io.github.clyu.geminilive.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.clyu.geminilive.data.LiveSettings
import io.github.clyu.geminilive.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    /** Null until the stored settings have been loaded. */
    val settings: StateFlow<LiveSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun save(settings: LiveSettings) {
        viewModelScope.launch { repository.save(settings) }
    }
}
