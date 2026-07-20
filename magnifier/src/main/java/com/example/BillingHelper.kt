package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A Google Play Billing API-t kezelő segédosztály a "Támogatás" (Tip Jar) funkcióhoz.
 * Amennyiben a Google Play Billing nem elérhető (pl. offline, emulátor Play szolgáltatások nélkül,
 * vagy tesztkörnyezet), automatikusan egy biztonságos, tesztelhető Mock szimulációt futtat le.
 */
class BillingHelper(
    private val context: Context,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    var isReady = false
        private set

    // A Google Play Console-ban regisztrált termékek azonosítói (IAP termékek)
    val productIds = listOf("tip_small", "tip_medium", "tip_large")

    // A Google Play-ről lekért termék részletek reaktív listája
    val productDetailsList = mutableStateListOf<ProductDetails>()

    init {
        initializeBillingClient()
    }

    private fun initializeBillingClient() {
        try {
            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build()

            startConnection()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isReady = true
                    queryProducts()
                } else {
                    isReady = false
                }
            }

            override fun onBillingServiceDisconnected() {
                isReady = false
            }
        })
    }

    private fun queryProducts() {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()

        billingClient?.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, detailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                scope.launch {
                    productDetailsList.clear()
                    productDetailsList.addAll(detailsList)
                }
            }
        }
    }

    fun launchBillingFlow(callingContext: Context, productId: String, onMockSuccess: () -> Unit) {
        val activity = callingContext.findActivity()
        if (activity == null || !isReady || billingClient == null) {
            simulateMockPurchase(productId, onMockSuccess)
            return
        }

        val productDetails = productDetailsList.find { it.productId == productId }
        if (productDetails == null) {
            simulateMockPurchase(productId, onMockSuccess)
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    private fun simulateMockPurchase(productId: String, onMockSuccess: () -> Unit) {
        val tierName = when (productId) {
            "tip_small" -> "Espresso ☕ (500 Ft)"
            "tip_medium" -> "Cappuccino ☕✨ (1500 Ft)"
            "tip_large" -> "Latte ☕💖 (3000 Ft)"
            else -> "Támogatás"
        }
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "Szimulált borravaló sikeres: $tierName", Toast.LENGTH_LONG).show()
            onMockSuccess()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(context, "Vásárlás megszakítva", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Hiba a vásárlás során: ${billingResult.debugMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // A borravalók consumable (elfogyasztható) tételek, így minden alkalommal
            // újra megvásárolhatóvá kell őket tennünk consumeAsync hívással.
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient?.consumeAsync(consumeParams) { billingResult, _ ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Köszönjük a támogatást! ❤️", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
