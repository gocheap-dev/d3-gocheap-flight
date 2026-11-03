package com.gocheap.flights.data.user

/**
 * On, as it is on Android.
 *
 * It was held off until `users/session/` was confirmed for the iOS app code, which it now has
 * been against the staging backend: `users/add` accepts this app's iOS label (`d3_ios_tflight`)
 * and answers with a user id, and `users/session/` accepts a row keyed on that id for all three
 * verticals. The flight host recognises the iOS app code (`D3IT`) on both `controller/` and
 * autocomplete, same as the Android one.
 *
 * Nothing else needed writing: the feature is entirely common code and this line was the whole of
 * what made it Android-only.
 */
internal actual val sessionTrackingEnabled: Boolean = true
