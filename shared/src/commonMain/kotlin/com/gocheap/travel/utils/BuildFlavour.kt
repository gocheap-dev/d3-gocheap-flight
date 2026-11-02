package com.gocheap.travel.utils

/**
 * Whether this build is a debuggable one.
 *
 * The only thing it gates is how loud the network logging is. Request and response bodies are
 * genuinely useful when a search misbehaves — they are what tells a client bug apart from a
 * backend one — but they also carry the install's `user_id` and the affiliate markers inside every
 * booking deeplink, and neither belongs in the log of a phone in someone's pocket.
 *
 * So the bodies are logged on a debug build and the headers alone on a release one. Read through
 * this rather than off a build config directly: common code has no access to either platform's.
 */
internal expect val isDebugBuild: Boolean
