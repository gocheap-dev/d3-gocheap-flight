package com.gocheap.travel.data.network

import com.gocheap.travel.utils.isDebugBuild
import com.gocheap.travel.utils.logDebug
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger

/**
 * The tag every HTTP line this app makes is written under.
 *
 * One tag for all four hosts on purpose: a search that misbehaves usually does so because of
 * something another call did — a `user_id` that never arrived, a controller that routed the
 * search somewhere unexpected — and chasing that across four logcat filters is how it gets
 * missed. `adb logcat -s ApiLog:V` is the whole story.
 */
internal const val ApiLogTag = "ApiLog"

/**
 * Ktor's log, routed into the app's own.
 *
 * Without this Ktor writes through its own platform default, which on Android is a plain
 * `println` to stdout — it lands in logcat untagged, under whatever the process happens to be,
 * and cannot be filtered for. Sending it through [logDebug] puts every request and response under
 * [ApiLogTag] alongside the lines the repositories write themselves.
 */
internal object ApiLogger : Logger {
    override fun log(message: String) {
        logDebug(ApiLogTag, message)
    }
}

/**
 * How much of each exchange is written down.
 *
 * [LogLevel.BODY] on a debug build — the reference app logs at this level unconditionally, and it
 * is the level that actually answers "why did the search fail", because a 403's body is the
 * difference between `Forbidden` and `Provided API key is disabled`.
 *
 * [LogLevel.HEADERS] on a release build. The method, the URL and the status still get written, so
 * a crash report still shows which call failed and how — but the bodies, which carry the install's
 * `user_id` and the affiliate markers inside every booking deeplink, do not. See [isDebugBuild].
 */
internal val apiLogLevel: LogLevel
    get() = if (isDebugBuild) LogLevel.BODY else LogLevel.HEADERS
