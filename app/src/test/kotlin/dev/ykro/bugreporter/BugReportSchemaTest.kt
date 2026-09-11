package dev.ykro.bugreporter

import dev.ykro.bugreporter.agent.BugReportSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BugReportSchemaTest {
  @Test
  fun `parses a question turn`() {
    val turn = BugReportSchema.parseTurn("""{"status":"QUESTION","question":"What total did you expect?"}""")
    assertEquals("QUESTION", turn?.status)
    assertEquals("What total did you expect?", turn?.question)
  }

  @Test
  fun `parses a fenced report turn with optional fields missing`() {
    val text =
      """
      ```json
      {"status":"REPORT","report":{"title":"Negative total","severity":"HIGH","area":"cart",
       "stepsToReproduce":["Add Headphones","Apply HALF","Set quantity to 1"],
       "expectedBehavior":"$200.00","actualBehavior":"-$200.00","environment":"sdk_gphone64 Android 16"}}
      ```
      """.trimIndent()
    val turn = BugReportSchema.parseTurn(text)
    assertNotNull(turn?.report)
    assertEquals(3, turn?.report?.stepsToReproduce?.size)
    assertEquals(emptyList<String>(), turn?.report?.labels)
    assertNull(turn?.report?.hypothesis)
  }

  @Test
  fun `returns null for plain prose`() {
    assertNull(BugReportSchema.parseTurn("I could not do that."))
  }

  @Test
  fun `schema requires the status field`() {
    assertEquals(listOf("status"), BugReportSchema.turn.required)
  }
}
