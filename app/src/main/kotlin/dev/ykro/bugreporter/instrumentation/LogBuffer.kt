package dev.ykro.bugreporter.instrumentation

import android.util.Log
import java.util.ArrayDeque
import timber.log.Timber

/** Keeps the app's own last [capacity] log lines. The system logcat is never read. */
class LogBuffer(private val capacity: Int = 200) {
  private val buffer = ArrayDeque<String>(capacity)

  @Synchronized
  fun append(line: String) {
    if (buffer.size == capacity) buffer.removeFirst()
    buffer.addLast(line)
  }

  @Synchronized
  fun last(limit: Int = capacity): List<String> = buffer.toList().takeLast(limit.coerceIn(1, capacity))

  /** A Timber tree that mirrors every log line into this buffer (and still prints to logcat). */
  inner class Tree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
      super.log(priority, tag, message, t)
      val level =
        when (priority) {
          Log.VERBOSE -> "V"
          Log.DEBUG -> "D"
          Log.INFO -> "I"
          Log.WARN -> "W"
          Log.ERROR -> "E"
          else -> "?"
        }
      append("${System.currentTimeMillis() % 100_000_000} $level/${tag ?: "App"}: $message")
    }
  }
}
