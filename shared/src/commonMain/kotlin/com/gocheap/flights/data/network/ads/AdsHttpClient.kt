package com.gocheap.flights.data.network.ads

import com.gocheap.flights.data.network.ApiLogger
import com.gocheap.flights.data.network.apiLogLevel
import com.gocheap.flights.data.user.platformUserAgent
import com.gocheap.flights.utils.AdsCredentials
import com.gocheap.flights.utils.BaseUrls
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient as KtorHttpClient

/**
 * The one HTTP client the ad endpoints go through — a fifth host alongside the four this
 * publisher runs, built the same way. See [com.gocheap.flights.data.network.car.CarHttpClient] for
 * why there is no engine named here, why it is a lazy singleton, and why Ktor's class is aliased.
 *
 * What is different about this one is the credential. Every call on this host carries an
 * `Authorization` header, and the token behind it comes out of local.properties rather than being
 * committed — see `AdsCredentials` in the generated `BuildValues`. A checkout without it sends no
 * header at all rather than an empty one, which the host answers with a 401 and the repository
 * turns into no ads. That is the intended behaviour of a build with no ad credentials: no ads.
 */
internal object AdsHttpClient {

    val instance: KtorHttpClient by lazy {
        KtorHttpClient {
            // Lets a 4xx/5xx surface as a thrown response rather than a body that fails to parse.
            // The repository leans on this: a 401 is how an expired session announces itself, and
            // it has to arrive as an exception carrying the status to be recognised.
            expectSuccess = true

            defaultRequest {
                url(BaseUrls.Ads)
                accept(ContentType.Application.Json)
                // Validated by the backend — see FlightHttpClient. Ktor's default is rejected.
                platformUserAgent.takeIf(String::isNotBlank)
                    ?.let { header(HttpHeaders.UserAgent, it) }
                // Sent bare, with no "Bearer" in front of it: the host reads the token as the
                // whole header value, and prefixing it is a 401.
                AdsCredentials.Token.takeIf(String::isNotBlank)
                    ?.let { header(HttpHeaders.Authorization, it) }
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        // The host adds fields to an ad faster than this app renders them, and
                        // sends some numbers as strings; neither should cost the screen its ad.
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }

            install(HttpTimeout) {
                // Shorter than the search client's. Nothing waits on an ad — it appears if it
                // arrives in time and is skipped if it does not, so a slow one is better dropped
                // than left holding a request slot.
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
            }

            install(Logging) {
                // Through the app's own logger and at body level on a debug build — see ApiLogger.
                logger = ApiLogger
                level = apiLogLevel
            }
        }
    }
}
