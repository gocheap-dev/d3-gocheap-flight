package com.gocheap.travel.data.network.user

import com.gocheap.travel.data.model.user.UpdatePushTokenRequest
import com.gocheap.travel.data.model.user.UpdatePushTokenResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The wire format of `users/update-push-token/`, pinned against what the endpoint itself asks for.
 *
 * Every assertion here was read off a 400 from the staging backend: posting a body without a field
 * answers `{"msg":"<field> is required"}`, one field at a time, and the six below are what it
 * stops asking for. Worth pinning because a renamed key fails at the backend rather than at
 * compile time, on a call that deliberately swallows its own failures — exactly the reasoning
 * behind [AddUserRequestTest].
 */
class UpdatePushTokenRequestTest {

    private val json = UserHttpClient.UserJson

    private val request = UpdatePushTokenRequest(
        userId = 123456,
        deviceId = "A1B2C3D4-E5F6-4789-A0B1-C2D3E4F5A6B7",
        token = "fZ7mQp2XbZ9aLw4T:APA91bExampleRegistrationToken",
        countryCode = "IN",
        timezone = "Asia/Kolkata",
        language = "en",
    )

    @Test
    fun the_body_carries_exactly_the_six_fields_the_endpoint_requires() {
        val encoded = json.encodeToString(UpdatePushTokenRequest.serializer(), request)
            .let(Json::parseToJsonElement).jsonObject

        assertEquals(
            setOf("user_id", "device_id", "token", "country_code", "timezone", "language"),
            encoded.keys,
        )
    }

    @Test
    fun the_registration_token_travels_as_token_not_push_token() {
        val encoded = json.encodeToString(UpdatePushTokenRequest.serializer(), request)
            .let(Json::parseToJsonElement).jsonObject

        // The endpoint ignores `push_token` and then rejects the body for a missing `token`, which
        // is a slow way to discover a one-word mistake.
        assertNull(encoded["push_token"])
        assertEquals(
            "fZ7mQp2XbZ9aLw4T:APA91bExampleRegistrationToken",
            encoded["token"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun the_user_id_is_a_number_rather_than_its_text() {
        val encoded = json.encodeToString(UpdatePushTokenRequest.serializer(), request)

        // `"user_id": 123456`, not `"123456"` — which is the form the endpoint accepted when the
        // contract was read off it, and which `UserSessionState.userId` is already an Int for.
        assertEquals(true, encoded.contains("\"user_id\":123456"))
    }

    @Test
    fun a_response_key_this_build_does_not_know_does_not_fail_the_call() {
        val decoded = json.decodeFromString(
            UpdatePushTokenResponse.serializer(),
            """{"msg": "Push token updated", "updated_at": "2026-10-30T08:51:22Z"}""",
        )

        assertEquals("Push token updated", decoded.msg)
    }
}
