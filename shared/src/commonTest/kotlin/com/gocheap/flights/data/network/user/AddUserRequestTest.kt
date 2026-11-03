package com.gocheap.flights.data.network.user

import com.gocheap.flights.data.model.user.AddUserRequest
import com.gocheap.flights.data.model.user.AddUserResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire format of `users/add/`, pinned against the backend team's own example request.
 *
 * Worth its own test because nothing else would catch a change here: the body is built in one
 * place, sent once per install, and a renamed key or a dropped null fails at the backend rather
 * than at compile time — on a call that deliberately swallows its own failures.
 */
class AddUserRequestTest {

    private val json = UserHttpClient.UserJson

    @Test
    fun the_body_carries_the_backends_own_key_names() {
        val encoded = json.encodeToString(
            AddUserRequest.serializer(),
            AddUserRequest(
                deviceId = "A1B2C3D4-E5F6-4789-A0B1-C2D3E4F5A6B7",
                deviceIdType = "uuid",
                app = "d3_ios_tflight",
                vendorId = "A1B2C3D4-E5F6-4789-A0B1-C2D3E4F5A6B7",
                pseudoId = "kR7mQp2XbZ9aLw4T",
                acquiredRoute = "unknown",
                referrerUrl = null,
                email = null,
            ),
        ).let(Json::parseToJsonElement).jsonObject

        // snake_case on the wire whatever the Kotlin properties are called.
        assertEquals(
            setOf(
                "device_id", "device_id_type", "app", "vendor_id", "pseudo_id",
                "acquired_route", "referrer_url", "email", "name", "email_consent",
            ),
            encoded.keys,
        )
    }

    @Test
    fun a_field_this_install_cannot_fill_is_sent_as_null_rather_than_dropped() {
        val encoded = json.encodeToString(
            AddUserRequest.serializer(),
            AddUserRequest(deviceId = "abc", deviceIdType = "uuid", acquiredRoute = "unknown"),
        ).let(Json::parseToJsonElement).jsonObject

        // The example request sends "referrer_url": null explicitly, and both reference apps
        // serialise nulls. Flipping explicitNulls off would silently drop these.
        assertEquals(JsonNull, encoded["referrer_url"])
        assertEquals(JsonNull, encoded["email"])
        assertTrue("name" in encoded)
    }

    @Test
    fun the_response_is_read_for_the_user_id_and_the_campaign() {
        val decoded = json.decodeFromString(
            AddUserResponse.serializer(),
            """
            {
              "msg": "User created",
              "user_id": 123456,
              "attribution": {
                "campaign_id": "cmp-1",
                "adgroup_id": "adg-2",
                "ad_objective_name": "installs"
              }
            }
            """.trimIndent(),
        )

        assertEquals(123456, decoded.userId)
        assertEquals("cmp-1", decoded.attribution?.campaignId)
        assertEquals("adg-2", decoded.attribution?.adGroupId)
        assertEquals("installs", decoded.attribution?.adObjectiveName)
        // Absent keys are null rather than a parse failure; the object's shape depends on the ad
        // network that produced the install.
        assertNull(decoded.attribution?.adId)
    }

    @Test
    fun an_attribution_key_this_build_does_not_know_does_not_fail_the_response() {
        val decoded = json.decodeFromString(
            AddUserResponse.serializer(),
            """{"user_id": 7, "attribution": {"campaign_id": "c", "some_new_network_id": "x"}}""",
        )

        assertEquals(7, decoded.userId)
        assertEquals("c", decoded.attribution?.campaignId)
    }
}
