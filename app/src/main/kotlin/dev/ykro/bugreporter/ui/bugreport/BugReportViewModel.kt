package dev.ykro.bugreporter.ui.bugreport

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import com.google.adk.kt.types.FunctionCall
import androidx.lifecycle.viewModelScope
import dev.ykro.bugreporter.agent.AgentRuntime
import dev.ykro.bugreporter.agent.AgentUiEvent
import dev.ykro.bugreporter.agent.BugReport
import dev.ykro.bugreporter.agent.BugReportSchema
import dev.ykro.bugreporter.agent.IssueRef
import dev.ykro.bugreporter.agent.ModelStore
import dev.ykro.bugreporter.data.SettingsStore
import dev.ykro.bugreporter.ui.components.ToolChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** One row of the conversation as the tester sees it. */
sealed interface TimelineItem {
  data class Tools(val chips: List<ToolChip>) : TimelineItem
  data class Agent(val text: String) : TimelineItem
  data class User(val text: String) : TimelineItem
  data class System(val text: String) : TimelineItem
  data class ReportReady(val title: String) : TimelineItem
  data class Failure(val text: String) : TimelineItem
}

data class PendingConfirmation(val callId: String, val toolName: String, val args: Map<String, Any?>, val hint: String?)

data class BugReportUiState(
  val items: List<TimelineItem> = emptyList(),
  val streamingText: String? = null,
  val busy: Boolean = true,
  val canAnswer: Boolean = false,
  val draft: BugReport? = null,
  val confirmation: PendingConfirmation? = null,
  val issue: IssueRef? = null,
  val doneMessage: String? = null,
  val onDevice: Boolean = false,
  val modelLabel: String = "",
  val screenshot: ImageBitmap? = null,
  val fatal: String? = null,
  val resumed: Boolean = false,
  val canRetry: Boolean = false,
)

/**
 * Drives one bug-report session. All conversation state lives in ADK's Room session; this class
 * only projects [AgentUiEvent]s into a timeline and holds the editable report draft.
 */
class BugReportViewModel(
  private val sessionId: String,
  private val screen: String,
  private val runtime: AgentRuntime,
  private val settings: SettingsStore,
  private val modelAvailable: () -> Boolean,
) : ViewModel() {
  private val _state = MutableStateFlow(BugReportUiState())
  val state: StateFlow<BugReportUiState> = _state
  private var onDevice = false

  init {
    viewModelScope.launch { start() }
  }

  private suspend fun start() {
    onDevice = settings.isPrivateMode()
    _state.update { it.copy(onDevice = onDevice, modelLabel = if (onDevice) AgentRuntime.ON_DEVICE_MODEL_LABEL else AgentRuntime.CLOUD_MODEL) }
    if (onDevice && !modelAvailable()) {
      _state.update { it.copy(busy = false, fatal = "Private mode is on but the on-device model is missing. Download it in Settings or turn private mode off.") }
      return
    }
    loadScreenshot()
    val events = runtime.sessionEvents(sessionId)
    if (events.isEmpty()) {
      send(runtime.kickoff(screen, onDevice), showAsUser = false)
    } else {
      runtime.replay(events).forEach { apply(it) }
      _state.update { it.copy(busy = false, resumed = true, streamingText = null) }
      if (_state.value.confirmation == null && _state.value.issue == null && _state.value.draft == null && !_state.value.canAnswer) {
        // Killed mid-turn before the model answered: nudge the agent to continue.
        send("${AgentRuntime.SYSTEM_PREFIX} The app was reopened. Continue from where you were.", showAsUser = false)
      }
    }
  }

  private suspend fun loadScreenshot() {
    val png = runCatching { runtime.loadScreenshot(sessionId) }.getOrNull() ?: return
    val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(png, 0, png.size) } ?: return
    _state.update { it.copy(screenshot = bmp.asImageBitmap()) }
  }

  fun answer(text: String) {
    if (text.isBlank() || _state.value.busy) return
    viewModelScope.launch { send(text.trim(), showAsUser = true) }
  }

  /** After a failed turn (e.g. a small model invented a tool name) nudge the agent to go on. */
  fun continueAfterError() {
    if (_state.value.busy) return
    viewModelScope.launch {
      send("${AgentRuntime.SYSTEM_PREFIX} The previous step failed: that tool does not exist. Use only the tools you were given, or continue without it.", showAsUser = false, systemLabel = "Asked the agent to continue")
    }
  }

  fun createIssue() {
    val draft = _state.value.draft ?: return
    viewModelScope.launch { send(AgentRuntime.createIssueMessage(BugReportSchema.encodeReport(draft)), showAsUser = false, systemLabel = "Asked the agent to file the issue") }
  }

  fun resolveConfirmation(confirmed: Boolean) {
    val pending = _state.value.confirmation ?: return
    _state.update { it.copy(confirmation = null, busy = true, items = it.items + TimelineItem.System(if (confirmed) "You approved create_github_issue" else "You rejected create_github_issue")) }
    viewModelScope.launch {
      runtime.sendConfirmation(sessionId, pending.callId, confirmed, onDevice).flowOn(Dispatchers.IO).collect { apply(it) }
    }
  }

  fun updateDraft(transform: BugReport.() -> BugReport) = _state.update { s -> s.copy(draft = s.draft?.transform()) }

  suspend fun finish() = settings.clearPending()

  private suspend fun send(text: String, showAsUser: Boolean, systemLabel: String? = null) {
    _state.update {
      it.copy(
        busy = true,
        canAnswer = false,
        canRetry = false,
        items = it.items + (if (showAsUser) TimelineItem.User(text) else TimelineItem.System(systemLabel ?: describeSystem(text))),
      )
    }
    runtime.sendText(sessionId, text, onDevice).flowOn(Dispatchers.IO).collect { apply(it) }
  }

  private fun describeSystem(text: String): String =
    when {
      text.contains("triggered a bug report") -> "Report started from the $screen screen"
      text.contains("reopened") -> "App reopened, resuming the conversation"
      text.contains("approved this final report") -> "Asked the agent to file the issue"
      else -> text.removePrefix(AgentRuntime.SYSTEM_PREFIX).trim()
    }

  private fun apply(event: AgentUiEvent) {
    when (event) {
      is AgentUiEvent.ToolCall -> addChip(ToolChip(event.id, event.name, event.isSkill))
      is AgentUiEvent.ToolResult -> {
        completeChip(event)
        if (event.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) _state.update { it.copy(confirmation = null) }
      }
      is AgentUiEvent.PartialText -> _state.update { it.copy(streamingText = event.text) }
      is AgentUiEvent.Turn -> applyTurn(event)
      is AgentUiEvent.ConfirmationRequested ->
        _state.update { it.copy(confirmation = PendingConfirmation(event.confirmationCallId, event.toolName, event.args, event.hint), busy = false, streamingText = null) }
      is AgentUiEvent.Error -> _state.update { it.copy(items = it.items + TimelineItem.Failure(event.message), streamingText = null, canRetry = true) }
      is AgentUiEvent.UserText ->
        _state.update { it.copy(items = it.items + if (event.isSystem) TimelineItem.System(describeSystem(event.text)) else TimelineItem.User(event.text)) }
      AgentUiEvent.Done -> _state.update { it.copy(busy = false, streamingText = null) }
    }
  }

  private fun applyTurn(event: AgentUiEvent.Turn) {
    val turn = event.turn
    Timber.d("Agent turn: %s", turn.status)
    _state.update { s ->
      when (turn.status) {
        "REPORT" -> {
          val report = turn.report
          if (report == null) s.copy(items = s.items + TimelineItem.Agent(turn.message ?: turn.question ?: event.rawText))
          else s.copy(draft = report, canAnswer = false, items = s.items + TimelineItem.ReportReady(report.title))
        }
        "DONE" -> s.copy(issue = turn.issue ?: s.issue, doneMessage = turn.message ?: "Issue created.", canAnswer = false, confirmation = null)
        "ERROR" -> s.copy(items = s.items + TimelineItem.Failure(turn.message ?: "The agent could not continue."), canAnswer = true)
        else -> s.copy(items = s.items + TimelineItem.Agent(turn.question ?: turn.message ?: event.rawText), canAnswer = true)
      }
    }
    if (turn.status == "DONE") viewModelScope.launch { settings.clearPending() }
  }

  private fun addChip(chip: ToolChip) =
    _state.update { s ->
      val last = s.items.lastOrNull()
      val items = if (last is TimelineItem.Tools) s.items.dropLast(1) + last.copy(chips = last.chips + chip) else s.items + TimelineItem.Tools(listOf(chip))
      s.copy(items = items)
    }

  private fun completeChip(result: AgentUiEvent.ToolResult) =
    _state.update { s ->
      s.copy(
        items =
          s.items.map { item ->
            if (item !is TimelineItem.Tools) item
            else item.copy(chips = item.chips.map { c -> if (c.id == result.id || (c.name == result.name && !c.done)) c.copy(done = true, isError = result.isError, summary = result.summary) else c })
          }
      )
    }

  companion object {
    fun modelAvailable(context: android.content.Context): () -> Boolean = { ModelStore.find(context) != null }
  }
}
