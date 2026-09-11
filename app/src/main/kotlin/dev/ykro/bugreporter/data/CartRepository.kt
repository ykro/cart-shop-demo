package dev.ykro.bugreporter.data

import kotlinx.coroutines.flow.Flow

data class CartLine(val product: Product, val quantity: Int) {
  val lineTotalCents: Long
    get() = product.priceCents * quantity
}

class CartRepository(private val dao: CartDao) {
  fun observe(): Flow<List<CartItemEntity>> = dao.observeAll()

  suspend fun lines(): List<CartLine> =
    dao.all().mapNotNull { e -> Catalog.product(e.productId)?.let { CartLine(it, e.quantity) } }

  suspend fun add(productId: Int, quantity: Int = 1) {
    val current = dao.byProduct(productId)?.quantity ?: 0
    dao.upsert(CartItemEntity(productId, current + quantity))
  }

  suspend fun setQuantity(productId: Int, quantity: Int) {
    if (quantity <= 0) dao.delete(productId) else dao.upsert(CartItemEntity(productId, quantity))
  }

  suspend fun clear() = dao.clear()
}
