package dev.ykro.bugreporter.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ykro.bugreporter.agent.ContextTools
import dev.ykro.bugreporter.data.CartLine
import dev.ykro.bugreporter.data.CartRepository
import dev.ykro.bugreporter.data.Catalog
import dev.ykro.bugreporter.data.Coupon
import dev.ykro.bugreporter.data.CouponType
import dev.ykro.bugreporter.data.FakeCatalogApi
import dev.ykro.bugreporter.instrumentation.Breadcrumbs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class CartUiState(
  val lines: List<CartLine> = emptyList(),
  val couponInput: String = "",
  val activeCoupon: Coupon? = null,
  val couponError: String? = null,
  val applyingCoupon: Boolean = false,
  val subtotalCents: Long = 0,
  val discountCents: Long = 0,
  val totalCents: Long = 0,
) {
  val itemCount: Int
    get() = lines.sumOf { it.quantity }
}

/**
 * The cart's view model, home of the demo bug `DISCOUNT-STALE`: the coupon discount is computed
 * once as an absolute amount when the coupon is applied and is never recomputed when the cart
 * changes. No exception, no log line — only the sequence of actions and the state dump explain it.
 */
class CartViewModel(
  private val cartRepo: CartRepository,
  private val api: FakeCatalogApi,
  private val breadcrumbs: Breadcrumbs,
  /** Publishes the current totals so the bug-reporter tools can read them. */
  private val totalsSink: (ContextTools.CartTotals) -> Unit = {},
) : ViewModel() {

  private val _uiState = MutableStateFlow(CartUiState())
  val uiState: StateFlow<CartUiState> = _uiState

  // BUG (DISCOUNT-STALE): an absolute amount frozen at apply time instead of the coupon rule.
  private var discountCents: Long = 0L
  private var activeCoupon: Coupon? = null

  init {
    viewModelScope.launch { cartRepo.observe().collect { recompute() } }
  }

  fun onCouponInput(text: String) {
    _uiState.update { it.copy(couponInput = text.uppercase(), couponError = null) }
  }

  fun applyCoupon() {
    val code = _uiState.value.couponInput.trim()
    if (code.isEmpty()) return
    viewModelScope.launch {
      _uiState.update { it.copy(applyingCoupon = true) }
      val coupon = api.validateCoupon(code)
      breadcrumbs.record("coupon:apply:$code:${if (coupon == null) "invalid" else "ok"}")
      if (coupon == null) {
        Timber.w("Coupon rejected: %s", code)
        _uiState.update { it.copy(applyingCoupon = false, couponError = "Invalid coupon") }
        return@launch
      }
      val subtotal = subtotalCents()
      discountCents =
        when (coupon.type) {
          CouponType.PERCENT -> subtotal * coupon.value / 100 // BUG: freezes the amount, not the percent
          CouponType.FIXED -> coupon.value
        }
      activeCoupon = coupon
      Timber.i("Coupon applied: %s", coupon.code)
      _uiState.update { it.copy(applyingCoupon = false, couponError = null) }
      recompute()
    }
  }

  fun removeCoupon() {
    breadcrumbs.record("coupon:remove")
    activeCoupon = null
    discountCents = 0
    _uiState.update { it.copy(couponInput = "") }
    viewModelScope.launch { recompute() }
  }

  fun addToCart(productId: Int, quantity: Int) {
    breadcrumbs.record("cart:add:$productId:$quantity")
    viewModelScope.launch {
      cartRepo.add(productId, quantity)
      Timber.i("Added product %d x%d", productId, quantity)
    }
  }

  fun changeQuantity(productId: Int, newQty: Int) {
    breadcrumbs.record("cart:qty:$productId:$newQty")
    viewModelScope.launch {
      cartRepo.setQuantity(productId, newQty)
      recompute() // BUG: discountCents is not recalculated for the new subtotal
    }
  }

  fun clearCart() {
    breadcrumbs.record("cart:clear")
    viewModelScope.launch { cartRepo.clear(); removeCoupon() }
  }

  private suspend fun subtotalCents(): Long = cartRepo.lines().sumOf { it.lineTotalCents }

  private suspend fun recompute() {
    val lines = cartRepo.lines()
    val subtotal = lines.sumOf { it.lineTotalCents }
    _uiState.update {
      it.copy(
        lines = lines,
        activeCoupon = activeCoupon,
        subtotalCents = subtotal,
        discountCents = discountCents,
        totalCents = subtotal - discountCents, // can go negative
      )
    }
    totalsSink(
      ContextTools.CartTotals(
        couponCode = activeCoupon?.code,
        couponRule = ContextTools.describeRule(activeCoupon?.type, activeCoupon?.value),
        subtotalCents = subtotal,
        discountCents = discountCents,
        totalCents = subtotal - discountCents,
      )
    )
  }
}
