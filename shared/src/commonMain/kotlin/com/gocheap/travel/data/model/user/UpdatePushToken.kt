package com.gocheap.travel.data.model.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The body `POST users/update-push-token/` is sent.
 *
 * All six fields are required — the endpoint rejects a body missing any one of them with a 400
 * naming the field, in this order — so unlike [AddUserRequest] nothing here is nullable. Sending
 * one of them blank would be accepted and would store a blank, which is worse than the 400.
 *
 * Names are the wire's, carried on [SerialName] for the same reason as [AddUserRequest]: the rest
 * of this module reads camelCase.
 */
@Serializable
internal data class UpdatePushTokenRequest(
    /** The id `users/add/` produced. The endpoint answers "User not found" for anything else. */
    @SerialName("user_id") val userId: Int,
    /** The identifier this install registered under — `UserSessionState.deviceId`, not a fresh one. */
    @SerialName("device_id") val deviceId: String,
    /**
     * The FCM registration token.
     *
     * `token`, not `push_token`: the endpoint ignores the latter and then rejects the body for a
     * missing `token`, which is a confusing way to find out and worth naming here.
     */
    @SerialName("token") val token: String,
    /** The user's chosen country, uppercased, as the rest of the app sends it. */
    @SerialName("country_code") val countryCode: String,
    /** IANA, as the platform reports it — `Asia/Kolkata`, not an offset. */
    @SerialName("timezone") val timezone: String,
    @SerialName("language") val language: String,
)

/**
 * What the endpoint answers with.
 *
 * Only `msg` is read. The call is fire-and-forget — nothing on screen depends on it — so there is
 * nothing to branch on, and the field is here so a failure that arrives as a 2xx has somewhere to
 * be logged from. Unknown keys are ignored by `UserHttpClient.UserJson`, so a richer response
 * than this does not break the call.
 */
@Serializable
internal data class UpdatePushTokenResponse(
    @SerialName("msg") val msg: String? = null,
)
