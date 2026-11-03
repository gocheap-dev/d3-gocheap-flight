package com.gocheap.flights.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.flights.analytics.ga4.FlightAnalytics
import com.gocheap.flights.data.controller.AppController
import com.gocheap.flights.data.settings.AppLanguageController
import com.gocheap.flights.data.settings.RegionDefaults
import com.gocheap.flights.data.settings.Regions
import com.gocheap.flights.data.onboarding.OnboardingStore
import com.gocheap.flights.data.user.PushTokenRegistration
import com.gocheap.flights.data.user.UserRegistration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SplashDestination { Onboarding, Home }

data class SplashUiState(
    val destination: SplashDestination? = null,
)

/**
 * Decides where the splash hands off to.
 *
 * The flag is read once, here, rather than watched: whether the intro has been seen cannot change
 * while this screen is on top of it, and a value read after the decision would arrive too late to
 * matter. Still a constructor parameter so a test can state the answer instead of writing one to
 * the device.
 */
class SplashViewModel(
    private val hasCompletedOnboarding: Boolean = OnboardingStore.hasCompleted(),
) : ViewModel() {
    private val _state = MutableStateFlow(SplashUiState())
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    init {
        // Before the sign-up rather than after it, because this is what subscribes to the
        // platform's "app went to the background" signal, and a launch that is backgrounded while
        // the sign-up is still in flight should still report the close. Idempotent, so a second
        // splash in one process — which only a test produces — does not double-subscribe.
        FlightAnalytics.start()

        // The first screen of the launch, and so the first point at which storage is attached and
        // the install can be registered. Fire-and-forget: it runs on its own process-lifetime
        // scope, and nothing below waits on it — see [UserRegistration].
        UserRegistration.start()

        // After it only in reading order — this waits for a user id rather than requiring one, so
        // the order of these two lines does not matter. Started here because it has to be watching
        // before the sign-up and the token land, which on a warm launch is almost immediately.
        PushTokenRegistration.start()

        // Alongside the sign-up rather than after it, as the reference app fetches the two: the
        // controller is keyed on country and route, neither of which the registration produces.
        // Also fire-and-forget — every switch it carries has a working default.
        //
        // `start` rather than `refresh`: the answer is per country, so this fetches now and again
        // whenever the region changes — including the change detection itself is about to make,
        // moments from now. Fetching once here would have asked about the shipped default and
        // then routed every flight search by the wrong country's answer. See AppController.start.
        AppController.start()

        // The catalogue the settings pages read their names and flags from, and which a detected
        // country is resolved against. Started here because this is the first screen of the launch
        // and the read is local; nothing waits on it.
        // Before anything is drawn: the stored language has to reach the platform, or the first
        // screens come up in the device's language and change under the user a moment later.
        AppLanguageController.sync()

        viewModelScope.launch {
            Regions.load()
            // Strictly after the catalogue: this resolves a country code into a row to take a
            // currency from, and without one it would adopt a country with no currency behind it.
            RegionDefaults.apply()
        }

        viewModelScope.launch {
            delay(SplashDurationMillis)
            val destination = if (hasCompletedOnboarding) {
                SplashDestination.Home
            } else {
                SplashDestination.Onboarding
            }
            _state.update { it.copy(destination = destination) }
        }
    }

    private companion object {
        /**
         * How long the splash is held before the app moves on.
         *
         * Cosmetic only — nothing behind it is waited for. The sign-up, the controller fetch and
         * the region catalogue all run on scopes that outlive this screen, and every one of them
         * has a working answer before it finishes, so shortening this delays nothing and skips
         * nothing.
         *
         * The animation timeline in `SplashScreen.kt` is sized against this and has to move with
         * it: everything there lands by ~820ms, which leaves the mark a beat to sit still before
         * the screen goes. Cut this much below that and the splash navigates away mid-animation,
         * which reads as a dropped frame rather than as a flourish.
         */
        const val SplashDurationMillis = 2500L
    }
}
