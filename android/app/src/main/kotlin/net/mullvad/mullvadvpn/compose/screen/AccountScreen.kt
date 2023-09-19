package net.mullvad.mullvadvpn.compose.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.onebone.toolbar.ScrollStrategy
import me.onebone.toolbar.rememberCollapsingToolbarScaffoldState
import net.mullvad.mullvadvpn.R
import net.mullvad.mullvadvpn.compose.button.ActionButton
import net.mullvad.mullvadvpn.compose.component.CollapsingToolbarScaffold
import net.mullvad.mullvadvpn.compose.component.CollapsingTopBar
import net.mullvad.mullvadvpn.compose.component.CopyableObfuscationView
import net.mullvad.mullvadvpn.compose.component.InformationView
import net.mullvad.mullvadvpn.compose.component.MissingPolicy
import net.mullvad.mullvadvpn.compose.component.drawVerticalScrollbar
import net.mullvad.mullvadvpn.compose.dialog.DeviceNameInfoDialog
import net.mullvad.mullvadvpn.compose.dialog.PaymentAvailabilityDialog
import net.mullvad.mullvadvpn.compose.dialog.PurchaseResultDialog
import net.mullvad.mullvadvpn.compose.extensions.createOpenAccountPageHook
import net.mullvad.mullvadvpn.compose.state.PaymentState
import net.mullvad.mullvadvpn.lib.common.util.capitalizeFirstCharOfEachWord
import net.mullvad.mullvadvpn.lib.payment.model.PaymentProduct
import net.mullvad.mullvadvpn.lib.theme.Dimens
import net.mullvad.mullvadvpn.util.toExpiryDateString
import net.mullvad.mullvadvpn.viewmodel.AccountUiState
import net.mullvad.mullvadvpn.viewmodel.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun PreviewAccountScreen() {
    AccountScreen(
        showSitePayment = true,
        uiState =
            AccountUiState(
                deviceName = "Test Name",
                accountNumber = "1234123412341234",
                accountExpiry = null,
                billingPaymentState =
                    PaymentState.PaymentAvailable(
                        listOf(PaymentProduct("productId", price = "34 SEK"))
                    )
            ),
        uiSideEffect = MutableSharedFlow<AccountViewModel.UiSideEffect>().asSharedFlow(),
        enterTransitionEndAction = MutableSharedFlow()
    )
}

@ExperimentalMaterial3Api
@Composable
fun AccountScreen(
    showSitePayment: Boolean,
    uiState: AccountUiState,
    uiSideEffect: SharedFlow<AccountViewModel.UiSideEffect>,
    enterTransitionEndAction: SharedFlow<Unit>,
    onRedeemVoucherClick: () -> Unit = {},
    onManageAccountClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onPurchaseBillingProductClick: (productId: String) -> Unit = {},
    onTryVerificationAgain: () -> Unit = {},
    onTryFetchProductsAgain: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val state = rememberCollapsingToolbarScaffoldState()
    val progress = state.toolbarState.progress
    val backgroundColor = MaterialTheme.colorScheme.background
    val systemUiController = rememberSystemUiController()
    var showDeviceNameInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enterTransitionEndAction.collect { systemUiController.setStatusBarColor(backgroundColor) }
    }
    val openAccountPage = LocalUriHandler.current.createOpenAccountPageHook()
    LaunchedEffect(Unit) {
        uiSideEffect.collect { viewAction ->
            if (viewAction is AccountViewModel.UiSideEffect.OpenAccountManagementPageInBrowser) {
                openAccountPage(viewAction.token)
            }
        }
    }

    if (showDeviceNameInfoDialog) {
        DeviceNameInfoDialog { showDeviceNameInfoDialog = false }
    }

    uiState.purchaseResult?.let {
        PurchaseResultDialog(
            purchaseResult = uiState.purchaseResult,
            onClose = {},
            onTryAgain = onTryVerificationAgain
        )
    }

    PaymentAvailabilityDialog(
        paymentAvailability = uiState.billingPaymentState,
        onClose = {},
        onTryAgain = onTryFetchProductsAgain
    )

    CollapsingToolbarScaffold(
        backgroundColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
        state = state,
        scrollStrategy = ScrollStrategy.ExitUntilCollapsed,
        isEnabledWhenCollapsable = false,
        toolbar = {
            val scaffoldModifier =
                Modifier.road(
                    whenCollapsed = Alignment.TopCenter,
                    whenExpanded = Alignment.BottomStart
                )
            CollapsingTopBar(
                backgroundColor = MaterialTheme.colorScheme.secondary,
                onBackClicked = onBackClick,
                title = stringResource(id = R.string.settings_account),
                progress = progress,
                modifier = scaffoldModifier,
                shouldRotateBackButtonDown = true
            )
        },
    ) {
        val scrollState = rememberScrollState()

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start,
                modifier =
                    Modifier.fillMaxSize()
                        .drawVerticalScrollbar(scrollState)
                        .verticalScroll(scrollState)
                        .animateContentSize()
            ) {
                Text(
                    style = MaterialTheme.typography.labelMedium,
                    text = stringResource(id = R.string.device_name),
                    modifier = Modifier.padding(start = Dimens.sideMargin, end = Dimens.sideMargin)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    InformationView(
                        content = uiState.deviceName?.capitalizeFirstCharOfEachWord() ?: "",
                        whenMissing = MissingPolicy.SHOW_SPINNER
                    )
                    IconButton(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        onClick = { showDeviceNameInfoDialog = true }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.icon_info),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inverseSurface
                        )
                    }
                }

                Text(
                    style = MaterialTheme.typography.labelMedium,
                    text = stringResource(id = R.string.account_number),
                    modifier =
                        Modifier.padding(
                            start = Dimens.sideMargin,
                            end = Dimens.sideMargin,
                            top = Dimens.smallPadding
                        )
                )
                CopyableObfuscationView(content = uiState.accountNumber ?: "")
                Text(
                    style = MaterialTheme.typography.labelMedium,
                    text = stringResource(id = R.string.paid_until),
                    modifier = Modifier.padding(start = Dimens.sideMargin, end = Dimens.sideMargin)
                )

                InformationView(
                    content = uiState.accountExpiry?.toExpiryDateString() ?: "",
                    whenMissing = MissingPolicy.SHOW_SPINNER
                )

                Spacer(modifier = Modifier.weight(1f))

                when (uiState.billingPaymentState) {
                    PaymentState.Error.BillingError,
                    PaymentState.Error.GenericError -> {
                        // We show some kind of dialog error at the top
                    }
                    PaymentState.Loading -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier =
                                Modifier.padding(
                                        start = Dimens.sideMargin,
                                        end = Dimens.sideMargin,
                                        bottom = Dimens.screenVerticalMargin
                                    )
                                    .size(
                                        width = Dimens.progressIndicatorSize,
                                        height = Dimens.progressIndicatorSize
                                    )
                                    .align(Alignment.CenterHorizontally)
                        )
                    }
                    PaymentState.NoPayment -> {
                        // Show nothing
                    }
                    is PaymentState.PaymentAvailable -> {
                        uiState.billingPaymentState.products.forEach { product ->
                            ActionButton(
                                text =
                                    stringResource(id = R.string.add_30_days_time_x, product.price),
                                onClick = { onPurchaseBillingProductClick(product.productId) },
                                modifier =
                                    Modifier.padding(
                                        start = Dimens.sideMargin,
                                        end = Dimens.sideMargin,
                                        bottom = Dimens.screenVerticalMargin
                                    ),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                            )
                        }
                    }
                }

                if (showSitePayment) {
                    ActionButton(
                        text = stringResource(id = R.string.manage_account),
                        onClick = onManageAccountClick,
                        modifier =
                            Modifier.padding(
                                start = Dimens.sideMargin,
                                end = Dimens.sideMargin,
                                bottom = Dimens.screenVerticalMargin
                            ),
                        colors =
                            ButtonDefaults.buttonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                    )
                }

                ActionButton(
                    text = stringResource(id = R.string.redeem_voucher),
                    onClick = onRedeemVoucherClick,
                    modifier =
                        Modifier.padding(
                            start = Dimens.sideMargin,
                            end = Dimens.sideMargin,
                            bottom = Dimens.screenVerticalMargin
                        ),
                    colors =
                        ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                )

                ActionButton(
                    text = stringResource(id = R.string.log_out),
                    onClick = onLogoutClick,
                    modifier =
                        Modifier.padding(
                            start = Dimens.sideMargin,
                            end = Dimens.sideMargin,
                            bottom = Dimens.screenVerticalMargin
                        ),
                    colors =
                        ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.error
                        )
                )
            }
        }
    }
}
