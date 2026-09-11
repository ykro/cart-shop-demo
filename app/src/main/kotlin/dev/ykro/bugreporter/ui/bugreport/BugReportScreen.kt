package dev.ykro.bugreporter.ui.bugreport

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.bugreporter.R
import dev.ykro.bugreporter.agent.BugReport
import dev.ykro.bugreporter.agent.IssueRef
import dev.ykro.bugreporter.ui.components.ConfirmationSheet
import dev.ykro.bugreporter.ui.components.ToolCallChip
import dev.ykro.bugreporter.ui.theme.AmberSoft
import dev.ykro.bugreporter.ui.theme.Danger
import dev.ykro.bugreporter.ui.theme.DangerSoft
import dev.ykro.bugreporter.ui.theme.Indigo
import dev.ykro.bugreporter.ui.theme.IndigoSoft
import dev.ykro.bugreporter.ui.theme.SuccessSoft
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportScreen(viewModel: BugReportViewModel, onClose: () -> Unit, onOpenSettings: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  var input by remember { mutableStateOf("") }

  LaunchedEffect(state.items.size, state.streamingText, state.draft, state.issue) {
    val count = listState.layoutInfo.totalItemsCount
    if (count > 0) listState.animateScrollToItem(count - 1)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Report a bug", style = MaterialTheme.typography.titleLarge)
            ModeBadge(state.onDevice, state.modelLabel)
          }
        },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = {
          IconButton(onClick = { scope.launch { viewModel.finish(); onClose() } }) { Icon(Icons.Default.Close, "Discard report") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      if (state.fatal == null && state.issue == null) {
        Row(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp).imePadding(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(if (state.busy) "Agent is working…" else if (state.canAnswer) "Type your answer" else "Waiting for the report…") },
            enabled = state.canAnswer && !state.busy,
            maxLines = 3,
          )
          FilledIconButton(onClick = { viewModel.answer(input); input = "" }, enabled = state.canAnswer && !state.busy && input.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send")
          }
        }
      }
    },
  ) { padding ->
    if (state.fatal != null) {
      Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(state.fatal!!, style = MaterialTheme.typography.bodyLarge, color = Danger)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text("Open settings") }
      }
      return@Scaffold
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (state.screenshot != null || state.resumed) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.screenshot?.let { Image(it, "Screenshot", Modifier.height(96.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit) }
            Column {
              Text("Attached automatically", style = MaterialTheme.typography.labelLarge)
              Text("Screenshot · breadcrumbs · logs · cart state · environment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              if (state.resumed) Text("Resumed from the saved session", style = MaterialTheme.typography.bodySmall, color = Indigo)
            }
          }
        }
      }
      itemsIndexed(state.items) { _, item -> TimelineRow(item) }
      if (state.canRetry && !state.busy && state.issue == null) {
        item { OutlinedButton(onClick = viewModel::continueAfterError) { Text("Ask the agent to continue") } }
      }
      state.streamingText?.let { item { AgentBubble(it.take(400), streaming = true) } }
      if (state.busy && state.streamingText == null) item { Thinking() }
      state.draft?.let { draft ->
        if (state.issue == null) item { ReportCard(draft, viewModel::updateDraft, enabled = !state.busy, onCreate = viewModel::createIssue) }
      }
      state.issue?.let { issue -> item { IssueCard(issue, state.doneMessage, onDone = { scope.launch { viewModel.finish(); onClose() } }) } }
      item { Spacer(Modifier.height(8.dp)) }
    }
  }

  state.confirmation?.let { pending ->
    ConfirmationSheet(
      toolName = pending.toolName,
      args = pending.args,
      hint = pending.hint,
      busy = state.busy,
      onConfirm = { viewModel.resolveConfirmation(true) },
      onCancel = { viewModel.resolveConfirmation(false) },
    )
  }
}

@Composable
private fun ModeBadge(onDevice: Boolean, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Icon(if (onDevice) Icons.Outlined.PhoneAndroid else Icons.Outlined.Cloud, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if (onDevice) "Private mode · $label" else "Cloud · $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun TimelineRow(item: TimelineItem) {
  when (item) {
    is TimelineItem.Tools -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { item.chips.forEach { ToolCallChip(it) } }
    is TimelineItem.Agent -> AgentBubble(item.text, streaming = false)
    is TimelineItem.User ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(item.text, Modifier.background(Indigo, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, style = MaterialTheme.typography.bodyLarge)
      }
    is TimelineItem.System -> Text(item.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
    is TimelineItem.ReportReady -> Text("Report ready: ${item.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    is TimelineItem.Failure -> Text(item.text, Modifier.fillMaxWidth().background(DangerSoft, RoundedCornerShape(12.dp)).padding(12.dp), color = Danger, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun AgentBubble(text: String, streaming: Boolean) {
  Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Image(painterResource(R.drawable.agent_avatar), null, Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)))
    Text(
      text,
      Modifier.weight(1f, fill = false).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
      style = MaterialTheme.typography.bodyLarge,
      color = if (streaming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun Thinking() {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportCard(draft: BugReport, update: (BugReport.() -> BugReport) -> Unit, enabled: Boolean, onCreate: () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Bug report", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      OutlinedTextField(value = draft.title, onValueChange = { v -> update { copy(title = v) } }, label = { Text("Title") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { sev ->
          FilterChip(selected = draft.severity == sev, onClick = { update { copy(severity = sev) } }, label = { Text(sev) }, enabled = enabled)
        }
      }
      OutlinedTextField(
        value = draft.stepsToReproduce.joinToString("\n"),
        onValueChange = { v -> update { copy(stepsToReproduce = v.lines()) } },
        label = { Text("Steps to reproduce (one per line)") },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
      )
      OutlinedTextField(value = draft.expectedBehavior, onValueChange = { v -> update { copy(expectedBehavior = v) } }, label = { Text("Expected") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
      OutlinedTextField(value = draft.actualBehavior, onValueChange = { v -> update { copy(actualBehavior = v) } }, label = { Text("Actual") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
      Text("Environment: ${draft.environment}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (draft.evidence.isNotEmpty()) Text("Evidence: ${draft.evidence.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      draft.hypothesis?.takeIf { it.isNotBlank() }?.let { hyp ->
        Row(Modifier.fillMaxWidth().background(AmberSoft, RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Outlined.Lightbulb, null, Modifier.size(20.dp))
          Column {
            Text("Hypothesis", style = MaterialTheme.typography.labelLarge)
            Text(hyp, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
      if (draft.labels.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          draft.labels.forEach { Text(it, Modifier.background(IndigoSoft, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }
        }
      }
      Button(onClick = onCreate, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Create GitHub issue", fontWeight = FontWeight.Bold) }
      Text("You will be asked to approve before anything is sent.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun IssueCard(issue: IssueRef, message: String?, onDone: () -> Unit) {
  val uriHandler = LocalUriHandler.current
  Card(colors = CardDefaults.cardColors(containerColor = SuccessSoft), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Image(painterResource(R.drawable.bug_reported), null, Modifier.size(160.dp).clip(RoundedCornerShape(16.dp)))
      Text("Issue #${issue.number} created", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      Text(issue.url, style = MaterialTheme.typography.bodySmall, color = Indigo)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { uriHandler.openUri(issue.url) }) { Text("Open on GitHub") }
        Button(onClick = onDone) { Text("Done") }
      }
    }
  }
}
