package com.gocheap.flights.data.network.user

import com.gocheap.flights.data.user.platformUserAgent
import com.gocheap.flights.utils.BaseUrls
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import com.gocheap.flights.data.network.ApiLogger
import com.gocheap.flights.data.network.apiLogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient as KtorHttpClient

/**
 * The one HTTP client the user and session endpoints go through — a fourth host alongside the stay,
 * rental and flight ones, built the same way and for the same reasons. See
 * [com.gocheap.flights.data.network.car.CarHttpClient] for why there is no engine named here, why
 * it is a lazy singleton, and why Ktor's class is aliased.
 *
 * This one is a plain Ktor client on both targets rather than Retrofit/OkHttp as in the reference
 * app: OkHttp is Android-only, and this call has to run identically on iOS. Ktor resolves its engine
 * off whatever each target contributes — OkHttp on Android, `NSURLSession` through Darwin on iOS —
 * so the request built here is the same request on both.
 */
internal object UserHttpClient {

    /**
     * Named rather than configured inline so the wire format can be asserted directly — the body
     * this produces is the contract with the backend, and a test is the only thing that keeps a
     * later `explicitNulls = false` from quietly changing it.
     */
    val UserJson = Json {
        // The response carries an `attribution` object whose keys depend on the ad network that
        // produced the install, and new ones appear without notice.
        ignoreUnknownKeys = true
        isLenient = true
        // Both reference apps send nulls on the wire — Moshi's `withNullSerialization()` on
        // Android, and the iOS client's own body — and this endpoint is specified against that
        // shape, so a field this install cannot fill goes out as `"referrer_url": null` rather
        // than being omitted.
        //
        // This is what it takes, and `explicitNulls` alone is not enough: every property of
        // `AddUserRequest` defaults to null, and without `encodeDefaults` kotlinx drops a property
        // sitting on its default before the null handling is ever consulted. Leaving it off
        // silently shortened the body to whichever fields happened to be set.
        encodeDefaults = true
    }

    val instance: KtorHttpClient by lazy {
        KtorHttpClient {
            // Lets a 4xx/5xx surface as a thrown response rather than a body that fails to parse.
            // `UserRepository` is what turns that back into a retry or a false.
            expectSuccess = true

            defaultRequest {
                url(BaseUrls.User)
                accept(ContentType.Application.Json)
                // The backend logs this against the install; the reference app sends the same
                // three headers on every session call.
                platformUserAgent.takeIf(String::isNotBlank)
                    ?.let { header(HttpHeaders.UserAgent, it) }
            }

            install(ContentNegotiation) {
                json(UserJson)
            }

            install(HttpTimeout) {
                // Generous next to the autocomplete clients' 15s, and deliberately so: this runs
                // once per install behind the splash, where a slow answer is still worth waiting
                // for. The reference app allows the same 60.
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }

            install(Logging) {
                // Through the app's own logger and at body level on a debug build — see ApiLogger.
                logger = ApiLogger
                level = apiLogLevel
            }
        }
    }
}
