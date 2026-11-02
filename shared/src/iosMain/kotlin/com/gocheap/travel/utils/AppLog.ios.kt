package com.gocheap.travel.utils

import platform.Foundation.NSLog

/**
 * `NSLog` rather than `println`: a `println` from Kotlin/Native only reaches stdout, which means the
 * Xcode console of a debug run and nowhere else. `NSLog` goes to the unified log as well, so the
 * same lines are readable in Console.app against a device that was never attached to a debugger —
 * which is where a failed sign-up on someone else's phone actually gets diagnosed.
 *
 * **Nothing is ever passed as a format argument.** `NSLog` is variadic, and Kotlin/Native does not
 * bridge a Kotlin `String` into a C vararg: `NSLog("%@", text)` hands it the characters where it
 * expects an object pointer, and it crashes with an `EXC_BAD_ACCESS` whose fault address spells out
 * the string. So the whole line goes in as the format itself and the vararg list stays empty.
 *
 * Which makes the escaping below load-bearing rather than defensive. These lines carry referrer
 * URLs and server error bodies, so a `%` in one is a real possibility, and an unescaped one would
 * be read as a specifier — sending `NSLog` after an argument that was never passed, which is the
 * same crash by a different route. Doubling it is how a literal `%` is written in a format string.
 */
private fun log(tag: String, message: String) {
    NSLog("$tag: ${message.replace("%", "%%")}")
}

internal actual fun logDebug(tag: String, message: String) {
    log(tag, message)
}

internal actual fun logWarning(tag: String, message: String, error: Throwable?) {
    log(tag, message)
    error?.let { log(tag, it.stackTraceToString()) }
}
