package com.example.hardware

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import com.example.data.local.DashboardSettings
import com.example.data.remote.OsmTileLoader
import com.example.model.EffectiveTelemetry
import com.example.model.GeoPoint
import com.example.model.GpsState
import com.example.model.HillGrade
import com.example.viewmodel.NavUiState
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.tan

object MidMapBitmapRenderer {
  fun encodeBitmapToBase64Jpeg(bmp: Bitmap, maxChars: Int): String {
    val qualities = intArrayOf(75, 62, 50, 40, 30, 22, 15, 10)
    val baos = ByteArrayOutputStream()
    var lastB64 = ""
    for (q in qualities) {
      baos.reset()
      bmp.compress(Bitmap.CompressFormat.JPEG, q, baos)
      val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
      lastB64 = b64
      if (b64.length <= maxChars) return b64
    }
    return lastB64
  }

  suspend fun renderFrame(
    context: Context,
    tileLoader: OsmTileLoader,
    settings: DashboardSettings,
    gps: GpsState,
    nav: NavUiState,
    eff: EffectiveTelemetry,
  ): Bitmap {
    val w = settings.mapsSnapWidth.coerceIn(160, 480)
    val h = settings.mapsSnapHeight.coerceIn(120, 360)

    if (settings.customWallpaperEnabled && settings.customWallpaperUri.isNotBlank()) {
      decodeScaledWallpaper(context, settings.customWallpaperUri, w, h)?.let { return it }
    }

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(Color.parseColor("#0A1520"))

    val isSplit = settings.mapsLayout == "split"
    val mapW = if (isSplit) (w * 0.54f).roundToInt() else w
    val zoom = if (nav.isNavigating) 16 else 15
    val center = gps.location ?: nav.destination?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(-6.1754, 106.8272)

    val riderScreenY =
      if (nav.isNavigating && settings.mapsRiderPos == "low") h * 0.70f else h * 0.50f
    val riderScreenX = mapW * 0.50f

    fun worldPx(lat: Double, lng: Double): Pair<Double, Double> {
      val scale = 256.0 * (1 shl zoom)
      val x = (lng + 180.0) / 360.0 * scale
      val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
      val y = (1.0 - asinh(tan(latRad)) / PI) / 2.0 * scale
      return x to y
    }

    val (cwX, cwY) = worldPx(center.lat, center.lng)
    val minTx = floor((cwX - riderScreenX) / 256.0).toInt()
    val maxTx = floor((cwX + (mapW - riderScreenX)) / 256.0).toInt()
    val minTy = floor((cwY - riderScreenY) / 256.0).toInt()
    val maxTy = floor((cwY + (h - riderScreenY)) / 256.0).toInt()
    val maxIdx = (1 shl zoom) - 1

    canvas.save()
    canvas.clipRect(0, 0, mapW, h)

    val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    for (tx in minTx..maxTx) {
      for (ty in minTy..maxTy) {
        if (tx !in 0..maxIdx || ty !in 0..maxIdx) continue
        val tileBmp = tileLoader.loadTileBitmap(zoom, tx, ty) ?: continue
        val sx = (riderScreenX + tx * 256.0 - cwX).roundToInt()
        val sy = (riderScreenY + ty * 256.0 - cwY).roundToInt()
        canvas.drawBitmap(tileBmp, null, Rect(sx, sy, sx + 257, sy + 257), tilePaint)
      }
    }

    fun toScreen(pt: GeoPoint): Pair<Float, Float> {
      val (px, py) = worldPx(pt.lat, pt.lng)
      return (riderScreenX + (px - cwX)).toFloat() to (riderScreenY + (py - cwY)).toFloat()
    }

    // Route polyline
    nav.activeRoute?.let { route ->
      if (route.geometry.size >= 2) {
        val routePath = Path()
        route.geometry.forEachIndexed { idx, gp ->
          val (sx, sy) = toScreen(gp)
          if (idx == 0) routePath.moveTo(sx, sy) else routePath.lineTo(sx, sy)
        }
        val outlineP =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 9f
            color = Color.parseColor("#041418")
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
          }
        val lineP =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            color = Color.parseColor("#00E676")
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
          }
        canvas.drawPath(routePath, outlineP)
        canvas.drawPath(routePath, lineP)
      }
    }

    // Destination marker
    nav.destination?.let { d ->
      val (dx, dy) = toScreen(GeoPoint(d.lat, d.lng))
      val pinP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5252") }
      canvas.drawCircle(dx, dy, 8f, pinP)
      pinP.color = Color.WHITE
      canvas.drawCircle(dx, dy, 3.5f, pinP)
    }

    // Rider directional arrow
    gps.location?.let { loc ->
      val (rx, ry) = toScreen(loc)
      canvas.save()
      canvas.rotate(gps.headingDeg ?: 0f, rx, ry)
      val arrow =
        Path().apply {
          moveTo(rx, ry - 11f)
          lineTo(rx + 8f, ry + 8f)
          lineTo(rx, ry + 4f)
          lineTo(rx - 8f, ry + 8f)
          close()
        }
      val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E676") }
      val strokeP =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 2f
          color = Color.WHITE
        }
      canvas.drawPath(arrow, fillP)
      canvas.drawPath(arrow, strokeP)
      canvas.restore()
    }
    canvas.restore()

    if (isSplit) {
      drawSplitPanel(canvas, mapW, w, h, nav, gps, eff)
    } else if (nav.isNavigating && nav.activeRoute != null) {
      drawCompactTopTurnBanner(canvas, w, nav)
    }

    return out
  }

  private fun drawCompactTopTurnBanner(canvas: Canvas, w: Int, nav: NavUiState) {
    val step = nav.activeRoute?.steps?.getOrNull(nav.activeStepIndex) ?: return
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 8, 20, 30) }
    canvas.drawRoundRect(RectF(8f, 8f, w - 8f, 44f), 8f, 8f, bg)
    val txtP =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
      }
    val distStr =
      if (nav.distToNextStepM >= 1000.0) String.format(Locale.US, "%.1fkm", nav.distToNextStepM / 1000.0)
      else "${nav.distToNextStepM.roundToInt()}m"
    canvas.drawText("$distStr  ${step.instruction.take(26)}", 16f, 31f, txtP)
  }

  private fun drawSplitPanel(
    canvas: Canvas,
    x0: Int,
    w: Int,
    h: Int,
    nav: NavUiState,
    gps: GpsState,
    eff: EffectiveTelemetry,
  ) {
    val bgP = Paint().apply { color = Color.parseColor("#0B1620") }
    canvas.drawRect(x0.toFloat(), 0f, w.toFloat(), h.toFloat(), bgP)
    val divP = Paint().apply { color = Color.parseColor("#456477") }
    canvas.drawRect(x0.toFloat(), 0f, x0 + 2f, h.toFloat(), divP)

    val left = x0 + 10f
    val titleP =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
      }
    val accentP =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
      }
    val subP =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8C7D1")
        textSize = 12f
      }

    if (nav.isNavigating && nav.activeRoute != null) {
      val step = nav.activeRoute?.steps?.getOrNull(nav.activeStepIndex)
      val distStr =
        if (nav.distToNextStepM >= 1000.0) String.format(Locale.US, "%.1f km", nav.distToNextStepM / 1000.0)
        else "${nav.distToNextStepM.roundToInt()} m"
      canvas.drawText(distStr, left, 30f, titleP)
      canvas.drawText((step?.instruction ?: "Ikuti rute").take(20), left, 52f, accentP)
      step?.roadName?.takeIf { it.isNotBlank() }?.let {
        canvas.drawText(it.take(20), left, 72f, subP)
      }
      nav.activeRoute?.energyEstimate?.let { est ->
        canvas.drawText(
          "TIBA ${est.socAtArrivalPct?.roundToInt() ?: "—"}%",
          left,
          96f,
          accentP,
        )
      }

      // Elevation profile card at bottom right
      val samples = nav.activeRoute?.elevationSamples ?: emptyList()
      if (samples.size >= 3) {
        val cardTop = h - 74f
        val cardBg = Paint().apply { color = Color.parseColor("#10202B") }
        canvas.drawRoundRect(RectF(left - 2f, cardTop, w - 8f, h - 8f), 6f, 6f, cardBg)
        val minE = samples.minOf { it.elevationM }
        val maxE = samples.maxOf { it.elevationM }.let { if (it - minE < 5.0) minE + 5.0 else it }
        val px1 = left + 4f
        val px2 = w - 14f
        val py1 = cardTop + 10f
        val py2 = h - 14f
        val segP = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f }
        for (i in 1 until samples.size) {
          val xA = px1 + ((i - 1).toFloat() / samples.lastIndex) * (px2 - px1)
          val xB = px1 + (i.toFloat() / samples.lastIndex) * (px2 - px1)
          val yA = (py2 - ((samples[i - 1].elevationM - minE) / (maxE - minE)) * (py2 - py1)).toFloat()
          val yB = (py2 - ((samples[i].elevationM - minE) / (maxE - minE)) * (py2 - py1)).toFloat()
          segP.color =
            when (samples[i].gradeType) {
              HillGrade.UPHILL_STEEP -> Color.parseColor("#FF5252")
              HillGrade.UPHILL_MILD -> Color.parseColor("#FFA726")
              HillGrade.DOWNHILL -> Color.parseColor("#29B6F6")
              HillGrade.FLAT -> Color.parseColor("#00E676")
            }
          canvas.drawLine(xA, yA, xB, yB, segP)
        }
      }
    } else {
      val dirs = listOf("UTARA", "TIMUR LAUT", "TIMUR", "TENGGARA", "SELATAN", "BARAT DAYA", "BARAT", "BARAT LAUT")
      val hdg = ((gps.headingDeg ?: 0f) % 360f + 360f) % 360f
      val card = dirs[(hdg / 45f).roundToInt() % 8]
      canvas.drawText(card, left, 32f, titleP)
      canvas.drawText("${hdg.roundToInt()}°", left, 54f, subP)
      canvas.drawText("EFISIENSI", left, 86f, subP)
      canvas.drawText("${eff.blendedWhPerKm.roundToInt()} Wh/km", left, 108f, accentP)
      gps.roadName.takeIf { it.isNotBlank() }?.let {
        canvas.drawText(it.take(20), left, 140f, subP)
      }
      gps.location?.altM?.let {
        canvas.drawText("ELEV ${it.roundToInt()} m", left, 166f, titleP)
      }
    }
  }

  private fun decodeScaledWallpaper(context: Context, uriStr: String, w: Int, h: Int): Bitmap? {
    return try {
      val uri = Uri.parse(uriStr)
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val raw = BitmapFactory.decodeStream(stream) ?: return null
        val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
        if (scaled != raw) raw.recycle()
        scaled
      }
    } catch (_: Exception) {
      null
    }
  }
}
