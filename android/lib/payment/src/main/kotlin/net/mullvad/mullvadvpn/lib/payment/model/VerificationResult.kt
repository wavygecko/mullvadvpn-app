package net.mullvad.mullvadvpn.lib.payment.model

sealed interface VerificationResult {

    // No verification was needed as there is no purchases to verify
    data object NoVerification : VerificationResult

    data object Success : VerificationResult

    // Generic error, add more cases as needed
    sealed interface Error : VerificationResult {
        data object Other : Error

        data class OtherWithException(val exception: Throwable) : Error
    }
}
