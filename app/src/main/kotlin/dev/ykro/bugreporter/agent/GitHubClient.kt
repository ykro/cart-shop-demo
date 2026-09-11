package dev.ykro.bugreporter.agent

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@Serializable data class IssueCreated(val number: Int, val url: String)

/**
 * Minimal GitHub REST client. For the demo the token comes from BuildConfig (local.properties);
 * a production app would call its own backend instead of holding a token.
 */
class GitHubClient(private val token: String, private val repo: String) {
  @Serializable private data class CreateIssueBody(val title: String, val body: String, val labels: List<String>)

  val isConfigured: Boolean
    get() = token.isNotBlank() && repo.contains("/")

  val repoName: String
    get() = repo

  suspend fun createIssue(title: String, body: String, labels: List<String>): IssueCreated =
    withContext(Dispatchers.IO) {
      require(isConfigured) { "GitHub is not configured: set GITHUB_TOKEN and GITHUB_REPO in local.properties." }
      val conn = URL("https://api.github.com/repos/$repo/issues").openConnection() as HttpURLConnection
      try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(Json.encodeToString(CreateIssueBody(title, body, labels)).toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) {
          Timber.e("GitHub issue creation failed: HTTP %d %s", code, text.take(300))
          error("GitHub API returned HTTP $code")
        }
        val obj: JsonObject = Json.parseToJsonElement(text).jsonObject
        IssueCreated(number = obj["number"]!!.jsonPrimitive.content.toInt(), url = obj["html_url"]!!.jsonPrimitive.content)
      } finally {
        conn.disconnect()
      }
    }
}
