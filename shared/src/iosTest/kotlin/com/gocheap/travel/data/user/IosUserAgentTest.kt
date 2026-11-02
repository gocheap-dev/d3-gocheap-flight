package com.gocheap.travel.data.user

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shape of the User-Agent iOS sends.
 *
 * Pinned because the backend validates this header and answers a product token on its own with
 * `400 {"error":"Invalid 'User-Agent' header"}` — before it reads the request. Every iOS flight
 * search failed that way while this returned `FlightTp-iOS/1.0`, and nothing in the app surfaced
 * it: the search simply never produced results.
 *
 * Established against the backend one token at a time: `FlightTp/1.0`, `FlightTp-iOS/1.0`,
 * `FlightTp/1.0 Darwin/23.6.0`, `FlightTp/1.0 iOS/17.5` and `FlightTp/1.0 (iPhone; iOS 17.5)` are
 * all refused; anything carrying `CFNetwork/<version>` is accepted.
 */
class IosUserAgentTest {

    @Test
    fun the_user_agent_carries_the_http_stack_token_the_backend_requires() {
        val agent = platformUserAgent
        // Blank is the documented escape hatch: the clients then leave the header unset and
        // NSURLSession supplies its own, which carries these tokens natively.
        if (agent.isBlank()) return

        assertTrue(
            agent.contains("CFNetwork/"),
            "User-Agent \"$agent\" has no CFNetwork token; the flight host answers 400 for that",
        )
    }

    @Test
    fun the_user_agent_names_the_app_before_the_system_tokens() {
        val agent = platformUserAgent
        if (agent.isBlank()) return

        assertTrue(agent.startsWith("FlightTp/"), "User-Agent \"$agent\" does not lead with the app")
        // The old value. It named the platform in the product token and carried no system token,
        // which is exactly the shape the backend rejects.
        assertFalse(
            agent.startsWith("FlightTp-iOS/"),
            "User-Agent \"$agent\" is the old product-only form the backend rejects",
        )
    }

    @Test
    fun the_version_is_read_rather_than_left_as_a_placeholder_token() {
        val agent = platformUserAgent
        if (agent.isBlank()) return

        // Each token is `name/version`; a bare name would mean a lookup silently returned nothing.
        agent.split(" ").filter(String::isNotBlank).forEach { token ->
            assertTrue(token.contains("/"), "Token \"$token\" in \"$agent\" carries no version")
        }
    }
}
