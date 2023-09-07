package net.mullvad.mullvadvpn.lib.payment.model

sealed interface VerificationResult {

    //No verification was needed as there is no purchases to verify
    data object NoVerification: VerificationResult

    data object Success : VerificationResult

    //Generic error, add more cases as needed
    data class Error(val exception: Throwable) : VerificationResult
}
