package com.gocheap.flights.utils

/**
 * The device's time zone, as an IANA identifier — `Asia/Kolkata`, `America/New_York`.
 *
 * An expect rather than something derived in common code because neither platform's zone database
 * is reachable from it, and because an offset is not a substitute: `users/update-push-token/` wants
 * a zone so that a campaign can be sent at a sensible local hour, and an offset read in June is the
 * wrong answer for the same device in December.
 *
 * Returns `UTC` rather than throwing or returning null where the platform cannot answer. A push
 * delivered at the wrong local hour is a worse outcome than one not sent at all only if the server
 * is allowed to assume the zone is right — and it is one field of six, none of which are worth
 * failing the whole call over.
 */
internal expect fun currentTimeZoneId(): String
