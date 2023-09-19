package net.mullvad.mullvadvpn.viewmodel

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.mullvad.mullvadvpn.PaymentProvider
import net.mullvad.mullvadvpn.compose.state.PaymentState
import net.mullvad.mullvadvpn.lib.common.test.TestCoroutineRule
import net.mullvad.mullvadvpn.lib.common.test.assertLists
import net.mullvad.mullvadvpn.lib.payment.PaymentRepository
import net.mullvad.mullvadvpn.lib.payment.model.PaymentAvailability
import net.mullvad.mullvadvpn.lib.payment.model.PaymentProduct
import net.mullvad.mullvadvpn.lib.payment.model.PurchaseResult
import net.mullvad.mullvadvpn.lib.payment.model.VerificationResult
import net.mullvad.mullvadvpn.model.AccountAndDevice
import net.mullvad.mullvadvpn.model.AccountExpiry
import net.mullvad.mullvadvpn.model.Device
import net.mullvad.mullvadvpn.model.DeviceState
import net.mullvad.mullvadvpn.repository.AccountRepository
import net.mullvad.mullvadvpn.repository.DeviceRepository
import net.mullvad.mullvadvpn.ui.serviceconnection.AuthTokenCache
import net.mullvad.mullvadvpn.ui.serviceconnection.ServiceConnectionManager
import net.mullvad.mullvadvpn.ui.serviceconnection.authTokenCache
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccountViewModelTest {
    @get:Rule val testCoroutineRule = TestCoroutineRule()

    private val mockAccountRepository: AccountRepository = mockk(relaxUnitFun = true)
    private val mockServiceConnectionManager: ServiceConnectionManager = mockk()
    private val mockDeviceRepository: DeviceRepository = mockk()
    private val mockAuthTokenCache: AuthTokenCache = mockk()
    private val mockPaymentProvider: PaymentProvider = mockk()
    private val mockPaymentRepository: PaymentRepository = mockk()

    private val deviceState: MutableStateFlow<DeviceState> = MutableStateFlow(DeviceState.Initial)
    private val purchaseResult =
        MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 1, replay = 1)
    private val accountExpiryState = MutableStateFlow(AccountExpiry.Missing)

    private val dummyAccountAndDevice: AccountAndDevice =
        AccountAndDevice(
            DUMMY_DEVICE_NAME,
            Device(
                id = "fake_id",
                name = "fake_name",
                pubkey = byteArrayOf(),
                created = "mock_date"
            )
        )

    private lateinit var viewModel: AccountViewModel

    @Before
    fun setUp() {
        mockkStatic(CACHE_EXTENSION_CLASS)
        coEvery { mockPaymentRepository.verifyPurchases() } returns
            VerificationResult.NoVerification
        coEvery { mockPaymentRepository.queryPaymentAvailability() } returns
            PaymentAvailability.ProductsUnavailable
        every { mockPaymentProvider.paymentRepository } returns mockPaymentRepository
        every { mockServiceConnectionManager.authTokenCache() } returns mockAuthTokenCache
        every { mockDeviceRepository.deviceState } returns deviceState
        every { mockAccountRepository.accountExpiryState } returns accountExpiryState
        every { mockPaymentRepository.purchaseBillingProduct(any()) } returns purchaseResult

        viewModel =
            AccountViewModel(
                accountRepository = mockAccountRepository,
                serviceConnectionManager = mockServiceConnectionManager,
                deviceRepository = mockDeviceRepository,
                paymentProvider = mockPaymentProvider
            )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testAccountLoggedInState() = runTest {
        // Arrange
        val mockPaymentAvailability = PaymentAvailability.ProductsUnavailable
        coEvery { mockPaymentRepository.queryPaymentAvailability() } returns mockPaymentAvailability

        // Act, Assert
        viewModel.uiState.test {
            var result = awaitItem()
            assertEquals(null, result.deviceName)
            deviceState.value = DeviceState.LoggedIn(accountAndDevice = dummyAccountAndDevice)
            result = awaitItem()
            assertEquals(DUMMY_DEVICE_NAME, result.accountNumber)
        }
    }

    @Test
    fun testOnLogoutClick() {
        // Act
        viewModel.onLogoutClick()

        // Assert
        verify { mockAccountRepository.logout() }
    }

    @Test
    fun testVerifyPurchases() = runTest {
        // Act
        viewModel.verifyPurchases()

        // Assert
        coVerify { mockPaymentRepository.verifyPurchases() }
    }

    @Test
    fun testBillingProductsUnavailableState() = runTest {
        // Arrange in setup

        // Act, Assert
        viewModel.uiState.test {
            val result = awaitItem().billingPaymentState
            assertIs<PaymentState.NoPayment>(result)
        }
    }

    @Test
    fun testBillingProductsGenericErrorState() = runTest {
        // Arrange
        val mockPaymentAvailability = PaymentAvailability.Error.Other(mockk())
        coEvery { mockPaymentRepository.queryPaymentAvailability() } returns mockPaymentAvailability

        // Act, Assert
        viewModel.uiState.test {
            // From init
            assertIs<PaymentState.NoPayment>(awaitItem().billingPaymentState)
            viewModel.fetchPaymentAvailability()
            val result = awaitItem().billingPaymentState
            assertIs<PaymentState.GenericError>(result)
        }
    }

    @Test
    fun testBillingProductsBillingErrorState() = runTest {
        // Arrange
        val mockPaymentAvailability = PaymentAvailability.Error.BillingUnavailable
        coEvery { mockPaymentRepository.queryPaymentAvailability() } returns mockPaymentAvailability

        // Act, Assert
        viewModel.uiState.test {
            // From init
            assertIs<PaymentState.NoPayment>(awaitItem().billingPaymentState)
            viewModel.fetchPaymentAvailability()
            val result = awaitItem().billingPaymentState
            assertIs<PaymentState.BillingError>(result)
        }
    }

    @Test
    fun testBillingProductsPaymentAvailableState() = runTest {
        // Arrange
        val mockProduct: PaymentProduct = mockk()
        val expectedProductList = listOf(mockProduct)
        val mockPaymentAvailability = PaymentAvailability.ProductsAvailable(listOf(mockProduct))
        coEvery { mockPaymentRepository.queryPaymentAvailability() } returns mockPaymentAvailability

        // Act, Assert
        viewModel.uiState.test {
            // From init
            assertIs<PaymentState.NoPayment>(awaitItem().billingPaymentState)
            viewModel.fetchPaymentAvailability()
            val result = awaitItem().billingPaymentState
            assertIs<PaymentState.PaymentAvailable>(result)
            assertLists(expectedProductList, result.products)
        }
    }

    @Test
    fun testBillingVerificationError() = runTest {
        // Arrange
        val mockPaymentAvailability = PaymentAvailability.ProductsUnavailable
        coEvery { mockPaymentRepository.queryPaymentAvailability() } returns mockPaymentAvailability

        // Act, Assert
        viewModel.uiState.test {
            // Default item
            awaitItem()
            purchaseResult.tryEmit(PurchaseResult.Error.VerificationError(null))
            val result = awaitItem().purchaseResult
            assertIs<PurchaseResult.Error.VerificationError>(result)
        }
    }

    @Test
    fun testBillingUserCancelled() = runTest {
        // Arrange
        val mockPaymentAvailability = PaymentAvailability.ProductsUnavailable
        coEvery { mockPaymentRepository.queryPaymentAvailability() } returns mockPaymentAvailability

        // Act, Assert
        viewModel.uiState.test {
            // Default item
            awaitItem()
            purchaseResult.tryEmit(PurchaseResult.PurchaseCancelled)
            assertIs<PurchaseResult.PurchaseCancelled>(awaitItem().purchaseResult)
        }
    }

    @Test
    fun testBillingPurchaseCompleted() = runTest {
        // Arrange
        val mockPaymentAvailability = PaymentAvailability.ProductsUnavailable
        coEvery { mockPaymentRepository.queryPaymentAvailability() } returns mockPaymentAvailability

        // Act, Assert
        viewModel.uiState.test {
            // Default item
            awaitItem()
            purchaseResult.tryEmit(PurchaseResult.PurchaseCompleted)
            val result = awaitItem().purchaseResult
            assertIs<PurchaseResult.PurchaseCompleted>(result)
        }
    }

    @Test
    fun testStartBillingPayment() {
        // Arrange
        val mockProductId = "MOCK"

        // Act
        viewModel.startBillingPayment(mockProductId)

        // Assert
        coVerify { mockPaymentRepository.purchaseBillingProduct(mockProductId) }
    }

    companion object {
        private const val CACHE_EXTENSION_CLASS = "net.mullvad.mullvadvpn.util.CacheExtensionsKt"
        private const val DUMMY_DEVICE_NAME = "fake_name"
    }
}
