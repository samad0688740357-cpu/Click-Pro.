package id.biz.rrndev.autoclicker.billing

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PremiumState(
    val isPremium: Boolean = false,
    val priceText: String? = null,
    val discountText: String? = null,
    val canPurchase: Boolean = false,
    val lastMessage: String? = null
)

class PremiumManager(context: Context) {

    companion object {
        const val PRODUCT_ID = "premium_upgrade"
        private const val PREFS = "premium_prefs"
        private const val KEY_PLAY_PREMIUM = "is_play_premium_lifetime"
        private const val KEY_DEBUG_PREMIUM = "is_debug_premium_lifetime"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val isDebugBuild =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private var productDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            setMessage("Purchase could not be completed. Please try again.")
        }
    }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    private val _state = MutableStateFlow(
        PremiumState(
            isPremium = hasPremiumEntitlement(),
            priceText = if (isDebugBuild) "Debug build" else null,
            discountText = if (isDebugBuild) {
                "Debug builds can activate Premium without Google Play Billing. Release builds require the real Play purchase."
            } else null,
            canPurchase = isDebugBuild
        )
    )
    val state: StateFlow<PremiumState> = _state

    fun start() {
        if (isDebugBuild) {
            _state.value = createState(
                priceText = "Debug build",
                discountText = "Debug builds can activate Premium without Google Play Billing. Release builds require the real Play purchase."
            )
            return
        }
        if (billingClient.isReady) {
            queryProducts()
            restorePurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restorePurchases()
                } else {
                    setMessage("Premium purchase is unavailable right now.")
                }
            }

            override fun onBillingServiceDisconnected() {
                setMessage("Premium purchase connection was interrupted.")
            }
        })
    }

    fun restorePurchases() {
        if (isDebugBuild) {
            _state.value = createState(
                priceText = "Debug build",
                discountText = "Debug premium is stored locally and ignored by release builds."
            )
            return
        }
        if (!billingClient.isReady) {
            start()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    fun launchPurchase(activity: Activity): BillingResult? {
        if (isDebugBuild) {
            grantDebugPremium()
            return null
        }
        val details = productDetails ?: run {
            queryProducts()
            setMessage("Premium price is still loading. Please try again.")
            return null
        }
        val detailsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.let { offerToken ->
            detailsBuilder.setOfferToken(offerToken)
        }
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(detailsBuilder.build()))
            .build()
        return billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun queryProducts() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = result.productDetailsList.firstOrNull()
                val offer = productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                val price = offer?.formattedPrice
                _state.value = _state.value.copy(
                    isPremium = hasPremiumEntitlement(),
                    priceText = price,
                    discountText = price?.let {
                        "Final price is shown by Google Play. Any eligible discount is applied automatically."
                    },
                    canPurchase = productDetails != null
                )
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        purchases
            .filter { purchase ->
                purchase.products.contains(PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            .forEach { purchase ->
                grantPlayPremium()
                if (!purchase.isAcknowledged) {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(params) { billingResult ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            setMessage("Premium Lifetime is active.")
                        }
                    }
                }
            }
    }

    private fun grantPlayPremium() {
        prefs.edit().putBoolean(KEY_PLAY_PREMIUM, true).apply()
        _state.value = createState(
            priceText = _state.value.priceText,
            discountText = _state.value.discountText,
            lastMessage = "Premium Lifetime is active."
        )
    }

    private fun grantDebugPremium() {
        prefs.edit().putBoolean(KEY_DEBUG_PREMIUM, true).apply()
        _state.value = createState(
            priceText = "Debug build",
            discountText = "Debug Premium is active locally. Release builds still require Google Play Billing.",
            lastMessage = "Debug Premium Lifetime is active."
        )
    }

    private fun hasPremiumEntitlement(): Boolean {
        val hasPlayPremium = prefs.getBoolean(KEY_PLAY_PREMIUM, false)
        val hasDebugPremium = isDebugBuild && prefs.getBoolean(KEY_DEBUG_PREMIUM, false)
        return hasPlayPremium || hasDebugPremium
    }

    private fun createState(
        priceText: String? = null,
        discountText: String? = null,
        canPurchase: Boolean = isDebugBuild || productDetails != null,
        lastMessage: String? = null
    ): PremiumState {
        return PremiumState(
            isPremium = hasPremiumEntitlement(),
            priceText = priceText,
            discountText = discountText,
            canPurchase = canPurchase,
            lastMessage = lastMessage
        )
    }

    private fun setMessage(message: String) {
        _state.value = _state.value.copy(lastMessage = message)
    }
}
