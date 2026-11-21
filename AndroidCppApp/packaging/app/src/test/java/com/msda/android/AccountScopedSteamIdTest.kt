package com.msda.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountScopedSteamIdTest {
    private val accounts = """
        0|alpha|76561198000000001
        1|beta|76561198000000002
        2|gamma|76561198000000003
    """.trimIndent()

    @Test
    fun fromAccountsCsv_resolvesIndexWithoutMutatingActiveAccount() {
        assertEquals("76561198000000001", AccountScopedSteamId.fromAccountsCsv(accounts, 0))
        assertEquals("76561198000000002", AccountScopedSteamId.fromAccountsCsv(accounts, 1))
        assertEquals("76561198000000003", AccountScopedSteamId.fromAccountsCsv(accounts, 2))
    }

    @Test
    fun fromAccountsCsv_returnsBlankForInvalidIndexOrRow() {
        assertEquals("", AccountScopedSteamId.fromAccountsCsv(accounts, -1))
        assertEquals("", AccountScopedSteamId.fromAccountsCsv(accounts, 3))
        assertEquals("", AccountScopedSteamId.fromAccountsCsv("0|alpha|", 0))
        assertEquals("", AccountScopedSteamId.fromAccountsCsv("", 0))
    }

    @Test
    fun fromActiveAccountJson_readsSteamIdAndToleratesBadJson() {
        assertEquals(
            "76561198000000009",
            AccountScopedSteamId.fromActiveAccountJson("""{"steamId":"76561198000000009"}""")
        )
        assertEquals("", AccountScopedSteamId.fromActiveAccountJson("not json"))
        assertEquals("", AccountScopedSteamId.fromActiveAccountJson("{}"))
    }

    @Test
    fun isKnownSteamId_matchesExactSteamIdOnly() {
        assertTrue(AccountScopedSteamId.isKnownSteamId(accounts, "76561198000000002"))
        assertFalse(AccountScopedSteamId.isKnownSteamId(accounts, "76561198000000099"))
        assertFalse(AccountScopedSteamId.isKnownSteamId(accounts, ""))
    }

    @Test
    fun secondsRemaining_isCodeWindowBoundedAndMatchesWallClock() {
        assertEquals(30, AccountScopedSteamId.secondsRemaining(1_700_000_010))
        assertEquals(1, AccountScopedSteamId.secondsRemaining(1_700_000_029))
        assertEquals(30, AccountScopedSteamId.secondsRemaining(1_700_000_030))

        for (second in 0L until 60L) {
            val value = AccountScopedSteamId.secondsRemaining(1_700_000_000 + second)
            assertTrue("seconds out of range: $value", value in 1..30)
        }
    }
}
