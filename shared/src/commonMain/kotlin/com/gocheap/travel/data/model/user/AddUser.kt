package com.gocheap.travel.data.model.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The body `POST users/add/` is sent, one field per column the backend keys attribution off.
 *
 * Every field is nullable with a null default, and nulls are serialised rather than omitted: a
 * value this install cannot produce — no advertising id, no referrer on a sideloaded build — goes
 * out as `"referrer_url": null`, which is the shape both reference apps send and the shape this
 * endpoint is specified against.
 *
 * Names are the wire's, carried on [SerialName] rather than by naming the properties in snake_case:
 * the rest of this module reads camelCase, and the reference app's `AddUserBody` only gets away
 * with snake_case properties because Moshi has no equivalent.
 */
@Serializable
internal data class AddUserRequest(
    @SerialName("device_id") val deviceId: String? = null,
    /** How [deviceId] was obtained — `gps_adid`, `cst_id`, `idfa` or `uuid`. */
    @SerialName("device_id_type") val deviceIdType: String? = null,
    /** The per-app, per-platform label from `Constants.App.Android.Label`; a wrong one is rejected. */
    @SerialName("app") val app: String? = null,
    @SerialName("vendor_id") val vendorId: String? = null,
    @SerialName("pseudo_id") val pseudoId: String? = null,
    @SerialName("acquired_route") val acquiredRoute: String? = null,
    @SerialName("referrer_url") val referrerUrl: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("email_consent") val emailConsent: Boolean? = null,
)

/**
 * What the endpoint answers with.
 *
 * [userId] is the whole point of the call: it is what every later user endpoint is keyed on and
 * what the supplier deeplinks identify this install by, in place of [com.gocheap.travel.data.user.platformAnonymousUserId].
 */
@Serializable
data class AddUserResponse(
    @SerialName("msg") val msg: String? = null,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("attribution") val attribution: Attribution? = null,
)

/**
 * Which campaign this install came from, as the backend resolved it from the referrer.
 *
 * Stored rather than read once: [campaignId] goes back out as the controller endpoint's `orKey`,
 * long after the sign-up that produced it.
 */
@Serializable
data class Attribution(
    @SerialName("ad_id") val adId: String? = null,
    @SerialName("adgroup_id") val adGroupId: String? = null,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("ad_objective_name") val adObjectiveName: String? = null,
    @SerialName("campaign_group_id") val campaignGroupId: String? = null,
    @SerialName("campaign_id") val campaignId: String? = null,
)
