package com.gocheap.flights.data.network.flight

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
 * The one HTTP client the flight endpoints go through — a third host alongside the stay and rental
 * ones, built the same way and for the same reasons. See [com.gocheap.flights.data.network.car.CarHttpClient]
 * for why there is no engine named here, why it is a lazy singleton, and why Ktor's class is aliased.
 */
internal object FlightHttpClient {

    val instance: KtorHttpClient by lazy {
        KtorHttpClient {
            // Lets a 4xx/5xx surface as a thrown response rather than a body that fails to parse.
            expectSuccess = true

            defaultRequest {
                url(BaseUrls.Flight)
                accept(ContentType.Application.Json)
                // The backend validates this and rejects a request without a recognisable one
                // outright — `{"error":"Invalid 'User-Agent' header: 'ktor-client'"}`, HTTP 400,
                // before it so much as looks at the body. Ktor's default is exactly that string,
                // so every call on this host needs the app's own. The user client has always sent
                // it; these three were relying on a default the backend does not accept.
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
