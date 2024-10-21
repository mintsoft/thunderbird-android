package app.k9mail.feature.funding.googleplay.data.remote

import app.k9mail.core.common.cache.Cache
import app.k9mail.feature.funding.googleplay.data.DataContract
import app.k9mail.feature.funding.googleplay.data.DataContract.Remote
import app.k9mail.feature.funding.googleplay.domain.entity.Contribution
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import timber.log.Timber

class GoogleBillingPurchaseHandler(
    private val productCache: Cache<String, ProductDetails>,
    private val productMapper: DataContract.Mapper.Product,
) : Remote.GoogleBillingPurchaseHandler {

    override suspend fun handlePurchases(
        clientProvider: Remote.GoogleBillingClientProvider,
        purchases: List<Purchase>,
    ): List<Contribution> {
        return purchases.flatMap { purchase ->
            handlePurchase(clientProvider.current, purchase)
        }
    }

    private suspend fun handlePurchase(
        billingClient: BillingClient,
        purchase: Purchase,
    ): List<Contribution> {
        // TODO verify purchase with public key
        consumePurchase(billingClient, purchase)
        acknowledgePurchase(billingClient, purchase)

        return extractContributions(purchase)
    }

    private suspend fun acknowledgePurchase(
        billingClient: BillingClient,
        purchase: Purchase,
    ) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                val acknowledgeResult: BillingResult = billingClient.acknowledgePurchase(acknowledgePurchaseParams)

                if (acknowledgeResult.responseCode != BillingResponseCode.OK) {
                    // TODO success
                } else {
                    // handle acknowledge error
                    Timber.e("acknowledgePurchase failed")
                }
            } else {
                Timber.e("purchase already acknowledged")
            }
        } else {
            Timber.e("purchase not purchased")
        }
    }

    private suspend fun consumePurchase(
        billingClient: BillingClient,
        purchase: Purchase,
    ) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        // This could fail but we can ignore the error as we handle purchases
        // the next time the purchases are requested
        billingClient.consumePurchase(consumeParams)
    }

    private fun extractContributions(purchase: Purchase): List<Contribution> {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            return emptyList()
        }

        return purchase.products.mapNotNull { product ->
            productCache[product]
        }.filter { it.productType == ProductType.SUBS }
            .map { productMapper.mapToContribution(it) }
    }
}
