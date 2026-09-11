package dev.ykro.bugreporter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.bugreporter.agent.AgentRuntime
import dev.ykro.bugreporter.agent.ModelStore
import dev.ykro.bugreporter.ui.theme.Danger
import dev.ykro.bugreporter.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      SectionCard("Bug reporter model") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Private mode (on-device)", style = MaterialTheme.typography.titleMedium)
            Text(
              if (state.privateMode) "Reports are drafted by ${AgentRuntime.ON_DEVICE_MODEL_LABEL}. Nothing leaves the phone until you approve the GitHub issue."
              else "Reports are drafted by ${AgentRuntime.CLOUD_MODEL} through Firebase AI Logic.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = state.privateMode, onCheckedChange = viewModel::setPrivateMode)
        }
      }
      SectionCard("On-device model") {
        if (state.modelPresent) {
          Text("Ready: ${state.modelFileName}", style = MaterialTheme.typography.bodyMedium, color = Success)
          Spacer(Modifier.height(8.dp))
          OutlinedButton(onClick = viewModel::deleteModel) { Text("Delete model") }
        } else if (state.downloading) {
          Text("Downloading ${ModelStore.FILE_NAME} (${ModelStore.SIZE_LABEL})…", style = MaterialTheme.typography.bodyMedium)
          LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
          Text("${(state.progress * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
          OutlinedButton(onClick = viewModel::cancelDownload) { Text("Cancel") }
        } else {
          Text("${ModelStore.FILE_NAME} (${ModelStore.SIZE_LABEL}) is not on this device.", style = MaterialTheme.typography.bodyMedium)
          state.error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::download) { Text("Download over Wi-Fi") }
            OutlinedButton(onClick = viewModel::refreshModel) { Text("Re-check") }
          }
          Spacer(Modifier.height(8.dp))
          Text("Or push it with adb:", style = MaterialTheme.typography.labelMedium)
          Text(state.pushCommand, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
      }
      SectionCard("GitHub") {
        if (state.gitHubConfigured) Text("Issues go to ${state.gitHubRepo}", style = MaterialTheme.typography.bodyMedium, color = Success)
        else Text("Not configured. Set GITHUB_TOKEN and GITHUB_REPO in local.properties and rebuild; the agent will still draft reports.", style = MaterialTheme.typography.bodyMedium, color = Danger)
      }
    }
  }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(16.dp)) {
      Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(8.dp))
      content()
    }
  }
}
