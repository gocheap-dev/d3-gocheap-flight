package com.gocheap.travel.data.user

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The route classifier, which is what decides the `acquired_route` every install is attributed by.
 *
 * Worth tests where the rest of this flow is not: it is pure string work with a precedence order
 * that is easy to break — two of the cases below pass only because of where their branch sits.
 */
class InstallReferralTest {

    @Test
    fun a_play_store_organic_install_is_organic_not_google() {
        // The ordering trap: this carries utm_source=google-play, which the Google branch below
        // would otherwise claim.
        assertEquals(
            "organic",
            classifyInstallRoute(
                utmSource = "google-play",
                utmMedium = "organic",
                referrerUrl = "utm_source=google-play&utm_medium=organic",
            ),
        )
    }

    @Test
    fun a_google_ads_click_is_google() {
        assertEquals(
            "google",
            classifyInstallRoute(
                utmSource = "google-ads",
                utmMedium = "cpc",
                referrerUrl = "utm_source=google-ads&utm_medium=cpc&gclid=abc123",
            ),
        )
    }

    @Test
    fun a_gclid_alone_is_enough_for_google() {
        assertEquals("google", classifyInstallRoute("", "", "gclid=abc123"))
    }

    @Test
    fun social_sources_are_recognised_by_their_many_domains() {
        assertEquals("facebook", classifyInstallRoute("l.facebook.com", "", ""))
        assertEquals("instagram", classifyInstallRoute("l.instagram.com", "", ""))
        assertEquals("tiktok", classifyInstallRoute("vm.tiktok.com", "", ""))
        assertEquals("snapchat", classifyInstallRoute("business.snapchat.com", "", ""))
        assertEquals("bing", classifyInstallRoute("bing-ads", "", ""))
    }

    @Test
    fun a_click_id_in_the_referrer_identifies_the_network_without_a_source() {
        assertEquals("tiktok", classifyInstallRoute("", "", "ttclid=xyz"))
        assertEquals("snapchat", classifyInstallRoute("", "", "sc_click_id=xyz"))
    }

    @Test
    fun an_unrecognised_source_is_unknown_rather_than_organic() {
        // The two mean different things to the backend: "we could not attribute this" is not the
        // same claim as "this install was organic".
        assertEquals("unknown", classifyInstallRoute("some-affiliate", "cpc", "utm_source=some-affiliate"))
    }

    @Test
    fun malformed_referrer_pairs_are_dropped_rather_than_guessed_at() {
        val parsed = parseReferrerParameters("utm_source=google&broken&utm_medium=cpc&a=b=c")
        assertEquals(mapOf("utm_source" to "google", "utm_medium" to "cpc"), parsed)
    }

    @Test
    fun a_full_referrer_becomes_route_url_and_gclid() {
        val referrer = "utm_source=google-ads&utm_medium=cpc&gclid=abc123"
        val referral = installReferralFrom(referrer)

        assertEquals("google", referral.route)
        // The raw string goes back out untouched: the backend re-parses it for the campaign it
        // resolves the attribution from.
        assertEquals(referrer, referral.referrerUrl)
        assertEquals("abc123", referral.gclid)
    }
}
