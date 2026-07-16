package com.vbt.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.local.PreferencesManager
import com.vbt.app.recording.OverlayMetric
import com.vbt.app.recording.RecordingQuality
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

    val recordingQuality: StateFlow<RecordingQuality> =
        preferencesManager.getRecordingQualityKey()
            .map { RecordingQuality.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordingQuality.DEFAULT)

    fun toggleMetric(metric: OverlayMetric) {
        viewModelScope.launch {
            // Atomowe przełączenie w DataStore - bez wyścigu read-modify-write.
            preferencesManager.toggleOverlayMetric(
                key = metric.key,
                defaultKeys = OverlayMetric.DEFAULT.map { it.key }.toSet()
            )
        }
    }

    fun setQuality(quality: RecordingQuality) {
        viewModelScope.launch {
            preferencesManager.setRecordingQualityKey(quality.key)
        }
    }
}
