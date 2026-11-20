package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ChargingIndicatorState
import com.example.model.DailyBarData
import com.example.model.ElevationSample
import com.example.model.HillGrade
import com.example.ui.theme.GarageLivery
import com.example.ui.theme.LocalCockpitColors
import kotlin.math.roundToInt
import org.json.JSONArray

@Composable
fun ScooterHeroCanvas(
  livery: GarageLivery,
  isCharging: Boolean,
  isRegen: Boolean,
  modifier: Modifier = Modifier,
) {
  val inf = rememberInfiniteTransition(label = "scooter_pulse")
  val pulse by
    inf.animateFloat(
      initialValue = 0.35f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
      label = "glow_pulse",
    )

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val groundY = h * 0.84f

    // Radial halo behind scooter matching selected livery
    val haloColor = if (isCharging || isRegen) Color(0xFF00E5FF) else livery.bodyColor
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              haloColor.copy(alpha = if (isCharging || isRegen) 0.32f * pulse else 0.22f),
              Color.Transparent,
            ),
          center = Offset(cx, h * 0.54f),
          radius = w * 0.45f,
        ),
      center = Offset(cx, h * 0.54f),
      radius = w * 0.45f,
    )

    // Ground shadow
    drawOval(
      color = Color.Black.copy(alpha = 0.42f),
      topLeft = Offset(w * 0.14f, groundY - 5f),
      size = Size(w * 0.72f, 12f),
    )

    val rearWheelX = w * 0.26f
    val frontWheelX = w * 0.74f
    val wheelY = groundY - h * 0.15f
    val wheelR = h * 0.14f

    // Tires & rims
    for (wx in listOf(rearWheelX, frontWheelX)) {
      drawCircle(color = Color(0xFF151C22), radius = wheelR, center = Offset(wx, wheelY))
      drawCircle(
        color = Color(0xFF2B3842),
        radius = wheelR * 0.86f,
        center = Offset(wx, wheelY),
        style = Stroke(width = 3.5f),
      )
      drawCircle(
        color = livery.trimColor.copy(alpha = 0.75f),
        radius = wheelR * 0.56f,
        center = Offset(wx, wheelY),
        style = Stroke(width = 2.5f),
      )
      drawCircle(color = Color(0xFF8FA3B3), radius = wheelR * 0.18f, center = Offset(wx, wheelY))
    }

    // Swingarm & Floorboard chassis
    val chassis =
      Path().apply {
        moveTo(rearWheelX, wheelY)
        lineTo(w * 0.40f, wheelY - h * 0.05f)
        lineTo(w * 0.56f, wheelY - h * 0.05f)
        lineTo(frontWheelX - w * 0.06f, h * 0.46f)
      }
    drawPath(
      path = chassis,
      color = Color(0xFF222E38),
      style = Stroke(width = h * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Front Fork
    drawLine(
      color = Color(0xFF8FA3B3),
      start = Offset(w * 0.65f, h * 0.34f),
      end = Offset(frontWheelX, wheelY),
      strokeWidth = h * 0.045f,
      cap = StrokeCap.Round,
    )

    // Sleek EV Body Fairing (Painted in GarageLivery)
    val bodyPath =
      Path().apply {
        moveTo(w * 0.18f, h * 0.49f)
        quadraticTo(w * 0.25f, h * 0.37f, w * 0.44f, h * 0.42f)
        lineTo(w * 0.56f, h * 0.52f)
        lineTo(w * 0.66f, h * 0.34f)
        lineTo(w * 0.73f, h * 0.36f)
        quadraticTo(w * 0.79f, h * 0.52f, w * 0.68f, h * 0.62f)
        lineTo(w * 0.54f, h * 0.65f)
        lineTo(w * 0.36f, h * 0.62f)
        close()
      }
    drawPath(
      path = bodyPath,
      brush = Brush.linearGradient(listOf(livery.bodyColor, livery.trimColor)),
    )
    drawPath(
      path = bodyPath,
      color = Color.White.copy(alpha = 0.28f),
      style = Stroke(width = 2f, join = StrokeJoin.Round),
    )

    // Seat
    val seatPath =
      Path().apply {
        moveTo(w * 0.23f, h * 0.42f)
        quadraticTo(w * 0.34f, h * 0.36f, w * 0.48f, h * 0.41f)
        lineTo(w * 0.47f, h * 0.46f)
        lineTo(w * 0.22f, h * 0.46f)
        close()
      }
    drawPath(path = seatPath, color = Color(0xFF1A222A))

    // Handlebar & Windshield
    drawLine(
      color = Color(0xFFCFD8DC),
      start = Offset(w * 0.65f, h * 0.34f),
      end = Offset(w * 0.61f, h * 0.23f),
      strokeWidth = h * 0.032f,
      cap = StrokeCap.Round,
    )
    drawRoundRect(
      color = Color(0xFF101820),
      topLeft = Offset(w * 0.58f, h * 0.20f),
      size = Size(w * 0.08f, h * 0.055f),
      cornerRadius = CornerRadius(8f, 8f),
    )

    // LED Headlight Beam
    drawCircle(
      color = Color(0xFFE0F7FA),
      radius = h * 0.035f,
      center = Offset(w * 0.745f, h * 0.45f),
    )
    drawPath(
      path =
        Path().apply {
          moveTo(w * 0.76f, h * 0.43f)
          lineTo(w * 0.94f, h * 0.37f)
          lineTo(w * 0.94f, h * 0.54f)
          lineTo(w * 0.76f, h * 0.47f)
          close()
        },
      brush =
        Brush.horizontalGradient(
          colors = listOf(Color(0xFF80DEEA).copy(alpha = 0.32f), Color.Transparent),
          startX = w * 0.76f,
          endX = w * 0.94f,
        ),
    )

    // Charging / Regen energy flow indicator
    if (isCharging || isRegen) {
      val flowColor = if (isCharging) Color(0xFF00E676) else Color(0xFF00E5FF)
      drawCircle(
        color = flowColor.copy(alpha = 0.45f * pulse),
        radius = h * 0.09f,
        center = Offset(w * 0.42f, h * 0.55f),
      )
    }
  }
}

@Composable
fun RadialSocGauge(
  socPct: Float?,
  chargingState: ChargingIndicatorState,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  val displaySoc = socPct?.coerceIn(0f, 100f)
  val gaugeColor =
    when {
      chargingState == ChargingIndicatorState.CHARGING -> accentColor
      chargingState == ChargingIndicatorState.REGEN -> cockpit.regen
      displaySoc == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
      displaySoc <= 15f -> cockpit.danger
      displaySoc <= 30f -> cockpit.warning
      else -> accentColor
    }

  Box(modifier = modifier.size(168.dp), contentAlignment = Alignment.Center) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokePx = 14.dp.toPx()
      val pad = strokePx / 2f + 4f
      val arcSize = Size(size.width - pad * 2f, size.height - pad * 2f)
      val topLeft = Offset(pad, pad)

      drawArc(
        color = trackColor,
        startAngle = 135f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )

      if (displaySoc != null) {
        drawArc(
          brush = Brush.sweepGradient(listOf(gaugeColor.copy(alpha = 0.7f), gaugeColor)),
          startAngle = 135f,
          sweepAngle = 270f * (displaySoc / 100f),
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
      }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = displaySoc?.let { "${it.roundToInt()}%" } ?: "—%",
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 38.sp),
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = chargingState.label,
        style = MaterialTheme.typography.labelSmall,
        color = gaugeColor,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
fun TelemetryLineChart(
  values: List<Float>,
  lineColor: Color,
  modifier: Modifier = Modifier,
) {
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
    val w = size.width
    val h = size.height
    for (i in 0..3) {
      val y = h * (i / 3f)
      drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
    }
    if (values.size < 2) return@Canvas

    val minVal = values.minOrNull() ?: 0f
    val maxVal = (values.maxOrNull() ?: 1f).let { if (it - minVal < 0.1f) minVal + 1f else it }
    val span = maxVal - minVal

    val linePath = Path()
    val fillPath = Path()
    values.forEachIndexed { idx, v ->
      val x = (idx.toFloat() / (values.lastIndex)) * w
      val y = h - ((v - minVal) / span) * (h * 0.86f) - h * 0.07f
      if (idx == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, h)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
    }
    fillPath.lineTo(w, h)
    fillPath.close()

    drawPath(
      path = fillPath,
      brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)),
    )
    drawPath(
      path = linePath,
      color = lineColor,
      style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

@Composable
fun ElevationProfileCanvas(
  samples: List<ElevationSample>,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
  Canvas(modifier = modifier.fillMaxWidth().height(92.dp)) {
    val w = size.width
    val h = size.height
    drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)
    if (samples.size < 2) return@Canvas

    val minE = samples.minOf { it.elevationM }
    val maxE = samples.maxOf { it.elevationM }.let { if (it - minE < 5.0) minE + 5.0 else it }
    val span = maxE - minE

    for (i in 1 until samples.size) {
      val prev = samples[i - 1]
      val cur = samples[i]
      val x1 = ((i - 1).toFloat() / samples.lastIndex) * w
      val x2 = (i.toFloat() / samples.lastIndex) * w
      val y1 = (h - ((prev.elevationM - minE) / span) * (h * 0.78f) - h * 0.1f).toFloat()
      val y2 = (h - ((cur.elevationM - minE) / span) * (h * 0.78f) - h * 0.1f).toFloat()

      val segColor =
        when (cur.gradeType) {
          HillGrade.UPHILL_STEEP -> cockpit.danger
          HillGrade.UPHILL_MILD -> cockpit.warning
          HillGrade.DOWNHILL -> cockpit.info
          HillGrade.FLAT -> cockpit.accent
        }
      drawLine(segColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
  }
}

@Composable
fun DailyStatsBarCanvas(
  bars: List<DailyBarData>,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
  Canvas(modifier = modifier.fillMaxWidth().height(110.dp)) {
    if (bars.isEmpty()) return@Canvas
    val w = size.width
    val h = size.height
    val maxKm = bars.maxOf { it.distanceKm }.coerceAtLeast(1.0)
    val slotW = w / bars.size
    val barW = slotW * 0.52f

    bars.forEachIndexed { idx, b ->
      val left = idx * slotW + (slotW - barW) / 2f
      drawRoundRect(
        color = trackColor,
        topLeft = Offset(left, 6f),
        size = Size(barW, h - 12f),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
      )
      val barH = ((b.distanceKm / maxKm) * (h - 14f)).toFloat().coerceAtLeast(if (b.distanceKm > 0) 6f else 0f)
      if (barH > 0f) {
        drawRoundRect(
          brush = Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.55f))),
          topLeft = Offset(left, h - 6f - barH),
          size = Size(barW, barH),
          cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
      }
    }
  }
}

@Composable
fun TripPathReplayCanvas(
  pathJson: String,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  Canvas(modifier = modifier.fillMaxWidth().height(130.dp)) {
    val arr =
      try {
        JSONArray(pathJson)
      } catch (_: Exception) {
        JSONArray()
      }
    val pts = mutableListOf<Triple<Double, Double, Double>>()
    for (i in 0 until arr.length()) {
      val row = arr.optJSONArray(i) ?: continue
      pts.add(Triple(row.optDouble(0), row.optDouble(1), row.optDouble(2, 0.0)))
    }
    if (pts.size < 2) return@Canvas

    val minLat = pts.minOf { it.first }
    val maxLat = pts.maxOf { it.first }.let { if (it - minLat < 1e-5) minLat + 1e-4 else it }
    val minLng = pts.minOf { it.second }
    val maxLng = pts.maxOf { it.second }.let { if (it - minLng < 1e-5) minLng + 1e-4 else it }

    val pad = 16.dp.toPx()
    val usableW = size.width - pad * 2
    val usableH = size.height - pad * 2

    fun toOffset(lat: Double, lng: Double): Offset {
      val x = pad + (((lng - minLng) / (maxLng - minLng)) * usableW).toFloat()
      val y = size.height - pad - (((lat - minLat) / (maxLat - minLat)) * usableH).toFloat()
      return Offset(x, y)
    }

    for (i in 1 until pts.size) {
      val p1 = toOffset(pts[i - 1].first, pts[i - 1].second)
      val p2 = toOffset(pts[i].first, pts[i].second)
      val spd = pts[i].third
      val segColor =
        when {
          spd >= 50 -> cockpit.danger
          spd >= 28 -> cockpit.warning
          else -> accentColor
        }
      drawLine(segColor, p1, p2, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }

    val startOff = toOffset(pts.first().first, pts.first().second)
    val endOff = toOffset(pts.last().first, pts.last().second)
    drawCircle(accentColor, radius = 5.dp.toPx(), center = startOff)
    drawCircle(cockpit.danger, radius = 5.dp.toPx(), center = endOff)
  }
}
