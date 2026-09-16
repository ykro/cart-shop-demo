package dev.ykro.bugreporter.agent

import android.content.Context
import com.google.adk.firebase.models.Firebase
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.artifacts.FileArtifactService
import com.google.adk.kt.artifacts.fromExternalFilesDir
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.ToolConfirmation
import com.google.adk.kt.litertlm.LiteRtLmModel
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.room.RoomSessionService
import com.google.adk.kt.tools.SkillToolset
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/** What the UI renders while a turn runs. Every ADK [Event] is folded into one of these. */
sealed interface AgentUiEvent {
  data class ToolCall(val id: String, val name: String, val args: Map<String, Any?>, val isSkill: Boolean) : AgentUiEvent
  data class ToolResult(val id: String, val name: String, val isError: Boolean, val summary: String) : AgentUiEvent
  data class PartialText(val text: String) : AgentUiEvent
  data class Turn(val turn: BugReportTurn, val rawText: String) : AgentUiEvent
  data class ConfirmationRequested(val confirmationCallId: String, val toolName: String, val args: Map<String, Any?>, val hint: String?) : AgentUiEvent
  data class Error(val message: String) : AgentUiEvent
  /** Only produced by [AgentRuntime.replay]: something the tester (or the app) sent earlier. */
  data class UserText(val text: String, val isSystem: Boolean) : AgentUiEvent
  data object Done : AgentUiEvent
}

/**
 * Owns the ADK runner and services for one app process. Sessions live in Room, artifacts on disk,
 * so a report in progress survives the tester leaving the app or the process being killed.
 */
class AgentRuntime(
  private val context: Context,
  private val contextTools: ContextTools,
  private val gitHubTools: GitHubTools,
) {
  val sessionService: SessionService = RoomSessionService.fromContext(context)
  val artifactService: ArtifactService = FileArtifactService.fromExternalFilesDir(context)

  private var runner: InMemoryRunner? = null
  private var runnerOnDevice: Boolean? = null
  private var liteRtModel: LiteRtLmModel? = null

  fun sessionKey(sessionId: String) = SessionKey(APP_NAME, USER_ID, sessionId)

  /** Builds (or reuses) the runner for the requested mode. Loading the on-device model takes seconds. */
  @Synchronized
  fun runner(onDevice: Boolean): InMemoryRunner {
    runner?.let { if (runnerOnDevice == onDevice) return it }
    liteRtModel?.let { runCatching { it.close() } }
    liteRtModel = null
    val model: Model =
      if (onDevice) {
        val file = ModelStore.find(context) ?: error("Private mode needs the on-device model. Download it in Settings.")
        createLiteRtModel(file).also { liteRtModel = it }
      } else {
        val app = runCatching { FirebaseApp.getInstance() }.getOrElse { error("Firebase is not configured. Add app/google-services.json (see README).") }
        Firebase.create(CLOUD_MODEL, FirebaseAI.getInstance(app))
      }
    val agent = BugReporterAgent.create(context, model, contextTools, gitHubTools, onDevice)
    return InMemoryRunner(agent = agent, appName = APP_NAME, sessionService = sessionService, artifactService = artifactService)
      .also { runner = it; runnerOnDevice = onDevice }
  }

  private fun createLiteRtModel(file: File): LiteRtLmModel =
    LiteRtLmModel.create(EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU(), cacheDir = context.cacheDir.absolutePath), name = file.name)
      .also { it.engine.initialize() }

  suspend fun createSession(sessionId: String) {
    sessionService.createSession(sessionKey(sessionId))
  }

  suspend fun sessionExists(sessionId: String): Boolean = sessionService.getSession(sessionKey(sessionId)) != null

  suspend fun sessionEvents(sessionId: String): List<Event> = sessionService.getSession(sessionKey(sessionId))?.events.orEmpty()

  suspend fun saveScreenshot(sessionId: String, png: ByteArray): String {
    val name = "screenshot.png"
    artifactService.saveArtifact(sessionKey(sessionId), name, Part(inlineData = Blob(mimeType = "image/png", data = png)))
    return name
  }

  suspend fun loadScreenshot(sessionId: String): ByteArray? =
    artifactService.loadArtifact(sessionKey(sessionId), "screenshot.png")?.inlineData?.data

  suspend fun kickoff(screen: String, onDevice: Boolean): String =
    if (onDevice) kickoffMessageWithContext(screen, contextTools.contextPack()) else kickoffMessage(screen)

  fun sendText(sessionId: String, text: String, onDevice: Boolean): Flow<AgentUiEvent> =
    run(sessionId, Content(role = Role.USER, parts = listOf(Part(text = text))), onDevice)

  /** Replies to a pending `adk_request_confirmation` call; the runner then executes (or rejects) the tool. */
  fun sendConfirmation(sessionId: String, confirmationCallId: String, confirmed: Boolean, onDevice: Boolean): Flow<AgentUiEvent> =
    run(
      sessionId,
      Content(
        role = Role.USER,
        parts =
          listOf(
            Part(
              functionResponse =
                FunctionResponse(
                  name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                  id = confirmationCallId,
                  response = mapOf(ToolConfirmation.CONFIRMED_KEY to confirmed),
                )
            )
          ),
      ),
      onDevice,
    )

  private fun run(sessionId: String, message: Content, onDevice: Boolean): Flow<AgentUiEvent> = flow {
    val partial = StringBuilder()
    var finalText: String? = null
    try {
      val activeRunner = runner(onDevice)
      activeRunner
        .runAsync(
          userId = USER_ID,
          sessionId = sessionId,
          newMessage = message,
          runConfig = RunConfig(streamingMode = if (onDevice) StreamingMode.NONE else StreamingMode.SSE, maxLlmCalls = 24),
        )
        .collect { event ->
          event.errorMessage?.let { emit(AgentUiEvent.Error(it)) }
          if (!event.partial) mapFunctionParts(event).forEach { emit(it) }
          if (event.author == BugReporterAgent.NAME) {
            val text = modelText(event)
            if (event.partial) {
              if (text.isNotEmpty()) { partial.append(text); emit(AgentUiEvent.PartialText(partial.toString())) }
            } else if (text.isNotBlank()) {
              finalText = text
              partial.setLength(0)
            }
          }
        }
      finalText?.let { emit(turnFrom(it)) }
    } catch (e: Exception) {
      Timber.e(e, "Agent turn failed")
      emit(AgentUiEvent.Error(e.message ?: e::class.simpleName.orEmpty()))
    }
    Timber.i("Turn finished")
    emit(AgentUiEvent.Done)
  }

  /** Rebuilds the UI timeline from the events ADK persisted in Room (used after a process death). */
  fun replay(events: List<Event>): List<AgentUiEvent> {
    val out = mutableListOf<AgentUiEvent>()
    for (event in events) {
      if (event.partial) continue
      if (event.author == "user") {
        val text = event.content?.parts.orEmpty().mapNotNull { it.text }.joinToString("").trim()
        if (text.isNotEmpty()) out += AgentUiEvent.UserText(text.removePrefix(SYSTEM_PREFIX).trim(), isSystem = text.startsWith(SYSTEM_PREFIX))
      }
      out += mapFunctionParts(event)
      if (event.author == BugReporterAgent.NAME) {
        val text = modelText(event)
        if (text.isNotBlank()) out += turnFrom(text)
      }
    }
    return out
  }

  private fun modelText(event: Event): String =
    event.content?.parts.orEmpty().filter { it.text != null && it.thought != true }.joinToString("") { it.text.orEmpty() }

  private fun turnFrom(raw: String): AgentUiEvent.Turn {
    val turn = BugReportSchema.parseTurn(raw)
    return if (turn != null) AgentUiEvent.Turn(turn, raw) else AgentUiEvent.Turn(BugReportTurn(status = "QUESTION", question = raw.trim()), raw)
  }

  private fun mapFunctionParts(event: Event): List<AgentUiEvent> {
    val out = mutableListOf<AgentUiEvent>()
    for (part in event.content?.parts.orEmpty()) {
      part.functionCall?.let { call ->
        if (call.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) {
          val original = call.args["originalFunctionCall"] as? Map<*, *>
          val confirmation = call.args["toolConfirmation"] as? Map<*, *>
          out +=
            AgentUiEvent.ConfirmationRequested(
              confirmationCallId = call.id.orEmpty(),
              toolName = original?.get("name") as? String ?: "tool",
              args = (original?.get("args") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty(),
              hint = confirmation?.get("hint") as? String,
            )
        } else {
          out += AgentUiEvent.ToolCall(call.id ?: call.name, call.name, call.args, call.name in SKILL_TOOLS)
        }
      }
      part.functionResponse?.let { resp ->
        if (resp.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) {
          out += AgentUiEvent.ToolResult(resp.id ?: resp.name, resp.name, isError = false, summary = "answered")
        } else {
          val response = resp.response
          out += AgentUiEvent.ToolResult(resp.id ?: resp.name, resp.name, response.containsKey("error"), summarize(resp.name, unwrap(response)))
        }
      }
    }
    return out
  }

  /** KSP-generated tools return `{"result": ...}`; skill tools and errors return their map directly. */
  private fun unwrap(response: Map<String, Any?>): Map<String, Any?> =
    (response["result"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: response

  private fun summarize(name: String, response: Map<String, Any?>): String =
    when (name) {
      "get_breadcrumbs" -> "${(response["result"] as? List<*>)?.size ?: 0} actions"
      "get_app_logs" -> "${(response["result"] as? List<*>)?.size ?: 0} lines"
      SkillToolset.TOOL_NAME_LOAD_SKILL -> "skill loaded"
      SkillToolset.TOOL_NAME_LOAD_SKILL_RESOURCE -> "resource loaded"
      SkillToolset.TOOL_NAME_LIST_SKILLS -> "catalog"
      "create_github_issue" -> (response["url"] ?: response["error"] ?: "").toString()
      else -> "ok"
    }

  companion object {
    const val APP_NAME = "CartShopBugReporter"
    const val USER_ID = "tester"
    const val CLOUD_MODEL = "gemini-3.8-flash"
    const val ON_DEVICE_MODEL_LABEL = "Gemma 4 E2B (on device)"

    /** Messages the app sends on the tester's behalf start with this so the UI can style them. */
    const val SYSTEM_PREFIX = "[app]"
    fun kickoffMessage(screen: String) =
      "$SYSTEM_PREFIX The tester triggered a bug report from the \"$screen\" screen. Collect the context with your tools, then ask what you still need."
    /** Private-mode kickoff: the context is inlined so the on-device model needs fewer tool calls. */
    fun kickoffMessageWithContext(screen: String, contextPack: String) =
      "$SYSTEM_PREFIX The tester triggered a bug report from the \"$screen\" screen. CONTEXT PACK (already collected and redacted, do not call the collection tools):\n$contextPack\nLoad the skill, then ask what you still need."
    fun createIssueMessage(reportJson: String) =
      "$SYSTEM_PREFIX The tester approved this final report (they may have edited it). Create the GitHub issue from it now:\n$reportJson"
    val SKILL_TOOLS = setOf(SkillToolset.TOOL_NAME_LIST_SKILLS, SkillToolset.TOOL_NAME_LOAD_SKILL, SkillToolset.TOOL_NAME_LOAD_SKILL_RESOURCE)
  }
}
