package dev.ykro.bugreporter.agent

import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.google.adk.kt.tools.FunctionTool

/** The one tool with side effects outside the phone. It never runs without the user's approval. */
class GitHubTools(private val client: GitHubClient) {
  @Tool(
    name = "create_github_issue",
    description = "Creates an issue in the project's GitHub repository. Requires the user's approval.",
    requireConfirmation = true,
  )
  suspend fun createIssue(
    @Param("Short title describing the symptom") title: String,
    @Param("Markdown body following the skill's issue template") body: String,
    @Param("Labels, e.g. bug, cart, needs-triage") labels: List<String>,
  ): Map<String, Any?> =
    try {
      val created = client.createIssue(title, body, labels)
      mapOf("number" to created.number, "url" to created.url)
    } catch (e: Exception) {
      mapOf(FunctionTool.ERROR_KEY to (e.message ?: "Could not create the issue."))
    }
}
