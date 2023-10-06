package net.mullvad.mullvadvpn.lib.billing.extension

import net.mullvad.mullvadvpn.lib.payment.model.VerificationResult
import net.mullvad.mullvadvpn.model.PlayPurchaseVerifyError
import net.mullvad.mullvadvpn.model.PlayPurchaseVerifyResult

fun PlayPurchaseVerifyResult.toVerificationResult(): VerificationResult =
    when (this) {
        is PlayPurchaseVerifyResult.Error -> {
            when (error) {
                PlayPurchaseVerifyError.OtherError -> VerificationResult.Error.Other
            }
        }
        PlayPurchaseVerifyResult.Ok -> VerificationResult.Success
    }
