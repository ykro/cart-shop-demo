package dev.ykro.bugreporter

import dev.ykro.bugreporter.agent.Redactor
import dev.ykro.bugreporter.data.Catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
  private val redactor = Redactor(knownNames = listOf(Catalog.user.name), knownEmails = listOf(Catalog.user.email))

  @Test
  fun `strips the signed-in user's name and email`() {
    val out = redactor.redact("Signed in as Ana Tester <tester@example.com>")
    assertFalse(out.contains("tester@example.com"))
    assertFalse(out.contains("Ana Tester"))
    assertEquals("Signed in as ${Redactor.NAME} <${Redactor.EMAIL}>", out)
  }

  @Test
  fun `strips arbitrary emails, phones and tokens`() {
    val out = redactor.redact("mail someone.else@corp.io or call +1 (555) 010-9999, token ghp_abcdefghijklmnopqrstuvwxyz0123 Bearer abc.def")
    assertFalse(out.contains("@corp.io"))
    assertFalse(out.contains("555"))
    assertFalse(out.contains("ghp_"))
    assertFalse(out.contains("abc.def"))
    assertTrue(out.contains(Redactor.TOKEN))
  }

  @Test
  fun `leaves breadcrumbs and money untouched`() {
    val line = "coupon:apply:HALF:ok total=-20000 qty=3"
    assertEquals(line, redactor.redact(line))
  }
}
