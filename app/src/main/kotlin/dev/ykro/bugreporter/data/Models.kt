package dev.ykro.bugreporter.data

import androidx.annotation.DrawableRes

data class Product(
  val id: Int,
  val name: String,
  val tagline: String,
  val priceCents: Long,
  @DrawableRes val imageRes: Int,
)

enum class CouponType { PERCENT, FIXED }

data class Coupon(val code: String, val type: CouponType, val value: Long)

/** A fake signed-in user. It exists only so there is PII for the redactor to strip. */
data class User(val name: String, val email: String)

fun Long.asMoney(): String {
  val negative = this < 0
  val abs = kotlin.math.abs(this)
  val dollars = abs / 100
  val cents = abs % 100
  val grouped = dollars.toString().reversed().chunked(3).joinToString(",").reversed()
  return (if (negative) "−$" else "$") + grouped + "." + cents.toString().padStart(2, '0')
}
