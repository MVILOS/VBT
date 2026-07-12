package com.vbt.app.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Most między warstwą sieciową a UI: interceptor OkHttp emituje event po
 * otrzymaniu 401 (wygasły/nieprawidłowy token), a nawigacja (VbtNavGraph)
 * reaguje przekierowaniem na ekran logowania.
 */
@Singleton
class SessionExpiredNotifier @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events

    fun notifyExpired() {
        _events.tryEmit(Unit)
    }
}
