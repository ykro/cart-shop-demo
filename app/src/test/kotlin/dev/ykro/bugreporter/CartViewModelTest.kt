package dev.ykro.bugreporter

import dev.ykro.bugreporter.agent.ContextTools
import dev.ykro.bugreporter.cart.CartViewModel
import dev.ykro.bugreporter.data.CartDao
import dev.ykro.bugreporter.data.CartItemEntity
import dev.ykro.bugreporter.data.CartRepository
import dev.ykro.bugreporter.data.FakeCatalogApi
import dev.ykro.bugreporter.instrumentation.Breadcrumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pins the intentional DISCOUNT-STALE bug so the demo repro never silently disappears. */
@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  private class InMemoryDao : CartDao {
    private val items = MutableStateFlow<List<CartItemEntity>>(emptyList())
    override fun observeAll(): Flow<List<CartItemEntity>> = items
    override suspend fun all() = items.value
    override suspend fun byProduct(productId: Int) = items.value.firstOrNull { it.productId == productId }
    override suspend fun upsert(item: CartItemEntity) { items.value = items.value.filter { it.productId != item.productId } + item }
    override suspend fun delete(productId: Int) { items.value = items.value.filter { it.productId != productId } }
    override suspend fun clear() { items.value = emptyList() }
  }

  @Before fun setUp() = Dispatchers.setMain(dispatcher)
  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun `repro - headphones x3, HALF, then x1 gives a negative total`() = runTest(dispatcher) {
    val breadcrumbs = Breadcrumbs()
    var totals: ContextTools.CartTotals? = null
    val vm = CartViewModel(CartRepository(InMemoryDao()), FakeCatalogApi(), breadcrumbs, totalsSink = { totals = it })

    vm.addToCart(productId = 1, quantity = 1); advanceUntilIdle()
    vm.changeQuantity(1, 3); advanceUntilIdle()
    vm.onCouponInput("half"); vm.applyCoupon(); advanceUntilIdle()
    assertEquals(120_000L, vm.uiState.value.subtotalCents)
    assertEquals(60_000L, vm.uiState.value.discountCents)
    assertEquals(60_000L, vm.uiState.value.totalCents)

    vm.changeQuantity(1, 1); advanceUntilIdle()
    val state = vm.uiState.value
    assertEquals(40_000L, state.subtotalCents)
    assertEquals(60_000L, state.discountCents) // stale: should be 20_000
    assertEquals(-20_000L, state.totalCents)
    assertTrue(state.totalCents < 0)

    assertEquals(listOf("cart:add:1:1", "cart:qty:1:3", "coupon:apply:HALF:ok", "cart:qty:1:1"), breadcrumbs.last().map { it.action })
    assertEquals("HALF", totals?.couponCode)
    assertEquals(-20_000L, totals?.totalCents)
  }

  @Test
  fun `invalid coupon is rejected and recorded`() = runTest(dispatcher) {
    val breadcrumbs = Breadcrumbs()
    val vm = CartViewModel(CartRepository(InMemoryDao()), FakeCatalogApi(), breadcrumbs)
    vm.onCouponInput("NOPE"); vm.applyCoupon(); advanceUntilIdle()
    assertEquals("Invalid coupon", vm.uiState.value.couponError)
    assertEquals(listOf("coupon:apply:NOPE:invalid"), breadcrumbs.last().map { it.action })
  }
}
