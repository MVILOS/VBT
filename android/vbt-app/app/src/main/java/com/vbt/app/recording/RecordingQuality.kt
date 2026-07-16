package com.vbt.app.recording

import androidx.camera.video.Quality

/**
 * Jakość nagrywania wideo wybierana w Ustawieniach. [key] trafia do DataStore
 * (PreferencesManager.getRecordingQualityKey). Mapuje się na CameraX [Quality];
 * jeśli urządzenie nie wspiera danej rozdzielczości, SetRecorder schodzi do niższej.
 */
enum class RecordingQuality(val key: String, val label: String) {
    SD("sd", "SD (480p)"),
    HD("hd", "HD (720p)"),
    FHD("fhd", "Full HD (1080p)"),
    UHD("uhd", "4K (2160p)");

    fun toCameraQuality(): Quality = when (this) {
        SD -> Quality.SD
        HD -> Quality.HD
        FHD -> Quality.FHD
        UHD -> Quality.UHD
    }

    companion object {
        val DEFAULT = FHD
        fun fromKey(key: String?): RecordingQuality = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
