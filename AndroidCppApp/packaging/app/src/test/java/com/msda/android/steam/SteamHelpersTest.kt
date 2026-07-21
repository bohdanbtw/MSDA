package com.msda.android.steam

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SteamHelpersTest {
    @Test
    fun steamGuardCode_isDeterministicForKnownVector() {
        val secret = "AQQBAgMEBQYHCAkKCwwNDg8QERITFBUW"
        assertEquals("35J23", SteamGuardCodeGenerator.generateFromSharedSecret(secret, 1_700_000_000))
        assertEquals("QN6GR", SteamGuardCodeGenerator.generateFromSharedSecret(secret, 1_700_000_030))
    }

    @Test
    fun confirmationHash_matchesKnownVector() {
        val secret = "AQQBAgMEBQYHCAkKCwwNDg8QERITFBUW"
        assertEquals(
            "U/iOJzojyVwBgJOtWFjZOUN3RyM=",
            EncryptionHelper.generateConfirmationHash(1_700_000_000, secret, "conf")
        )
        assertEquals(
            "kB8Z2QWb8aBC1MIruZ0hgRlQYgo=",
            EncryptionHelper.generateConfirmationHash(1_700_000_000, secret, "allow")
        )
    }

    @Test
    fun steamTokenParser_readsMobileRefreshJwt() {
        val token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiI3NjU2MTE5ODAwMDAwMDAwMCIsImV4cCI6MjAwMDAwMDAwMCwiYXVkIjpbIndlYiIsIm1vYmlsZSIsInJlbmV3Il19.sig"
        val parsed = SteamTokenHelper.parse(token)
        assertEquals("76561198000000000", parsed.steamId)
        assertTrue(parsed.expiresAtMs > 0L)
        assertEquals(SteamTokenType.MobileRefresh, parsed.tokenType)
    }

    @Test
    fun mafileSessionReader_readsNebulaSessionDataBlock() {
        val steamId = "76561198000000000"
        val jwt = "76561198000000000%7C%7CeyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3NjU2MTE5ODAwMDAwMDAwMCIsImV4cCI6MjAwMDAwMDAwMCwiYXVkIjpbIm1vYmlsZSJdfQ.sig"
        val json = JSONObject(
            """
            {
              "account_name": "nebula_user",
              "SessionData": {
                "SessionID": "abc123session",
                "steamLoginSecure": "$jwt",
                "RefreshToken": "refresh_jwt_token",
                "AccessToken": "access_jwt_token",
                "SteamID": "$steamId"
              }
            }
            """.trimIndent()
        )
        val session = MafileSessionReader.parse(steamId, json)
        assertNotNull(session)
        assertEquals("nebula_user", session!!.accountName)
        assertEquals("abc123session", session.sessionId)
        assertEquals(jwt, session.steamLoginSecure)
        assertEquals("refresh_jwt_token", session.refreshToken)
        assertEquals("access_jwt_token", session.accessToken)
    }

    @Test
    fun mafileSessionReader_readsSdaLegacyTopLevelFields() {
        val steamId = "76561198123456789"
        val jwt = "76561198123456789%7C%7CeyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3NjU2MTE5ODEyMzQ1Njc4OSIsImV4cCI6MjAwMDAwMDAwMCwiYXVkIjpbIm1vYmlsZSJdfQ.sig"
        val json = JSONObject(
            """
            {
              "account_name": "sda_user",
              "sessionid": "legacy_session_id",
              "steamLoginSecure": "$jwt",
              "OAuthToken": "legacy_refresh",
              "access_token": "legacy_access"
            }
            """.trimIndent()
        )
        val session = MafileSessionReader.parse(steamId, json)
        assertNotNull(session)
        assertEquals("sda_user", session!!.accountName)
        assertEquals("legacy_session_id", session.sessionId)
        assertEquals("legacy_refresh", session.refreshToken)
        assertEquals("legacy_access", session.accessToken)
    }

    @Test
    fun mafileSessionReader_prefersSessionDataOverLegacy() {
        val steamId = "76561198000000001"
        val json = JSONObject(
            """
            {
              "sessionid": "legacy_id",
              "SessionData": { "SessionID": "nebula_id", "steamLoginSecure": "76561198000000001%7C%7Cjwt" }
            }
            """.trimIndent()
        )
        val session = MafileSessionReader.parse(steamId, json)
        assertNotNull(session)
        assertEquals("nebula_id", session!!.sessionId)
    }

    @Test
    fun steamTokenHelper_combineJwtWithSteamId_matchesNebulaFormat() {
        val steamId = "76561198000000000"
        val jwt = "eyJhbGciOiJIUzI1NiJ9.payload.sig"
        val combined = SteamTokenHelper.combineJwtWithSteamId(steamId, jwt)
        assertTrue(combined.startsWith("$steamId%7C%7C"))
        assertEquals(jwt, SteamTokenHelper.extractJwt(combined))
    }

    @Test
    fun extractProtobufBody_returnsRawBytesForProtobuf() {
        val raw = byteArrayOf(0x08, 0x01, 0x12, 0x03, 0x61, 0x62, 0x63)
        assertArrayEquals(raw, extractProtobufBody(raw))
    }

    @Test
    fun extractProtobufBody_decodesJsonBase64Wrapper() {
        val inner = byteArrayOf(0x08, 0x01, 0x12, 0x03, 0x61, 0x62, 0x63)
        val b64 = Base64.getEncoder().encodeToString(inner)
        val json = """{"response":"$b64"}""".toByteArray(Charsets.UTF_8)
        assertArrayEquals(inner, extractProtobufBody(json))
    }

    @Test
    fun extractProtobufBody_decodesNestedJsonResponseObject() {
        val json = """
            {
              "response": {
                "publickey_mod": "abc123",
                "publickey_exp": "010001",
                "timestamp": "1700000000"
              }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val decoded = SteamProtoMessages.decodeGetPasswordRsaPublicKeyResponse(extractProtobufBody(json))
        assertEquals("abc123", decoded.publicKeyMod)
        assertEquals("010001", decoded.publicKeyExp)
        assertEquals(1_700_000_000L, decoded.timestamp)
    }

    @Test
    fun extractEResult_prefersHeaderAndDoesNotDefaultForBinaryBody() {
        val proto = byteArrayOf(0x08, 0x01)
        assertEquals(5, extractEResult("5", proto))
        assertNull(extractEResult(null, proto))
        assertEquals(63, extractEResult(null, """{"eresult":63}""".toByteArray()))
        assertEquals(2, extractEResult(null, """{"result":2}""".toByteArray()))
    }
}
