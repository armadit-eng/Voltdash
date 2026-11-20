package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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
    val groundY = h * 0.85f

    // Subtle cockpit grid horizon line behind scooter
    val gridCol = Color.White.copy(alpha = 0.05f)
    for (i in 1..3) {
      val gy = groundY - i * (h * 0.14f)
      drawLine(gridCol, Offset(w * 0.06f, gy), Offset(w * 0.94f, gy), strokeWidth = 1f)
    }
    drawLine(
      brush = Brush.horizontalGradient(listOf(Color.Transparent, livery.bodyColor.copy(alpha = 0.35f), Color.Transparent)),
      start = Offset(w * 0.08f, groundY),
      end = Offset(w * 0.92f, groundY),
      strokeWidth = 2f,
    )

    // Radial halo behind scooter matching selected livery
    val haloColor = if (isCharging || isRegen) Color(0xFF00E5FF) else livery.bodyColor
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              haloColor.copy(alpha = if (isCharging || isRegen) 0.36f * pulse else 0.24f),
              Color.Transparent,
            ),
          center = Offset(cx, h * 0.53f),
          radius = w * 0.48f,
        ),
      center = Offset(cx, h * 0.53f),
      radius = w * 0.48f,
    )

    // Ground shadow
    drawOval(
      color = Color.Black.copy(alpha = 0.48f),
      topLeft = Offset(w * 0.14f, groundY - 4f),
      size = Size(w * 0.72f, 11f),
    )

    val rearWheelX = w * 0.26f
    val frontWheelX = w * 0.74f
    val wheelY = groundY - h * 0.145f
    val wheelR = h * 0.14f

    // Tires & precision alloy rims
    for (wx in listOf(rearWheelX, frontWheelX)) {
      drawCircle(color = Color(0xFF10161C), radius = wheelR, center = Offset(wx, wheelY))
      drawCircle(
        color = Color(0xFF2E3C47),
        radius = wheelR * 0.85f,
        center = Offset(wx, wheelY),
        style = Stroke(width = 3.5f),
      )
      drawCircle(
        color = livery.trimColor.copy(alpha = 0.85f),
        radius = wheelR * 0.56f,
        center = Offset(wx, wheelY),
        style = Stroke(width = 2.5f),
      )
      drawCircle(color = Color(0xFFA4B8C7), radius = wheelR * 0.18f, center = Offset(wx, wheelY))
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
      color = Color(0xFF212E39),
      style = Stroke(width = h * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Front Fork
    drawLine(
      color = Color(0xFFA0B4C4),
      start = Offset(w * 0.65f, h * 0.34f),
      end = Offset(frontWheelX, wheelY),
      strokeWidth = h * 0.044f,
      cap = StrokeCap.Round,
    )

    // Sleek EV Body Fairing (Painted in GarageLivery)
    val bodyPath =
      Path().apply {
        moveTo(w * 0.17f, h * 0.49f)
        quadraticTo(w * 0.25f, h * 0.36f, w * 0.44f, h * 0.42f)
        lineTo(w * 0.56f, h * 0.52f)
        lineTo(w * 0.66f, h * 0.33f)
        lineTo(w * 0.735f, h * 0.36f)
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
      color = Color.White.copy(alpha = 0.34f),
      style = Stroke(width = 2f, join = StrokeJoin.Round),
    )

    // Tail LED Accent
    drawCircle(
      color = Color(0xFFFF5252).copy(alpha = 0.85f),
      radius = h * 0.024f,
      center = Offset(w * 0.175f, h * 0.49f),
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
    drawPath(path = seatPath, color = Color(0xFF151D24))

    // Handlebar & Windshield
    drawLine(
      color = Color(0xFFCFD8DC),
      start = Offset(w * 0.65f, h * 0.34f),
      end = Offset(w * 0.61f, h * 0.23f),
      strokeWidth = h * 0.032f,
      cap = StrokeCap.Round,
    )
    drawRoundRect(
      color = Color(0xFF0E161E),
      topLeft = Offset(w * 0.58f, h * 0.20f),
      size = Size(w * 0.085f, h * 0.055f),
      cornerRadius = CornerRadius(8f, 8f),
    )

    // LED Headlight Beam
    drawCircle(
      color = Color(0xFFE0F7FA),
      radius = h * 0.036f,
      center = Offset(w * 0.745f, h * 0.45f),
    )
    drawPath(
      path =
        Path().apply {
          moveTo(w * 0.76f, h * 0.43f)
          lineTo(w * 0.95f, h * 0.36f)
          lineTo(w * 0.95f, h * 0.55f)
          lineTo(w * 0.76f, h * 0.47f)
          close()
        },
      brush =
        Brush.horizontalGradient(
          colors = listOf(Color(0xFF80DEEA).copy(alpha = 0.36f), Color.Transparent),
          startX = w * 0.76f,
          endX = w * 0.95f,
        ),
    )

    // Charging / Regen energy flow indicator
    if (isCharging || isRegen) {
      val flowColor = if (isCharging) Color(0xFF00E676) else Color(0xFF00E5FF)
      drawCircle(
        color = flowColor.copy(alpha = 0.50f * pulse),
        radius = h * 0.095f,
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
  val animSoc by
    animateFloatAsState(
      targetValue = displaySoc ?: 0f,
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "soc_sweep",
    )
  val gaugeColor =
    when {
      chargingState == ChargingIndicatorState.CHARGING -> accentColor
      chargingState == ChargingIndicatorState.REGEN -> cockpit.regen
      displaySoc == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
      displaySoc <= 15f -> cockpit.danger
      displaySoc <= 30f -> cockpit.warning
      else -> accentColor
    }

  Box(modifier = modifier.size(172.dp), contentAlignment = Alignment.Center) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokePx = 14.dp.toPx()
      val pad = strokePx / 2f + 10.dp.toPx()
      val arcSize = Size(size.width - pad * 2f, size.height - pad * 2f)
      val topLeft = Offset(pad, pad)
      val center = Offset(size.width / 2f, size.height / 2f)
      val outerR = size.minDimension / 2f - 2.dp.toPx()

      // Outer precision tick marks
      for (i in 0..20) {
        val deg = 135.0 + (270.0 / 20.0) * i
        val rad = deg * PI / 180.0
        val inner = outerR - if (i % 5 == 0) 6.dp.toPx() else 3.dp.toPx()
        val p1 = Offset(center.x + (inner * cos(rad)).toFloat(), center.y + (inner * sin(rad)).toFloat())
        val p2 = Offset(center.x + (outerR * cos(rad)).toFloat(), center.y + (outerR * sin(rad)).toFloat())
        val tickActive = displaySoc != null && (i / 20f) * 100f <= animSoc
        drawLine(
          color = if (tickActive) gaugeColor.copy(alpha = 0.85f) else trackColor,
          start = p1,
          end = p2,
          strokeWidth = if (i % 5 == 0) 2.dp.toPx() else 1.2.dp.toPx(),
          cap = StrokeCap.Round,
        )
      }

      // Base track arc
      drawArc(
        color = trackColor,
        startAngle = 135f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )

      // Ambient glow + active arc
      if (displaySoc != null && animSoc > 0.5f) {
        val sweep = 270f * (animSoc / 100f)
        drawArc(
          color = gaugeColor.copy(alpha = 0.22f),
          startAngle = 135f,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokePx + 6.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
          brush = Brush.sweepGradient(listOf(gaugeColor.copy(alpha = 0.7f), gaugeColor)),
          startAngle = 135f,
          sweepAngle = sweep,
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
        fontWeight = FontWeight.Bold,
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
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
  Canvas(modifier = modifier.fillMaxWidth().height(126.dp)) {
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

    val pts =
      values.mapIndexed { idx, v ->
        val x = (idx.toFloat() / values.lastIndex) * w
        val y = h - ((v - minVal) / span) * (h * 0.84f) - h * 0.08f
        Offset(x, y)
      }

    val linePath = Path().apply { moveTo(pts.first().x, pts.first().y) }
    val fillPath =
      Path().apply {
        moveTo(pts.first().x, h)
        lineTo(pts.first().x, pts.first().y)
      }
    for (i in 1 until pts.size) {
      val prev = pts[i - 1]
      val cur = pts[i]
      val midX = (prev.x + cur.x) / 2f
      linePath.cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
      fillPath.cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
    }
    fillPath.lineTo(w, h)
    fillPath.close()

    drawPath(
      path = fillPath,
      brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.30f), Color.Transparent)),
    )
    drawPath(
      path = linePath,
      color = lineColor,
      style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    val lastPt = pts.last()
    drawCircle(lineColor.copy(alpha = 0.28f), radius = 7.dp.toPx(), center = lastPt)
    drawCircle(lineColor, radius = 3.5.dp.toPx(), center = lastPt)
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
  val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
  Canvas(modifier = modifier.fillMaxWidth().height(114.dp)) {
    if (bars.isEmpty()) return@Canvas
    val w = size.width
    val h = size.height
    val maxKm = bars.maxOf { it.distanceKm }.coerceAtLeast(1.0)
    val slotW = w / bars.size
    val barW = slotW * 0.50f

    bars.forEachIndexed { idx, b ->
      val left = idx * slotW + (slotW - barW) / 2f
      drawRoundRect(
        color = trackColor,
        topLeft = Offset(left, 6f),
        size = Size(barW, h - 12f),
        cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
      )
      val barH = ((b.distanceKm / maxKm) * (h - 14f)).toFloat().coerceAtLeast(if (b.distanceKm > 0) 8f else 0f)
      if (barH > 0f) {
        val topY = h - 6f - barH
        drawRoundRect(
          brush = Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.45f))),
          topLeft = Offset(left, topY),
          size = Size(barW, barH),
          cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
        )
        drawRoundRect(
          color = Color.White.copy(alpha = 0.35f),
          topLeft = Offset(left, topY),
          size = Size(barW, 3.dp.toPx()),
          cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
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
      drawLine(segColor, p1, p2, strokeWidth = 3.2.dp.toPx(), cap = StrokeCap.Round)
    }

    val startOff = toOffset(pts.first().first, pts.first().second)
    val endOff = toOffset(pts.last().first, pts.last().second)
    drawCircle(accentColor.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = startOff)
    drawCircle(accentColor, radius = 5.dp.toPx(), center = startOff)
    drawCircle(cockpit.danger.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = endOff)
    drawCircle(cockpit.danger, radius = 5.dp.toPx(), center = endOff)
  }
}
