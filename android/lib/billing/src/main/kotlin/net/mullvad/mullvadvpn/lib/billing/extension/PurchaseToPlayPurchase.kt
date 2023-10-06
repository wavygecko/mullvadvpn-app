package net.mullvad.mullvadvpn.lib.billing.extension

import com.android.billingclient.api.Purchase
import net.mullvad.mullvadvpn.model.PlayPurchase

fun Purchase.toPlayPurchase() = PlayPurchase(
    productId = products.firstOrNull() ?: "",
    token = purchaseToken
)
