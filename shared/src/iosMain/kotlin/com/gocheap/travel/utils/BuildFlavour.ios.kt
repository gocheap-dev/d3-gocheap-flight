package com.gocheap.travel.utils

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * Kotlin/Native's own view of how the framework was compiled, which is the closest iOS equivalent
 * of Android's `debuggable` flag — a release framework is what goes into an App Store build.
 */
@OptIn(ExperimentalNativeApi::class)
internal actual val isDebugBuild: Boolean
    get() = Platform.isDebugBinary
