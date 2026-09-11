package dev.ykro.bugreporter

import dev.ykro.bugreporter.instrumentation.Breadcrumbs
import org.junit.Assert.assertEquals
import org.junit.Test

class BreadcrumbsTest {
  @Test
  fun `keeps only the newest entries in order`() {
    var now = 1_000L
    val crumbs = Breadcrumbs(capacity = 3, clock = { now })
    listOf("a", "b", "c", "d").forEach { now += 10; crumbs.record(it) }
    assertEquals(listOf("b", "c", "d"), crumbs.last().map { it.action })
    assertEquals(listOf(20L, 30L, 40L), crumbs.last().map { it.tMs })
  }

  @Test
  fun `limit returns the tail`() {
    val crumbs = Breadcrumbs(capacity = 10)
    repeat(5) { crumbs.record("x$it") }
    assertEquals(listOf("x3", "x4"), crumbs.last(2).map { it.action })
  }
}
