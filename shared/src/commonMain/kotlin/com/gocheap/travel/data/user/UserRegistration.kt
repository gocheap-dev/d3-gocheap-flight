package com.gocheap.travel.data.user

import com.gocheap.travel.utils.logDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs `users/add` once per launch, on a scope that outlives the screen that started it.
 *
 * Deliberately not scoped to a ViewModel. The splash is on screen for well under two seconds and a
 * cold sign-up has three attempts and a network round trip to get through; tying this to
 * `viewModelScope` would cancel it the moment the splash navigated away, so a slow connection would
 * leave the install unregistered every launch and never finish.
 *
 * Nor does it gate the splash. The reference app waits for this before showing its first screen;
 * here the user id is not needed to render anything — the search tabs and the supplier deeplinks
 * work with [platformAnonymousUserId] until one exists — so making the launch wait on a network
 * call would buy a slower start and nothing else. When the id matters, [UserSession] is where it
 * is read from.
 */
internal object UserRegistration {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = UserRepository()

    /** Guards the check-and-start below; [start] is called from composition and can race itself. */
    private val mutex = Mutex()
    private var inFlight: Job? = null

    /**
     * Starts the registration unless this install already has an id, or an attempt is still
     * running. Returns immediately either way — nothing on screen depends on the outcome.
     */
    fun start() {
        if (UserSession.current.isRegistered) {
            logDebug(
                AddUserLogTag,
                "START skipped — already registered as user_id=${UserSession.current.userId}",
            )
            return
        }

        scope.launch {
            mutex.withLock {
                if (UserSession.current.isRegistered) return@withLock
                if (inFlight?.isActive == true) {
                    logDebug(AddUserLogTag, "START skipped — an attempt is still running")
                    return@withLock
                }
                logDebug(AddUserLogTag, "START users/add — this install has no user_id yet")
                inFlight = scope.launch { repository.addUserIfNeeded() }
            }
        }
    }
}
