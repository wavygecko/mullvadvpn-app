package net.mullvad.mullvadvpn.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.mullvad.mullvadvpn.R
import net.mullvad.mullvadvpn.lib.payment.model.PurchaseResult
import net.mullvad.mullvadvpn.lib.theme.AppTheme

@Composable
@Preview
private fun PreviewPurchaseResultDialogPurchaseStarted() {
    AppTheme {
        PurchaseResultDialog(
            purchaseResult = PurchaseResult.PurchaseStarted,
            onClose = {},
            onTryAgain = {}
        )
    }
}

@Composable
@Preview
private fun PreviewPurchaseResultDialogVerificationStarted() {
    AppTheme {
        PurchaseResultDialog(
            purchaseResult = PurchaseResult.VerificationStarted,
            onClose = {},
            onTryAgain = {}
        )
    }
}

@Composable
@Preview
private fun PreviewPurchaseResultDialogPurchaseCompleted() {
    AppTheme {
        PurchaseResultDialog(
            purchaseResult = PurchaseResult.PurchaseCompleted,
            onClose = {},
            onTryAgain = {}
        )
    }
}

@Composable
@Preview
private fun PreviewPurchaseResultTransactionIdError() {
    AppTheme {
        PurchaseResultDialog(
            purchaseResult = PurchaseResult.Error.TransactionIdError(null),
            onClose = {},
            onTryAgain = {}
        )
    }
}

@Composable
@Preview
private fun PreviewPurchaseResultVerificationError() {
    AppTheme {
        PurchaseResultDialog(
            purchaseResult = PurchaseResult.Error.VerificationError(null),
            onClose = {},
            onTryAgain = {}
        )
    }
}

@Composable
fun PurchaseResultDialog(
    purchaseResult: PurchaseResult,
    onClose: () -> Unit,
    onTryAgain: () -> Unit
) {
    when (purchaseResult) {
        // Idle states
        PurchaseResult.PurchaseCancelled,
        PurchaseResult.BillingFlowStarted,
        is PurchaseResult.Error.BillingError -> {
            // Show nothing
        }
        // Loading states
        PurchaseResult.PurchaseStarted,
        PurchaseResult.VerificationStarted ->
            LoadingDialog(text = stringResource(id = R.string.connecting))
        // Success state
        PurchaseResult.PurchaseCompleted -> PurchaseCompletedDialog(onClose = onClose)
        // Error states
        is PurchaseResult.Error.TransactionIdError ->
            PurchaseErrorDialog(
                title = stringResource(id = R.string.error_occurred),
                message = stringResource(id = R.string.try_again),
                onClose = onClose
            )
        is PurchaseResult.Error.VerificationError ->
            PurchaseErrorDialog(
                title = stringResource(id = R.string.payment_verification_error_dialog_title),
                message = stringResource(id = R.string.payment_verification_error_dialog_message),
                onTryAgain = onTryAgain,
                onClose = onClose
            )
    }
}

@Composable
private fun PurchaseCompletedDialog(onClose: () -> Unit) {
    BasePaymentDialog(
        title = stringResource(id = R.string.payment_completed_dialog_title),
        message = stringResource(id = R.string.payment_completed_dialog_message),
        icon = R.drawable.icon_success,
        onConfirmClick = onClose,
        confirmText = stringResource(id = R.string.got_it),
        onDismissRequest = onClose
    )
}

@Composable
fun PurchaseErrorDialog(
    title: String,
    message: String,
    onTryAgain: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    BasePaymentDialog(
        title = title,
        message = message,
        icon = R.drawable.icon_fail,
        onConfirmClick = onClose,
        confirmText = stringResource(id = R.string.cancel),
        onDismissRequest = onClose,
        dismissText = onTryAgain?.let { stringResource(id = R.string.try_again) },
        onDismissClick = onTryAgain
    )
}
