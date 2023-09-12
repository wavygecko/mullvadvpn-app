package net.mullvad.mullvadvpn.repository.payment

sealed interface PurchaseResult {
    data object PurchaseCompleted: PurchaseResult

    data object VerificationError: PurchaseResult

    data object PurchaseCancelled: PurchaseResult

    data object PurchaseError: PurchaseResult
}
