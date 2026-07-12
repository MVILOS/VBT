package com.vbt.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vbt.app.data.sync.SessionSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VbtApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Przy każdym starcie aplikacji spróbuj dosynchronizować sesje
        // zakończone offline (no-op gdy nie ma zaległych).
        SessionSyncWorker.enqueue(this)
    }
}
