package com.gocheap.travel.data.user

import com.gocheap.travel.data.model.user.Attribution
import com.gocheap.travel.data.storage.PersistedValue
import com.gocheap.travel.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** No one registered yet, matching the reference app's sentinel rather than a nullable column. */
internal const val NoUserId: Int = -1

/**
 * What survives a successful `users/add`, in the shape it is stored.
 *
 * [deviceId] is kept beside [userId] deliberately: it is the pair the backend deduped on, so
 * knowing which identifier this install registered under is what makes a later change in that
 * identifier — a reset advertising id, say — diagnosable instead of mysterious.
 */
@Serializable
internal data class UserSessionState(
    val userId: Int = NoUserId,
    val deviceId: String = "",
    val deviceIdType: String = "",
    val attribution: Attribution? = null,
    val acquiredRoute: String = "",
    val referrerUrl: String = "",
) {
    val isRegistered: Boolean get() = userId != NoUserId
}

/**
 * The registered user, for as long as the install lasts.
 *
 * A singleton for the same reason as [com.gocheap.travel.data.settings.AppSettings]: it is
 * written once behind the splash and read later by code that knows nothing about the sign-up —
 * a deeplink builder that wants the id, or a support screen that wants the campaign.
 *
 * Backed by [PersistedValue], so a registered install never registers twice; that check is the
 * whole reason the reference app stores the id rather than calling the endpoint each launch.
 */
internal object UserSession {

    private val storage = PersistedValue("user_session", UserSessionState.serializer())

    private val _state = MutableStateFlow(storage.load(UserSessionState()))
    val state: StateFlow<UserSessionState> = _state.asStateFlow()

    /** Read straight through for callers outside composition — most of them, here. */
    val current: UserSessionState get() = _state.value

    /**
     * The id supplier deeplinks should identify this install by, or null before one exists — in
     * which case the callers fall back to [platformAnonymousUserId] as they do today.
     */
    val userIdOrNull: String? get() = current.userId.takeIf { it != NoUserId }?.toString()

    /**
     * The id the supplier deeplinks identify this install by.
     *
     * The registered one as soon as `users/add` has produced it — which is the point of registering
     * at all, since a search run under the fallback is attributed to nobody — and the platform's
     * anonymous id only while [userIdOrNull] is still null. Resolved per read rather than captured:
     * the sign-up finishes on its own schedule behind the splash, so a value read at launch would
     * be the fallback for the whole process even after an id had arrived.
     */
    val deeplinkUserId: String get() = userIdOrNull ?: platformAnonymousUserId

    /**
     * The acquisition route every later call repeats back — `users/session/` and `/controller/`.
     *
     * Falls back to the platform default before the sign-up has stored one, which is a real case:
     * the controller is fetched at launch, alongside a registration that has not finished yet.
     */
    val acquiredRouteOrDefault: String
        get() = current.acquiredRoute.ifBlank { Constants.Defaults.Route }

    fun save(state: UserSessionState) {
        _state.value = state
        storage.save(state)
    }
}
