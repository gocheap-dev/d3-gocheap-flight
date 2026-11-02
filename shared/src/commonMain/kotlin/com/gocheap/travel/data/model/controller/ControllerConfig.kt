package com.gocheap.travel.data.model.controller

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What `GET /controller/` answers with: the switches the backend holds over this build, resolved
 * per country, app and acquisition route.
 *
 * The same object the reference app receives, stores and reads its ad decisions off. The defaults
 * below are its defaults and they matter: the call is made once at launch and is allowed to fail,
 * and a failure stores this object as-is — so "the backend never answered" has to mean the app
 * behaves as it would have without a controller at all, not that every switch reads false.
 *
 * Kept whole rather than unpacked into the fields this app happens to use today. It is one small
 * record, the backend adds keys to it without notice, and a later feature wanting one of them
 * should find it already stored rather than have to add it to a call it did not know about.
 */
@Serializable
data class ControllerConfig(
    /** Echoed back from the request, which is how a stale stored copy is recognisable. */
    @SerialName("app") val app: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("route") val route: String? = null,
    /** Flips which of a pair of ad placements is shown. Unused here; this app has no ads yet. */
    @SerialName("inverse_ad_logic") val inverseAdLogic: Boolean? = null,
    /** Which tab the home screen should open on, by the backend's own name for it. */
    @SerialName("current_tab") val currentTab: String? = null,
    @SerialName("new_tab") val newTab: String? = null,
    @SerialName("is_popup_ads_enabled") val isPopupAdsEnabled: Boolean? = true,
    @SerialName("is_ads_enabled") val isAdsEnabled: Boolean? = true,
    @SerialName("is_app_open_popup_ad_enabled") val isAppOpenPopupAdEnabled: Boolean? = true,
    @SerialName("is_loader_ad_enabled") val isLoaderAdEnabled: Boolean? = true,
)
