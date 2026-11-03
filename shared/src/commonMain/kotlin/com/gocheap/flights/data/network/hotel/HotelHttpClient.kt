package com.gocheap.flights.data.network.hotel

import com.gocheap.flights.data.user.platformUserAgent
import com.gocheap.flights.utils.Constants
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import com.gocheap.flights.data.network.ApiLogger
import com.gocheap.flights.data.network.apiLogLevel
import com.gocheap.flights.utils.BaseUrls
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient as KtorHttpClient

/**
 * The one HTTP client the stay endpoints go through.
 *
 * No engine is named: Ktor picks the single one each target puts on its classpath — OkHttp on
 * Android, Darwin on iOS — so this stays in common code rather than needing an expect/actual pair.
 *
 * Built lazily and shared, because a client owns a connection pool and a dispatcher; one per call
 * would leak both. It lives as long as the process, which is why nothing closes it.
 *
 * This object and Ktor's own client class share a name, so Ktor's is aliased rather than imported
 * plainly — without that, `HttpClient { }` below resolves to this object and fails to compile.
 */
internal object HotelHttpClient {

    val instance: KtorHttpClient by lazy {
        KtorHttpClient {
            // Lets a 4xx/5xx surface as a thrown response rather than a body that fails to parse.
            expectSuccess = true

            defaultRequest {
                url(BaseUrls.Hotel)
                accept(ContentType.Application.Json)
                // Validated by the backend — see FlightHttpClient. Ktor's default is rejected.
                platformUserAgent.takeIf(String::isNotBlank)
                    ?.let { header(HttpHeaders.UserAgent, it) }
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        // The supplier adds fields faster than this app tracks them, and sends some
                        // numbers as strings; neither should fail a search.
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }

            install(HttpTimeout) {
                // Type-ahead: a request slower than this is already stale, so fail and let the next
                // keystroke try again rather than leaving the spinner up.
                requestTimeoutMillis = 15_000
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
