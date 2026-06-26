package com.msda.android

import android.content.Context

/** Shared auto-confirm filters used by the foreground UI and background worker. */
object ConfirmationAutoAccept {
    fun filterItems(
        bundles: List<ConfirmationBundle>,
        marketEnabled: Boolean,
        tradeEnabled: Boolean,
        giftTradeEnabled: Boolean
    ): List<ConfirmationItem> {
        if (!marketEnabled && !tradeEnabled && !giftTradeEnabled) return emptyList()
        return bundles
            .flatMap { it.items }
            .filter { item ->
                (marketEnabled && isStrictMarketConfirmation(item)) ||
                    (tradeEnabled && isStrictTradeConfirmation(item)) ||
                    (giftTradeEnabled && isGiftTradeConfirmation(item))
            }
            .distinctBy { it.id }
    }

    suspend fun acceptMatching(
        context: Context,
        auth: ConfirmationAuthContext,
        bundles: List<ConfirmationBundle>,
        marketEnabled: Boolean,
        tradeEnabled: Boolean,
        giftTradeEnabled: Boolean,
        onSessionRenewed: ((ConfirmationAuthContext) -> Unit)? = null
    ): Int {
        val items = filterItems(bundles, marketEnabled, tradeEnabled, giftTradeEnabled)
        if (items.isEmpty()) return 0

        var active = auth
        val renew: (ConfirmationAuthContext) -> Unit = { updated ->
            active = updated
            onSessionRenewed?.invoke(updated)
        }

        var accepted = 0
        for (item in items) {
            try {
                if (ConfirmationService.respondItemWithRenew(context, active, item, true, renew)) {
                    accepted++
                }
            } catch (_: Throwable) {
            }
        }
        return accepted
    }

    fun isStrictMarketConfirmation(item: ConfirmationItem): Boolean {
        return item.type != 2 && item.typeName.contains("market", ignoreCase = true)
    }

    fun isStrictTradeConfirmation(item: ConfirmationItem): Boolean {
        return item.type == 2 || item.typeName.contains("trade", ignoreCase = true)
    }

    fun isGiftTradeConfirmation(item: ConfirmationItem): Boolean {
        if (!isStrictTradeConfirmation(item)) return false

        val text = (listOf(item.headline) + item.summary)
            .joinToString(" ")
            .lowercase()

        val giftSignals = listOf(
            "gift",
            "you will receive",
            "you'll receive",
            "for free",
            "without exchange",
            "no items from your inventory",
            "no items from you",
            "0 items from you",
            "nothing to give",
            "sent you a gift"
        )
        val lossSignals = listOf(
            "you will give",
            "you'll give",
            "you are giving",
            "from your inventory",
            "in exchange",
            "for your"
        )

        return giftSignals.any { text.contains(it) } && lossSignals.none { text.contains(it) }
    }
}
