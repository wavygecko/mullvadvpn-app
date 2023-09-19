package net.mullvad.mullvadvpn.lib.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import net.mullvad.mullvadvpn.lib.billing.extension.toPaymentProducts
import net.mullvad.mullvadvpn.lib.billing.extension.toPurchaseResult
import net.mullvad.mullvadvpn.lib.billing.model.BillingException
import net.mullvad.mullvadvpn.lib.billing.model.PurchaseEvent
import net.mullvad.mullvadvpn.lib.payment.PaymentRepository
import net.mullvad.mullvadvpn.lib.payment.ProductIds
import net.mullvad.mullvadvpn.lib.payment.model.PaymentAvailability
import net.mullvad.mullvadvpn.lib.payment.model.PurchaseResult
import net.mullvad.mullvadvpn.lib.payment.model.VerificationResult

class BillingPaymentRepository(private val billingRepository: BillingRepository) :
    PaymentRepository {

    override suspend fun queryPaymentAvailability(): PaymentAvailability = getBillingProducts()

    override fun purchaseBillingProduct(productId: String): Flow<PurchaseResult> = flow {
        emit(PurchaseResult.PurchaseStarted)
        // Get transaction id
        val transactionId: String =
            fetchTransactionId()
                ?: run {
                    emit(PurchaseResult.Error.TransactionIdError(null))
                    return@flow
                }

        val result =
            billingRepository.startPurchaseFlow(
                productId = productId,
                transactionId = transactionId
            )

        if (result.responseCode == BillingResponseCode.OK) {
            emit(PurchaseResult.BillingFlowStarted)
        } else {
            emit(
                PurchaseResult.Error.BillingError(
                    BillingException(result.responseCode, result.debugMessage)
                )
            )
            return@flow
        }

        // Wait for a callback from the billing library
        val event = billingRepository.purchaseEvents.firstOrNull()

        event?.let {
            emit(event.toPurchaseResult())

            // Verify towards api
            if (event is PurchaseEvent.PurchaseCompleted) {
                if (verifyPurchase(event.purchases.first()) == VerificationResult.Success) {
                    emit(PurchaseResult.PurchaseCompleted)
                } else {
                    emit(PurchaseResult.Error.VerificationError(null))
                }
            }
        } ?: emit(PurchaseResult.Error.BillingError(null))

        return@flow
    }

    override suspend fun verifyPurchases(): VerificationResult {
        val result = billingRepository.queryPurchases()
        return when {
            result.billingResult.responseCode == BillingResponseCode.OK &&
                result.purchasesList.isNotEmpty() -> verifyPurchase(result.purchasesList.first())
            result.billingResult.responseCode == BillingResponseCode.OK ->
                VerificationResult.NoVerification
            else ->
                VerificationResult.Error(
                    BillingException(
                        result.billingResult.responseCode,
                        result.billingResult.debugMessage
                    )
                )
        }
    }

    private suspend fun getBillingProducts(): PaymentAvailability {
        val result = billingRepository.queryProducts(listOf(ProductIds.OneMonth))
        return when {
            result.billingResult.responseCode == BillingResponseCode.OK &&
                result.productDetailsList.isNullOrEmpty() -> {
                PaymentAvailability.ProductsUnavailable
            }
            result.billingResult.responseCode == BillingResponseCode.OK ->
                PaymentAvailability.ProductsAvailable(
                    result.productDetailsList?.toPaymentProducts() ?: emptyList()
                )
            result.billingResult.responseCode == BillingResponseCode.BILLING_UNAVAILABLE ->
                PaymentAvailability.Error.BillingUnavailable
            result.billingResult.responseCode == BillingResponseCode.SERVICE_UNAVAILABLE ->
                PaymentAvailability.Error.ServiceUnavailable
            else ->
                PaymentAvailability.Error.Other(
                    BillingException(
                        result.billingResult.responseCode,
                        result.billingResult.debugMessage
                    )
                )
        }
    }

    private suspend fun fetchTransactionId(): String? {
        // Placeholder function
        // delay to simulate network request
        delay(1500L)
        return "BOOPITOBOP"
    }

    private suspend fun verifyPurchase(purchase: Purchase): VerificationResult {
        // Placeholder function
        // delay ot simulate network request
        delay(1500L)
        return VerificationResult.Success
    }
}
