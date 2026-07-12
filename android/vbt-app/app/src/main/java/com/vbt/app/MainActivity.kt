package com.vbt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vbt.app.data.local.PreferencesManager
import com.vbt.app.data.remote.SessionExpiredNotifier
import com.vbt.app.ui.navigation.VbtNavGraph
import com.vbt.app.ui.theme.VbtTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var sessionExpiredNotifier: SessionExpiredNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VbtTheme {
                VbtNavGraph(preferencesManager, sessionExpiredNotifier)
            }
        }
    }
}
