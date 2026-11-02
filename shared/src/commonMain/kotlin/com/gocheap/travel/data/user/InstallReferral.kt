package com.gocheap.travel.data.user

import com.gocheap.travel.utils.Constants

/**
 * Where this install came from, as `users/add` is told it.
 *
 * [route] is the classified source — "google", "facebook", "organic" and so on — and goes out as
 * `acquired_route`; [referrerUrl] is the raw string it was classified from, which the backend
 * re-parses for the campaign it resolves the response's `attribution` from.
 */
/**
 * What the backend is told when nothing attributed this install — as opposed to
 * `Constants.Defaults.Route` ("organic"), which is the positive claim that it came from a store
 * listing rather than an ad. The two are not interchangeable.
 */
internal const val UnattributedRoute = "unknown"

internal data class InstallReferral(
    val route: String = Constants.Defaults.Route,
    val referrerUrl: String = "",
    /** Google's click identifier, pulled out separately because the backend indexes on it. */
    val gclid: String = "",
)

/**
 * Reads the install referrer, where the platform has one.
 *
 * Returns the organic default rather than failing: a sideloaded build, a store that does not
 * report referrers, and an install that genuinely was organic are indistinguishable from here, and
 * all three should still register.
 */
internal expect suspend fun resolveInstallReferral(): InstallReferral

/**
 * Classifies a referrer into the route the backend groups installs by.
 *
 * Pure string work, so it lives in common code and is tested there — the reference app's
 * `detectSource`, with the same source lists and the same precedence. The order matters and is not
 * alphabetical: `medium == "organic"` is checked before the Google list because a Play Store
 * organic install carries `utm_source=google-play`, which would otherwise be read as a Google ad.
 */
internal fun classifyInstallRoute(
    utmSource: String,
    utmMedium: String,
    referrerUrl: String,
): String {
    val source = utmSource.lowercase()
    val medium = utmMedium.lowercase()
    val referrer = referrerUrl.lowercase()

    return when {
        FacebookSources.any(source::contains) -> "facebook"
        InstagramSources.any(source::contains) -> "instagram"
        TikTokSources.any(source::contains) || referrer.contains("ttclid=") -> "tiktok"
        SnapchatSources.any(source::contains) ||
            referrer.contains("sccid=") ||
            referrer.contains("sc_click_id=") -> "snapchat"
        medium == "organic" || source.contains("google-play") -> "organic"
        referrer.contains("gclid=") || GoogleSources.any(source::contains) -> "google"
        BingSources.any(source::contains) -> "bing"
        else -> UnattributedRoute
    }
}

/**
 * Splits a referrer string into its `key=value` pairs.
 *
 * Pairs that are not exactly one `=` apart are dropped rather than guessed at — a referrer is
 * attacker-influenceable in the sense that anyone can craft a Play Store link, and a malformed one
 * should cost a field, not the sign-up.
 */
internal fun parseReferrerParameters(referrerUrl: String): Map<String, String> =
    referrerUrl.split("&").mapNotNull { pair ->
        val parts = pair.split("=")
        if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
    }.toMap()

/** Builds the referral from an already-read referrer string, shared by every platform that has one. */
internal fun installReferralFrom(referrerUrl: String): InstallReferral {
    val parameters = parseReferrerParameters(referrerUrl)
    return InstallReferral(
        route = classifyInstallRoute(
            utmSource = parameters["utm_source"].orEmpty(),
            utmMedium = parameters["utm_medium"].orEmpty(),
            referrerUrl = referrerUrl,
        ),
        referrerUrl = referrerUrl,
        gclid = parameters["gclid"].orEmpty(),
    )
}

private val FacebookSources = listOf(
    "facebook", "facebook.com", "apps.facebook.com", "business.facebook.com",
    "free.facebook.com", "lm.facebook.com", "l.facebook.com", "m.facebook.com",
    "mobile.facebook.com", "touch.facebook.com", "web.facebook.com", "fb", "fb.me",
)

private val InstagramSources = listOf(
    "instagram", "instagram.com", "l.instagram.com", "apps.instagram.com", "igshopping", "ig",
)

private val TikTokSources = listOf(
    "tiktok", "tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com",
    "tiktok-ads", "tiktok_ads", "tiktokads", "musically", "bytedance",
)

private val SnapchatSources = listOf(
    "snapchat", "snapchat.com", "www.snapchat.com", "business.snapchat.com",
    "snap", "snap-ads", "snap_ads", "snapads", "snapchat-ads", "snapchat_ads",
)

private val GoogleSources = listOf(
    "google", "google.com", "google-ads", "googleplus", "plus.google.com",
    "sites.google.com", "googlegroups.com", "groups.google.com", "play.google.com",
    "lens.google.com", "news.google.com", "search.google.com", "plus.url.google.com",
    "messages.google.com", "music.youtube.com", "google shopping", "m.youtube.com",
    "youtube.com", "youtube", "goo.gl",
)

private val BingSources = listOf("bing-ads", "bing")
