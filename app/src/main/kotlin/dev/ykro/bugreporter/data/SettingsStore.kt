package dev.ykro.bugreporter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small settings: the pending bug-report session (for resumability) and the private-mode toggle. */
class SettingsStore(private val context: Context) {
  private val pendingSession = stringPreferencesKey("pending_report_session")
  private val pendingScreen = stringPreferencesKey("pending_report_screen")
  private val privateMode = booleanPreferencesKey("private_mode")

  val pendingSessionId: Flow<String?> = context.dataStore.data.map { it[pendingSession] }
  val privateModeEnabled: Flow<Boolean> = context.dataStore.data.map { it[privateMode] ?: false }

  suspend fun pendingSessionIdNow(): String? = pendingSessionId.first()

  suspend fun pendingScreenNow(): String? = context.dataStore.data.map { it[pendingScreen] }.first()

  suspend fun isPrivateMode(): Boolean = privateModeEnabled.first()

  suspend fun setPending(sessionId: String, screen: String) =
    context.dataStore.edit {
      it[pendingSession] = sessionId
      it[pendingScreen] = screen
    }

  suspend fun clearPending() = context.dataStore.edit { it.remove(pendingSession); it.remove(pendingScreen) }

  suspend fun setPrivateMode(enabled: Boolean) = context.dataStore.edit { it[privateMode] = enabled }
}
