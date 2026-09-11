package dev.ykro.bugreporter.agent

/**
 * Deterministic PII redaction applied inside every tool, before anything reaches the model.
 * Privacy does not depend on the LLM following an instruction.
 */
class Redactor(private val knownNames: List<String> = emptyList(), private val knownEmails: List<String> = emptyList()) {
  private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
  private val phone = Regex("""(?<!\d)(\+?\d[\d\s().-]{7,}\d)(?!\d)""")
  private val githubToken = Regex("""\b(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b""")
  private val bearer = Regex("""(?i)bearer\s+[A-Za-z0-9\-._~+/]+=*""")
  private val nameRegexes = knownNames.filter { it.isNotBlank() }.map { Regex("(?i)" + Regex.escape(it)) }

  fun redactOrNull(text: String?): String? = text?.let { redact(it) }

  fun redact(text: String): String {
    var out = text
    knownEmails.forEach { out = out.replace(it, EMAIL, ignoreCase = true) }
    out = email.replace(out, EMAIL)
    out = githubToken.replace(out, TOKEN)
    out = bearer.replace(out, "Bearer $TOKEN")
    out = phone.replace(out, PHONE)
    nameRegexes.forEach { out = it.replace(out, NAME) }
    return out
  }

  fun redactAll(lines: List<String>): List<String> = lines.map(::redact)

  companion object {
    const val EMAIL = "[email redacted]"
    const val PHONE = "[phone redacted]"
    const val TOKEN = "[token redacted]"
    const val NAME = "[name redacted]"
  }
}
