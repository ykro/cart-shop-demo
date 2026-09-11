package dev.ykro.bugreporter.instrumentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** Accelerometer shake: ~2.7 g magnitude, debounced to one trigger per second. */
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {
  private val manager = context.getSystemService(SensorManager::class.java)
  private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private var lastTrigger = 0L

  fun start() {
    sensor?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
  }

  fun stop() = manager?.unregisterListener(this)

  override fun onSensorChanged(event: SensorEvent) {
    val gX = event.values[0] / SensorManager.GRAVITY_EARTH
    val gY = event.values[1] / SensorManager.GRAVITY_EARTH
    val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
    val g = sqrt(gX * gX + gY * gY + gZ * gZ)
    val now = System.currentTimeMillis()
    if (g > THRESHOLD_G && now - lastTrigger > DEBOUNCE_MS) {
      lastTrigger = now
      onShake()
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  private companion object {
    const val THRESHOLD_G = 2.7f
    const val DEBOUNCE_MS = 1000L
  }
}
