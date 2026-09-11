package dev.ykro.bugreporter.agent

import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the agent returns on every turn. One JSON object; the UI renders it by [status]. */
@Serializable
data class BugReportTurn(
  val status: String, // QUESTION | REPORT | DONE | ERROR
  val question: String? = null,
  val message: String? = null,
  val report: BugReport? = null,
  val issue: IssueRef? = null,
)

@Serializable
data class BugReport(
  val title: String,
  val severity: String, // LOW | MEDIUM | HIGH | CRITICAL
  val area: String, // cart | checkout | catalog | other
  val stepsToReproduce: List<String>,
  val expectedBehavior: String,
  val actualBehavior: String,
  val environment: String,
  val evidence: List<String> = emptyList(),
  val hypothesis: String? = null,
  val labels: List<String> = emptyList(),
)

@Serializable data class IssueRef(val number: Int, val url: String)

object BugReportSchema {
  private fun str(desc: String, enum: List<String>? = null) = Schema(type = Type.STRING, description = desc, enum = enum)
  private fun strList(desc: String) = Schema(type = Type.ARRAY, description = desc, items = Schema(type = Type.STRING))

  val report: Schema =
    Schema(
      type = Type.OBJECT,
      description = "A structured, reproducible bug report.",
      properties =
        mapOf(
          "title" to str("Short title, imperative or describing the symptom"),
          "severity" to str("Severity per the team's severity guide", listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")),
          "area" to str("Area of the app", listOf("cart", "checkout", "catalog", "other")),
          "stepsToReproduce" to strList("Numbered steps, one action each, derived from the breadcrumbs"),
          "expectedBehavior" to str("What the user expected, with concrete values"),
          "actualBehavior" to str("What actually happened, with concrete values"),
          "environment" to str("Device model, Android version, app version, build type"),
          "evidence" to strList("Artifact names attached: screenshot, dumps"),
          "hypothesis" to str("Optional suspected root cause, one sentence"),
          "labels" to strList("GitHub labels, e.g. bug, cart, needs-triage"),
        ),
      required = listOf("title", "severity", "area", "stepsToReproduce", "expectedBehavior", "actualBehavior", "environment"),
    )

  val turn: Schema =
    Schema(
      type = Type.OBJECT,
      properties =
        mapOf(
          "status" to str("QUESTION while you still need something from the user; REPORT when the report is ready; DONE after the issue was created; ERROR if you cannot continue.", listOf("QUESTION", "REPORT", "DONE", "ERROR")),
          "question" to str("The single question to ask the user (status QUESTION only)"),
          "message" to str("A short message for the user (status DONE or ERROR)"),
          "report" to report,
          "issue" to Schema(type = Type.OBJECT, properties = mapOf("number" to Schema(type = Type.INTEGER), "url" to Schema(type = Type.STRING))),
        ),
      required = listOf("status"),
    )

  private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

  /** Parses the model's final text, tolerating markdown fences around the JSON. */
  fun parseTurn(text: String): BugReportTurn? {
    val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching { json.decodeFromString<BugReportTurn>(trimmed.substring(start, end + 1)) }.getOrNull()
  }

  fun encodeReport(report: BugReport): String = json.encodeToString(BugReport.serializer(), report)
}
