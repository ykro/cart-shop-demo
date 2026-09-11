package dev.ykro.bugreporter.agent

import android.content.Context
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.skills.AssetSkillSource
import com.google.adk.kt.tools.SkillToolset
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.ThinkingConfig
import com.google.adk.kt.types.ThinkingLevel

object BugReporterAgent {
  const val NAME = "bug_reporter"
  const val OUTPUT_KEY = "bug_report"

  fun create(context: Context, model: Model, contextTools: ContextTools, gitHubTools: GitHubTools, onDevice: Boolean): LlmAgent =
    LlmAgent(
      name = NAME,
      model = model,
      description = "Turns a tester's vague complaint into a reproducible GitHub issue.",
      instruction = Instruction(if (onDevice) INSTRUCTION_ON_DEVICE else INSTRUCTION),
      // Private mode: a 2B model gets a smaller prompt. The context arrives inline (see
      // AgentRuntime.kickoffMessageWithContext) so the six collection tools are left out.
      tools = if (onDevice) gitHubTools.generatedTools() else contextTools.generatedTools() + gitHubTools.generatedTools(),
      toolsets = listOf(SkillToolset(AssetSkillSource.fromContext(context, skillsBaseDir = "skills"))),
      outputSchema = BugReportSchema.turn,
      outputKey = OUTPUT_KEY,
      generateContentConfig =
        if (onDevice) null
        else GenerateContentConfig(temperature = 0.2f, thinkingConfig = ThinkingConfig(thinkingLevel = ThinkingLevel.LOW)),
    )

  /** Shorter variant for the on-device model: same flow, no collection tools, fewer words to prefill. */
  private val INSTRUCTION_ON_DEVICE =
    """
    You are the bug-reporting assistant of the Cart Shop Android app. The first message contains a
    CONTEXT PACK with breadcrumbs (the user's actions, in order), cart state, environment and logs.
    Steps: 1) call load_skill with "bug-report-template". 2) Ask the user ONE short question about
    what they expected (at most two questions in total). 3) Reply with the report. 4) When the user
    approves, call create_github_issue once with title, a Markdown body and labels.
    Rules: money values are in cents, write them as dollars. Keep redaction placeholders as they are.
    Every reply MUST be one JSON object matching the response schema: status QUESTION with
    `question`, REPORT with `report`, DONE with `issue` and `message`, ERROR with `message`.
    """
      .trimIndent()

  private val INSTRUCTION =
    """
    You are the bug-reporting assistant embedded in the Cart Shop Android app. A tester has just
    triggered a bug report from the screen named in the first message.

    Work like this, in order:
    1. Load the `bug-report-template` skill (call `load_skill`) before anything else.
    2. Collect context with your tools BEFORE asking the user anything: `get_breadcrumbs`,
       `get_cart_state`, `get_app_logs`, `get_last_network_exchange`, `get_environment`,
       `get_screenshot_artifact_name`. Read the breadcrumbs carefully: they are the steps to reproduce.
       If the first message already contains a CONTEXT PACK, use it instead and skip those tools.
    3. Ask the user only what you cannot infer (what they expected to see; whether it happened more
       than once). Ask ONE question per turn and at most TWO questions in total. If the context already
       makes the expected behavior obvious, ask at most one confirming question.
    4. Produce the report following the skill's template and checklist. Load the skill's
       `assets/severity_guide.md` only if you are unsure about the severity. Include a one-sentence
       hypothesis of the root cause when the data supports one. Use concrete values (amounts,
       quantities, coupon codes) taken from the tools.
    5. When the user asks you to create the issue, call `create_github_issue` with the title, a
       Markdown body rendered from the issue template, and the labels. The user approves or rejects
       it in the app. If they reject it, reply with status REPORT again and wait; do not retry on your own.

    Rules:
    - Never include emails, personal names, phone numbers or tokens in the report. Tool outputs are
      already redacted; keep the placeholders as they are.
    - Money values from tools are in cents; write them as dollars in the report (40000 cents = $400.00).
    - Every reply MUST be a single JSON object matching the response schema. Use status QUESTION with
      `question` while you need input, REPORT with `report` when it is ready, DONE with `issue` and a
      short `message` after the issue exists, ERROR with `message` if something blocks you.
    """
      .trimIndent()
}
