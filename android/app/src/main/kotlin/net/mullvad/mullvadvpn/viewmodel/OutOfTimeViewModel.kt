package net.mullvad.mullvadvpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.PaymentProvider
import net.mullvad.mullvadvpn.compose.state.OutOfTimeUiState
import net.mullvad.mullvadvpn.compose.state.PaymentState
import net.mullvad.mullvadvpn.constant.ACCOUNT_EXPIRY_POLL_INTERVAL
import net.mullvad.mullvadvpn.lib.payment.extensions.toPurchaseResult
import net.mullvad.mullvadvpn.lib.payment.model.PaymentAvailability
import net.mullvad.mullvadvpn.lib.payment.model.PurchaseResult
import net.mullvad.mullvadvpn.lib.payment.model.VerificationResult
import net.mullvad.mullvadvpn.model.TunnelState
import net.mullvad.mullvadvpn.repository.AccountRepository
import net.mullvad.mullvadvpn.repository.DeviceRepository
import net.mullvad.mullvadvpn.ui.serviceconnection.ConnectionProxy
import net.mullvad.mullvadvpn.ui.serviceconnection.ServiceConnectionManager
import net.mullvad.mullvadvpn.ui.serviceconnection.ServiceConnectionState
import net.mullvad.mullvadvpn.ui.serviceconnection.authTokenCache
import net.mullvad.mullvadvpn.ui.serviceconnection.connectionProxy
import net.mullvad.mullvadvpn.util.callbackFlowFromNotifier
import org.joda.time.DateTime

class OutOfTimeViewModel(
    private val accountRepository: AccountRepository,
    private val serviceConnectionManager: ServiceConnectionManager,
    private val deviceRepository: DeviceRepository,
    paymentProvider: PaymentProvider,
    private val pollAccountExpiry: Boolean = true,
) : ViewModel() {
    private val paymentRepository = paymentProvider.paymentRepository

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
                    serviceConnection.connectionProxy.tunnelStateFlow(),
                    deviceRepository.deviceState,
                    _paymentAvailability,
                    _purchaseResult
                ) { tunnelState, deviceState, paymentAvailability, purchaseResult ->
                    OutOfTimeUiState(
                        tunnelState = tunnelState,
                        deviceName = deviceState.deviceName() ?: "",
                        billingPaymentState = paymentAvailability?.toPaymentState()
                                ?: PaymentState.NoPayment,
                        purchaseResult = purchaseResult
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), OutOfTimeUiState())

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
        verifyPurchases(updatePurchaseResult = false)
        fetchPaymentAvailability()
    }

    private fun ConnectionProxy.tunnelStateFlow(): Flow<TunnelState> =
        callbackFlowFromNotifier(this.onStateChange)

    fun onSitePaymentClick() {
        viewModelScope.launch {
            _uiSideEffect.tryEmit(
                UiSideEffect.OpenAccountView(
                    serviceConnectionManager.authTokenCache()?.fetchAuthToken() ?: ""
                )
            )
        }
    }

    fun onDisconnectClick() {
        viewModelScope.launch { serviceConnectionManager.connectionProxy()?.disconnect() }
    }

    fun startBillingPayment(productId: String) {
        viewModelScope.launch {
            try {
                paymentRepository?.purchaseBillingProduct(productId)?.collect(_purchaseResult)
            } finally {
                // Update payment status in case the payment is pending or the verification failed
                fetchPaymentAvailability()
            }
        }
    }

    fun verifyPurchases(updatePurchaseResult: Boolean = true) {
        viewModelScope.launch {
            if (updatePurchaseResult) {
                paymentRepository
                    ?.verifyPurchases()
                    ?.map(VerificationResult::toPurchaseResult)
                    ?.collect(_purchaseResult)
            } else {
                paymentRepository?.verifyPurchases()
            }
        }
    }

    fun fetchPaymentAvailability() {
        viewModelScope.launch {
            _paymentAvailability.emit(PaymentAvailability.Loading)
            delay(100L) // So that the ui gets a new state in retries
            paymentRepository?.queryPaymentAvailability()?.collect(_paymentAvailability)
                ?: run { _paymentAvailability.emit(PaymentAvailability.ProductsUnavailable) }
        }
    }

    private fun PaymentAvailability.toPaymentState(): PaymentState =
        when (this) {
            PaymentAvailability.Error.ServiceUnavailable,
            PaymentAvailability.Error.BillingUnavailable -> PaymentState.Error.BillingError
            is PaymentAvailability.Error.Other -> PaymentState.Error.GenericError
            is PaymentAvailability.ProductsAvailable -> PaymentState.PaymentAvailable(products)
            PaymentAvailability.ProductsUnavailable -> PaymentState.NoPayment
            PaymentAvailability.Loading -> PaymentState.Loading
        }

    sealed interface UiSideEffect {
        data class OpenAccountView(val token: String) : UiSideEffect

        data object OpenConnectScreen : UiSideEffect
    }
}
