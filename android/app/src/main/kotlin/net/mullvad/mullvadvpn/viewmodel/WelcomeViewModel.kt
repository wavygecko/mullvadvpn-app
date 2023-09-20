package net.mullvad.mullvadvpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.PaymentProvider
import net.mullvad.mullvadvpn.compose.state.PaymentState
import net.mullvad.mullvadvpn.compose.state.WelcomeUiState
import net.mullvad.mullvadvpn.constant.ACCOUNT_EXPIRY_POLL_INTERVAL
import net.mullvad.mullvadvpn.lib.common.util.capitalizeFirstCharOfEachWord
import net.mullvad.mullvadvpn.lib.payment.PaymentRepository
import net.mullvad.mullvadvpn.lib.payment.model.PaymentAvailability
import net.mullvad.mullvadvpn.lib.payment.model.PurchaseResult
import net.mullvad.mullvadvpn.model.TunnelState
import net.mullvad.mullvadvpn.repository.AccountRepository
import net.mullvad.mullvadvpn.repository.DeviceRepository
import net.mullvad.mullvadvpn.ui.serviceconnection.ConnectionProxy
import net.mullvad.mullvadvpn.ui.serviceconnection.ServiceConnectionManager
import net.mullvad.mullvadvpn.ui.serviceconnection.ServiceConnectionState
import net.mullvad.mullvadvpn.ui.serviceconnection.authTokenCache
import net.mullvad.mullvadvpn.util.UNKNOWN_STATE_DEBOUNCE_DELAY_MILLISECONDS
import net.mullvad.mullvadvpn.util.addDebounceForUnknownState
import net.mullvad.mullvadvpn.util.callbackFlowFromNotifier
import org.joda.time.DateTime

@OptIn(FlowPreview::class)
class WelcomeViewModel(
    private val accountRepository: AccountRepository,
    private val deviceRepository: DeviceRepository,
    private val serviceConnectionManager: ServiceConnectionManager,
    paymentProvider: PaymentProvider,
    private val pollAccountExpiry: Boolean = true
) : ViewModel() {

    private val paymentRepository: PaymentRepository? = paymentProvider.paymentRepository

    private val _paymentAvailability = MutableStateFlow<PaymentAvailability?>(null)
    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    private val _uiSideEffect = MutableSharedFlow<UiSideEffect>(extraBufferCapacity = 1)
    val uiSideEffect = _uiSideEffect.asSharedFlow()

    val uiState =
        serviceConnectionManager.connectionState
            .flatMapLatest { state ->
                if (state is ServiceConnectionState.ConnectedReady) {
                    flowOf(state.container)
                } else {
                    emptyFlow()
                }
            }
            .flatMapLatest { serviceConnection ->
                combine(
                    serviceConnection.connectionProxy.tunnelUiStateFlow(),
                    deviceRepository.deviceState.debounce {
                        it.addDebounceForUnknownState(UNKNOWN_STATE_DEBOUNCE_DELAY_MILLISECONDS)
                    },
                    _paymentAvailability,
                    _purchaseResult
                ) { tunnelState, deviceState, paymentAvailability, purchaseResult ->
                    WelcomeUiState(
                        tunnelState = tunnelState,
                        accountNumber = deviceState.token(),
                        deviceName = deviceState.deviceName()?.capitalizeFirstCharOfEachWord(),
                        billingPaymentState =
                            paymentAvailability?.toPaymentState() ?: PaymentState.Loading,
                        purchaseResult = purchaseResult
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), WelcomeUiState())

    init {
        viewModelScope.launch {
            accountRepository.accountExpiryState.collectLatest { accountExpiry ->
                accountExpiry.date()?.let { expiry ->
                    val tomorrow = DateTime.now().plusHours(20)

                    if (expiry.isAfter(tomorrow)) {
                        _uiSideEffect.tryEmit(UiSideEffect.OpenConnectScreen)
                    }
                }
            }
        }
        viewModelScope.launch {
            while (pollAccountExpiry) {
                accountRepository.fetchAccountExpiry()
                delay(ACCOUNT_EXPIRY_POLL_INTERVAL)
            }
        }
        verifyPurchases()
        fetchPaymentAvailability()
    }

    private fun ConnectionProxy.tunnelUiStateFlow(): Flow<TunnelState> =
        callbackFlowFromNotifier(this.onUiStateChange)

    fun onSitePaymentClick() {
        viewModelScope.launch {
            _uiSideEffect.tryEmit(
                UiSideEffect.OpenAccountView(
                    serviceConnectionManager.authTokenCache()?.fetchAuthToken() ?: ""
                )
            )
        }
    }

    fun startBillingPayment(productId: String) {
        viewModelScope.launch {
            paymentRepository?.purchaseBillingProduct(productId)?.collect(_purchaseResult)
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

    sealed interface UiSideEffect {
        data class OpenAccountView(val token: String) : UiSideEffect

        data object OpenConnectScreen : UiSideEffect
    }
}
