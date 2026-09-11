package dev.ykro.bugreporter.instrumentation

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Captures the activity's own window with PixelCopy (no permission, no other apps' content). */
object ScreenshotCapture {
  suspend fun capturePng(activity: Activity): ByteArray? {
    val view = activity.window.decorView
    if (view.width == 0 || view.height == 0) return null
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    val ok =
      suspendCancellableCoroutine<Boolean> { cont ->
        val location = IntArray(2).also { view.getLocationInWindow(it) }
        val rect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        PixelCopy.request(activity.window, rect, bitmap, { result -> cont.resume(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
      }
    if (!ok) return null
    val scaled = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
    return ByteArrayOutputStream().use { out ->
      scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
      out.toByteArray()
    }
  }
}
