package com.vbt.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.local.PreferencesManager
import com.vbt.app.recording.OverlayMetric
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val selectedMetrics: StateFlow<Set<OverlayMetric>> =
        preferencesManager.getOverlayMetricKeys()
            .map { OverlayMetric.fromKeysOrDefault(it).toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverlayMetric.DEFAULT)

    fun toggleMetric(metric: OverlayMetric) {
        viewModelScope.launch {
            val current = selectedMetrics.value
            val updated = if (metric in current) current - metric else current + metric
            preferencesManager.setOverlayMetricKeys(updated.map { it.key }.toSet())
        }
    }
}
