package com.example.timefriend.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight navigation & UI preference ViewModel.
 * Timer / History / WebDAV concerns moved to their own ViewModels.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()
    private val currentState: MainState get() = _state.value

    init {
        val savedBlur = ctx.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            .getFloat("blur_level", 10f)
        _state.value = currentState.copy(blurLevel = savedBlur)
    }

    fun switchTab(index: Int) {
        _state.value = currentState.copy(currentTab = index, showSettings = false)
    }

    fun showSettings() {
        _state.value = currentState.copy(showSettings = true)
    }

    fun hideSettings() {
        _state.value = currentState.copy(showSettings = false)
    }

    fun updateBlurLevel(value: Float) {
        _state.value = currentState.copy(blurLevel = value)
        ctx.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            .edit().putFloat("blur_level", value).apply()
    }
}
