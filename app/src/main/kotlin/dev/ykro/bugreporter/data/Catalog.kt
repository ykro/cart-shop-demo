package dev.ykro.bugreporter.data

import dev.ykro.bugreporter.R

object Catalog {
  val products: List<Product> =
    listOf(
      Product(1, "Headphones", "Over-ear, noise cancelling", 40_000, R.drawable.product_headphones),
      Product(2, "Keyboard", "Mechanical, 75%, hot-swappable", 120_000, R.drawable.product_keyboard),
      Product(3, "Mouse", "Wireless, 8K polling", 35_000, R.drawable.product_mouse),
      Product(4, "Cable", "USB-C 240 W braided, 2 m", 12_000, R.drawable.product_cable),
    )

  fun product(id: Int): Product? = products.firstOrNull { it.id == id }

  val coupons: Map<String, Coupon> =
    listOf(
        Coupon("HALF", CouponType.PERCENT, 50),
        Coupon("SAVE10", CouponType.PERCENT, 10),
        Coupon("OFF100", CouponType.FIXED, 10_000),
      )
      .associateBy { it.code }

  val user = User(name = "Ana Tester", email = "tester@example.com")
}
