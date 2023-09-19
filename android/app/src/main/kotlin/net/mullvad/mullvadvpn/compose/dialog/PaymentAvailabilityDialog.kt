package net.mullvad.mullvadvpn.compose.dialog

import androidx.compose.runtime.Composable
import net.mullvad.mullvadvpn.compose.state.PaymentState

@Composable
fun PaymentAvailabilityDialog(
    paymentAvailability: PaymentState,
    onClose: () -> Unit,
    onTryAgain: () -> Unit
) {
    when (paymentAvailability) {
        is PaymentState.Error ->
            PaymentBillingErrorDialog(onTryAgain = onTryAgain, onClose = onClose)
        else -> {
            // Show nothing
        }
    }
}
