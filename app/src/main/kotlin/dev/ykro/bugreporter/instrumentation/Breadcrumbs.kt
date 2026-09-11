package dev.ykro.bugreporter.instrumentation

import java.util.ArrayDeque
import kotlinx.serialization.Serializable

@Serializable
data class Breadcrumb(val tMs: Long, val action: String)

/**
 * Ring buffer of the last UI and navigation actions, with timestamps relative to app start.
 * This is what lets the agent reconstruct "steps to reproduce" that a user would never type.
 */
class Breadcrumbs(private val capacity: Int = 50, private val clock: () -> Long = System::currentTimeMillis) {
  private val startedAt = clock()
  private val buffer = ArrayDeque<Breadcrumb>(capacity)

  @Synchronized
  fun record(action: String) {
    if (buffer.size == capacity) buffer.removeFirst()
    buffer.addLast(Breadcrumb(clock() - startedAt, action))
  }

  @Synchronized
  fun last(limit: Int = capacity): List<Breadcrumb> = buffer.toList().takeLast(limit.coerceIn(1, capacity))

  @Synchronized fun clear() = buffer.clear()
}
