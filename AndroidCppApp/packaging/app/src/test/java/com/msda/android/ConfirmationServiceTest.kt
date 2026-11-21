package com.msda.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationServiceTest {
    private fun item(
        id: String,
        nonce: String,
        type: Int = 3,
        typeName: String = "Generic",
        headline: String = "Headline",
        summary: List<String> = emptyList(),
        icon: String? = null,
        creatorId: String = "",
        multi: Boolean = false
    ): JSONObject {
        val summaryArray = JSONArray()
        summary.forEach { summaryArray.put(it) }
        return JSONObject()
            .put("id", id)
            .put("nonce", nonce)
            .put("type", type)
            .put("type_name", typeName)
            .put("headline", headline)
            .put("summary", summaryArray)
            .put("icon", icon ?: JSONObject.NULL)
            .put("creator_id", creatorId)
            .put("multi", multi)
    }

    private fun auth() = ConfirmationAuthContext(
        steamId = "76561198000000000",
        identitySecret = "identity",
        deviceId = "device",
        sessionId = "session",
        steamLoginSecure = "login",
        accountName = "account"
    )

    @Test
    fun parseAuthPayload_rejectsBlankAndShortPayloads() {
        assertNull(ConfirmationService.parseAuthPayload(""))
        assertNull(ConfirmationService.parseAuthPayload("   "))
        assertNull(ConfirmationService.parseAuthPayload("a|b|c|d|e"))
    }

    @Test
    fun parseAuthPayload_readsAllFields() {
        val payload = "steamId|identity|device|session|login|account|shared|refresh|access"
        val parsed = ConfirmationService.parseAuthPayload(payload)

        assertNotNull(parsed)
        assertEquals("steamId", parsed!!.steamId)
        assertEquals("identity", parsed.identitySecret)
        assertEquals("device", parsed.deviceId)
        assertEquals("session", parsed.sessionId)
        assertEquals("login", parsed.steamLoginSecure)
        assertEquals("account", parsed.accountName)
        assertEquals("shared", parsed.sharedSecret)
        assertEquals("refresh", parsed.refreshToken)
        assertEquals("access", parsed.accessToken)
    }

    @Test
    fun parseAuthPayload_optionalFieldsDefaultToEmpty() {
        val parsed = ConfirmationService.parseAuthPayload("s|i|d|sess|login|account")!!

        assertEquals("", parsed.sharedSecret)
        assertEquals("", parsed.refreshToken)
        assertEquals("", parsed.accessToken)
    }

    @Test
    fun withSession_fillsOnlyBlankFields() {
        val auth = auth().copy(
            sessionId = "stale-session",
            steamLoginSecure = "stale-login",
            accountName = "",
            refreshToken = "kept-refresh",
            accessToken = ""
        )
        val session = StoredSteamSession(
            steamLoginSecure = "fresh-login",
            sessionId = "fresh-session",
            refreshToken = "fresh-refresh",
            accessToken = "fresh-access",
            accountName = "fresh-account"
        )

        val merged = auth.withSession(session)

        assertEquals("fresh-login", merged.steamLoginSecure)
        assertEquals("fresh-session", merged.sessionId)
        assertEquals("fresh-account", merged.accountName)
        assertEquals("kept-refresh", merged.refreshToken)
        assertEquals("fresh-access", merged.accessToken)
        assertEquals(auth.steamId, merged.steamId)
        assertEquals(auth.identitySecret, merged.identitySecret)
    }

    @Test(expected = IllegalStateException::class)
    fun parseBundlesOrThrow_rejectsFailureResponse() {
        ConfirmationService.parseBundlesOrThrow(JSONObject().put("success", false).put("message", "needauth"))
    }

    @Test
    fun parseBundlesOrThrow_emptyConfReturnsNoBundles() {
        assertTrue(ConfirmationService.parseBundlesOrThrow(JSONObject().put("success", true)).isEmpty())
        assertTrue(
            ConfirmationService.parseBundlesOrThrow(
                JSONObject().put("success", true).put("conf", JSONArray())
            ).isEmpty()
        )
    }

    @Test
    fun parseBundlesOrThrow_groupsTradesByHeadlineAndParsesFields() {
        val json = JSONObject().put("success", true).put(
            "conf",
            JSONArray()
                .put(
                    item(
                        id = "1", nonce = "n1", type = 2, typeName = "Trade offer",
                        headline = "Trader A", summary = listOf("You send: A", "They send: B"),
                        icon = "https://example.com/a.png", creatorId = "7651", multi = true
                    )
                )
                .put(
                    item(
                        id = "2", nonce = "n2", type = 2, typeName = "Trade offer",
                        headline = "Trader A", creatorId = "7652"
                    )
                )
                .put(
                    item(
                        id = "3", nonce = "n3", type = 1, typeName = "Market Listing",
                        headline = "Sell item", creatorId = "7653"
                    )
                )
                .put(
                    item(
                        id = "4", nonce = "n4", type = 3, typeName = "Account Recovery",
                        headline = "Recover", creatorId = "42"
                    )
                )
        )

        val bundles = ConfirmationService.parseBundlesOrThrow(json)

        assertEquals(3, bundles.size)

        val trade = bundles.first { it.key == "trade:Trader A" }
        assertEquals("Trade offer", trade.title)
        assertEquals(2, trade.items.size)
        assertEquals(listOf("1", "2"), trade.items.map { it.id })
        assertTrue(trade.items.first().multi)
        assertEquals(listOf("You send: A", "They send: B"), trade.items.first().summary)
        assertEquals("https://example.com/a.png", trade.items.first().iconUrl)
        assertEquals("n1", trade.items.first().nonce)
        assertEquals(2, trade.items.first().type)
        assertEquals("7651", trade.items.first().creatorId)
        assertNotNull(trade.partner)
        assertEquals("Trader A", trade.partner!!.nickname)
        assertEquals("https://example.com/a.png", trade.partner!!.avatarUrl)
        assertEquals("Steam Level: --", trade.partner!!.steamLevel)

        val market = bundles.first { it.key == "market:Market Listing" }
        assertEquals(1, market.items.size)
        assertNull(market.partner)
        assertNull(market.items.first().iconUrl)

        val other = bundles.first { it.key == "Account Recovery:42" }
        assertEquals("Account Recovery", other.title)
        assertFalse(other.items.first().multi)
    }

    @Test
    fun parseBundlesOrThrow_appliesItemDefaults() {
        val json = JSONObject().put("success", true).put(
            "conf",
            JSONArray().put(JSONObject().put("id", "1").put("nonce", "n"))
        )

        val item = ConfirmationService.parseBundlesOrThrow(json).single().items.single()

        assertEquals("1", item.id)
        assertEquals("n", item.nonce)
        assertEquals(0, item.type)
        assertEquals("Unknown", item.typeName)
        assertEquals("", item.headline)
        assertTrue(item.summary.isEmpty())
        assertNull(item.iconUrl)
        assertEquals("", item.creatorId)
        assertFalse(item.multi)
    }

    @Test
    fun respondWithoutContext_returnsFalseWithoutNetwork() {
        val bundle = ConfirmationBundle(
            key = "k", title = "t", typeName = "tn",
            items = listOf(ConfirmationItem("id", "nonce", 3, "tn", "h", emptyList(), null, "", false)),
            partner = null
        )

        assertFalse(ConfirmationService.respondBundle(auth(), bundle, true))
        assertFalse(ConfirmationService.respondBundle(auth(), bundle, false))
        assertFalse(ConfirmationService.respondItem(auth(), bundle.items.single(), true))
        assertFalse(ConfirmationService.respondItem(auth(), bundle.items.single(), false))
    }
}
