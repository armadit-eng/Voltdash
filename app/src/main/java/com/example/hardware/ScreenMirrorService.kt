package com.example.hardware

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class MirrorCaptureConfig(
  val snapWidth: Int = 350,
  val snapHeight: Int = 220,
  val maxChars: Int = 7000,
  val leftPct: Int = 0,
  val rightPct: Int = 100,
  val topPct: Int = 10,
  val bottomPct: Int = 78,
  val stripEnabled: Boolean = true,
  val stripPos: String = "top",
  val stripText: String = "",
)

object MirrorFrameBus {
  private val _isRunning = MutableStateFlow(false)
  val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

  private val _frames = MutableSharedFlow<Pair<Bitmap, String>>(extraBufferCapacity = 2)
  val frames = _frames.asSharedFlow()

  @Volatile var config = MirrorCaptureConfig()

  fun setRunning(running: Boolean) {
    _isRunning.value = running
  }

  fun emitFrame(bmp: Bitmap, b64: String) {
    _frames.tryEmit(bmp to b64)
  }
}

class ScreenMirrorService : Service() {
  companion object {
    const val ACTION_START = "com.example.action.START_MIRROR"
    const val ACTION_STOP = "com.example.action.STOP_MIRROR"
    const val EXTRA_RESULT_CODE = "result_code"
    const val EXTRA_DATA = "result_data"
    private const val CHANNEL_ID = "gesits_mirror_channel"
    private const val NOTIF_ID = 7201
  }

  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var workerThread: HandlerThread? = null
  private var workerHandler: Handler? = null
  private var lastCaptureMs = 0L

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopProjection()
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_START -> {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data: Intent? =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
          } else {
            intent.getParcelableExtra(EXTRA_DATA)
          }
        if (resultCode == Activity.RESULT_OK && data != null) {
          startProjectionForeground(resultCode, data)
        } else {
          stopSelf()
        }
      }
    }
    return START_NOT_STICKY
  }

  private fun startProjectionForeground(resultCode: Int, data: Intent) {
    createNotificationChannel()
    val notif: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("GESITS Dash — Mirror Layar Aktif")
        .setContentText("Mengirim tampilan layar ke MID motor melalui AT+MAPS")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setOngoing(true)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    } else {
      startForeground(NOTIF_ID, notif)
    }

    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val projection = mpm.getMediaProjection(resultCode, data) ?: run {
      stopSelf()
      return
    }
    mediaProjection = projection

    val metrics = resources.displayMetrics
    val capW = (metrics.widthPixels / 2).coerceAtLeast(360)
    val capH = (metrics.heightPixels / 2).coerceAtLeast(480)
    val density = metrics.densityDpi

    val ht = HandlerThread("MirrorCaptureThread").apply { start() }
    workerThread = ht
    val handler = Handler(ht.looper)
    workerHandler = handler

    projection.registerCallback(
      object : MediaProjection.Callback() {
        override fun onStop() {
          stopProjection()
          stopSelf()
        }
      },
      handler,
    )

    val reader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
    imageReader = reader
    reader.setOnImageAvailableListener(
      { r ->
        val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
        try {
          val now = System.currentTimeMillis()
          if (now - lastCaptureMs < 950L) {
            return@setOnImageAvailableListener
          }
          lastCaptureMs = now

          val plane = img.planes[0]
          val buffer = plane.buffer
          val pixelStride = plane.pixelStride
          val rowStride = plane.rowStride
          val rowPadding = rowStride - pixelStride * capW
          val fullBmp =
            Bitmap.createBitmap(capW + rowPadding / pixelStride, capH, Bitmap.Config.ARGB_8888)
          fullBmp.copyPixelsFromBuffer(buffer)

          val cfg = MirrorFrameBus.config
          val l = (cfg.leftPct.coerceIn(0, 89) / 100f * capW).toInt()
          val rPx = (cfg.rightPct.coerceIn(cfg.leftPct + 5, 100) / 100f * capW).toInt().coerceAtMost(capW)
          val t = (cfg.topPct.coerceIn(0, 89) / 100f * capH).toInt()
          val b = (cfg.bottomPct.coerceIn(cfg.topPct + 5, 100) / 100f * capH).toInt().coerceAtMost(capH)

          val cropW = (rPx - l).coerceAtLeast(32)
          val cropH = (b - t).coerceAtLeast(32)
          val cropped = Bitmap.createBitmap(fullBmp, l, t, cropW, cropH)
          fullBmp.recycle()

          val outBmp = Bitmap.createScaledBitmap(cropped, cfg.snapWidth, cfg.snapHeight, true)
          if (cropped != outBmp) cropped.recycle()

          if (cfg.stripEnabled && cfg.stripText.isNotBlank()) {
            val canvas = Canvas(outBmp)
            val barH = 26f
            val yTop = if (cfg.stripPos == "bottom") cfg.snapHeight - barH - 4f else 4f
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 8, 20, 30) }
            canvas.drawRoundRect(
              RectF(4f, yTop, cfg.snapWidth - 4f, yTop + barH),
              6f,
              6f,
              bg,
            )
            val txt =
              Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E676")
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
              }
            canvas.drawText(cfg.stripText.take(34), 10f, yTop + 18f, txt)
          }

          val b64 = MidMapBitmapRenderer.encodeBitmapToBase64Jpeg(outBmp, cfg.maxChars)
          MirrorFrameBus.emitFrame(outBmp, b64)
        } catch (_: Exception) {
        } finally {
          img.close()
        }
      },
      handler,
    )

    virtualDisplay =
      projection.createVirtualDisplay(
        "GESITSMirror",
        capW,
        capH,
        density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        handler,
      )
    MirrorFrameBus.setRunning(true)
  }

  private fun stopProjection() {
    MirrorFrameBus.setRunning(false)
    try {
      virtualDisplay?.release()
    } catch (_: Exception) {}
    virtualDisplay = null
    try {
      imageReader?.close()
    } catch (_: Exception) {}
    imageReader = null
    try {
      mediaProjection?.stop()
    } catch (_: Exception) {}
    mediaProjection = null
    workerThread?.quitSafely()
    workerThread = null
    workerHandler = null
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
        mgr.createNotificationChannel(
          NotificationChannel(CHANNEL_ID, "Screen Mirroring MID", NotificationManager.IMPORTANCE_LOW)
        )
      }
    }
  }

  override fun onDestroy() {
    stopProjection()
    super.onDestroy()
  }
}
