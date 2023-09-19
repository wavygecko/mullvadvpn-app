package net.mullvad.mullvadvpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.PaymentProvider
import net.mullvad.mullvadvpn.compose.state.PaymentState
import net.mullvad.mullvadvpn.lib.payment.PaymentRepository
import net.mullvad.mullvadvpn.lib.payment.model.PaymentAvailability
import net.mullvad.mullvadvpn.lib.payment.model.PurchaseResult
import net.mullvad.mullvadvpn.model.AccountExpiry
import net.mullvad.mullvadvpn.model.DeviceState
import net.mullvad.mullvadvpn.repository.AccountRepository
import net.mullvad.mullvadvpn.repository.DeviceRepository
import net.mullvad.mullvadvpn.ui.serviceconnection.ServiceConnectionManager
import net.mullvad.mullvadvpn.ui.serviceconnection.authTokenCache
import org.joda.time.DateTime

class AccountViewModel(
    private var accountRepository: AccountRepository,
    private var serviceConnectionManager: ServiceConnectionManager,
    paymentProvider: PaymentProvider,
    deviceRepository: DeviceRepository
) : ViewModel() {

    private val paymentRepository: PaymentRepository? = paymentProvider.paymentRepository

    private val _uiSideEffect = MutableSharedFlow<UiSideEffect>(extraBufferCapacity = 1)
    private val _enterTransitionEndAction = MutableSharedFlow<Unit>()
    private val _paymentAvailability = MutableStateFlow<PaymentAvailability?>(null)
    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    val uiSideEffect = _uiSideEffect.asSharedFlow()

    val uiState: StateFlow<AccountUiState> =
        combine(
                deviceRepository.deviceState,
                accountRepository.accountExpiryState,
                _purchaseResult,
                _paymentAvailability
            ) { deviceState, accountExpiry, purchaseResult, paymentAvailability ->
                AccountUiState(
                    deviceName = deviceState.deviceName() ?: "",
                    accountNumber = deviceState.token() ?: "",
                    accountExpiry = accountExpiry.date(),
                    purchaseResult = purchaseResult,
                    billingPaymentState =
                        paymentAvailability?.toPaymentState() ?: PaymentState.Loading
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), AccountUiState.default())

    @Suppress("konsist.ensure public properties use permitted names")
    val enterTransitionEndAction = _enterTransitionEndAction.asSharedFlow()

    init {
        verifyPurchases()
        fetchPaymentAvailability()
    }

    fun onManageAccountClick() {
        viewModelScope.launch {
            _uiSideEffect.tryEmit(
                UiSideEffect.OpenAccountManagementPageInBrowser(
                    serviceConnectionManager.authTokenCache()?.fetchAuthToken() ?: ""
                )
            )
        }
    }

    fun onLogoutClick() {
        accountRepository.logout()
    }

    fun onTransitionAnimationEnd() {
        viewModelScope.launch { _enterTransitionEndAction.emit(Unit) }
    }

    fun startBillingPayment(productId: String) {
        viewModelScope.launch {
            try {
                paymentRepository?.purchaseBillingProduct(productId)?.collect(_purchaseResult)
            } finally {
                // Set result as null
                delay(2000L)
                _purchaseResult.emit(null)
            }
        }
    }

    fun verifyPurchases() {
        viewModelScope.launch { paymentRepository?.verifyPurchases() }
    }

    fun fetchPaymentAvailability() {
        viewModelScope.launch {
            val result =
                paymentRepository?.queryPaymentAvailability()
                    ?: PaymentAvailability.ProductsUnavailable
            _paymentAvailability.tryEmit(result)
        }
    }

    private fun PaymentAvailability.toPaymentState(): PaymentState =
        when (this) {
            PaymentAvailability.Error.ServiceUnavailable,
            PaymentAvailability.Error.BillingUnavailable -> PaymentState.Error.BillingError
            is PaymentAvailability.Error.Other -> PaymentState.Error.GenericError
            is PaymentAvailability.ProductsAvailable -> PaymentState.PaymentAvailable(products)
            PaymentAvailability.ProductsUnavailable -> PaymentState.NoPayment
        }

    sealed class UiSideEffect {
        data class OpenAccountManagementPageInBrowser(val token: String) : UiSideEffect()
    }
}

data class AccountUiState(
    val deviceName: String?,
    val accountNumber: String?,
    val accountExpiry: DateTime?,
    val billingPaymentState: PaymentState = PaymentState.Loading,
    val purchaseResult: PurchaseResult? = null,
) {
    companion object {
        fun default() =
            AccountUiState(
                deviceName = DeviceState.Unknown.deviceName(),
                accountNumber = DeviceState.Unknown.token(),
                accountExpiry = AccountExpiry.Missing.date(),
                billingPaymentState = PaymentState.Loading,
                purchaseResult = null,
            )
    }
}
