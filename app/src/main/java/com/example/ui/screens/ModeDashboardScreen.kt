package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ChargingStatusPill
import com.example.ui.components.CockpitCard
import com.example.ui.components.StatTile
import com.example.ui.theme.LocalCockpitColors
import com.example.viewmodel.DashboardViewModel
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ModeDashboardScreen(
  viewModel: DashboardViewModel,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler { onClose() }

  val cockpit = LocalCockpitColors.current
  val eff by viewModel.effective.collectAsStateWithLifecycle()
  val chargingIndicator by viewModel.chargingIndicator.collectAsStateWithLifecycle()
  val liveTrip by viewModel.liveTrip.collectAsStateWithLifecycle()
  val gps by viewModel.gpsState.collectAsStateWithLifecycle()
  val nav by viewModel.navState.collectAsStateWithLifecycle()

  Box(
    modifier =
      modifier.fillMaxSize()
        .background(Color(0xFF03080D))
        .drawBehind {
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(cockpit.accent.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.34f),
                radius = size.width * 0.85f,
              ),
            center = Offset(size.width * 0.5f, size.height * 0.34f),
            radius = size.width * 0.85f,
          )
        }
        .statusBarsPadding(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 680.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Top Cockpit Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = cockpit.accent.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, cockpit.accent),
          ) {
            Text(
              text = eff.modeLabel,
              style = MaterialTheme.typography.labelLarge,
              color = cockpit.accent,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (eff.isPark == true) cockpit.warning.copy(alpha = 0.18f) else Color(0xFF10202D),
          ) {
            Text(
              text =
                when (eff.isPark) {
                  true -> "PARK"
                  false -> "DRIVE"
                  null -> "STANDBY"
                },
              style = MaterialTheme.typography.labelLarge,
              color = if (eff.isPark == true) cockpit.warning else Color(0xFFEAF2F8),
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }

        // Compass Heading Pill
        Text(
          text = gps.headingDeg?.let { "HDG ${it.roundToInt()}° (${compassDir(it)})" } ?: "HDG —°",
          style = MaterialTheme.typography.labelMedium,
          color = Color(0xFF8CA3B5),
        )

        IconButton(
          onClick = onClose,
          modifier = Modifier.size(48.dp).testTag("close_cockpit_btn"),
        ) {
          Icon(Icons.Filled.Close, contentDescription = "Keluar Mode Cockpit", tint = Color.White)
        }
      }

      // Active Turn-by-Turn Mini Banner if Navigating
      if (nav.isNavigating && nav.activeRoute != null) {
        val step = nav.activeRoute?.steps?.getOrNull(nav.activeStepIndex)
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = cockpit.accent.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, cockpit.accent),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Filled.Navigation, contentDescription = null, tint = cockpit.accent)
            Text(
              text = "${nav.distToNextStepM.roundToInt()} m • ${step?.instruction ?: ""}",
              style = MaterialTheme.typography.titleSmall,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      // Main Arc Speedometer Gauge
      CockpitCard(
        borderColor = cockpit.accent.copy(alpha = 0.48f),
        containerColor = Color(0xFF07111A),
        accentGlow = if (eff.isRegen) cockpit.regen else cockpit.accent,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          modifier = Modifier.fillMaxWidth().height(240.dp),
          contentAlignment = Alignment.Center,
        ) {
          val targetSpeed = (eff.speedKmh ?: 0f).coerceIn(0f, 120f)
          val animSpeed by
            animateFloatAsState(
              targetValue = targetSpeed,
              animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
              label = "hud_speed",
            )
          val activeSweepColor =
            when {
              eff.isRegen -> cockpit.regen
              animSpeed >= 80f -> cockpit.warning
              else -> cockpit.accent
            }

          Canvas(modifier = Modifier.size(230.dp)) {
            val strokePx = 18.dp.toPx()
            val pad = strokePx / 2f + 12.dp.toPx()
            val arcSize = Size(size.width - pad * 2f, size.height - pad * 2f)
            val topLeft = Offset(pad, pad)
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerR = size.minDimension / 2f - 2.dp.toPx()

            // Precision speedometer ticks (0..120 km/h)
            for (i in 0..24) {
              val deg = 140.0 + (260.0 / 24.0) * i
              val rad = deg * PI / 180.0
              val isMajor = i % 4 == 0
              val inner = outerR - if (isMajor) 8.dp.toPx() else 4.dp.toPx()
              val p1 = Offset(center.x + (inner * cos(rad)).toFloat(), center.y + (inner * sin(rad)).toFloat())
              val p2 = Offset(center.x + (outerR * cos(rad)).toFloat(), center.y + (outerR * sin(rad)).toFloat())
              val tickActive = (i / 24f) * 120f <= animSpeed
              drawLine(
                color = if (tickActive) activeSweepColor else Color(0xFF284054),
                start = p1,
                end = p2,
                strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                cap = StrokeCap.Round,
              )
            }

            drawArc(
              color = Color(0xFF152534),
              startAngle = 140f,
              sweepAngle = 260f,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (animSpeed > 0.5f) {
              val sweep = 260f * (animSpeed / 120f)
              drawArc(
                color = activeSweepColor.copy(alpha = 0.22f),
                startAngle = 140f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx + 8.dp.toPx(), cap = StrokeCap.Round),
              )
              drawArc(
                brush = Brush.sweepGradient(listOf(activeSweepColor.copy(alpha = 0.65f), activeSweepColor)),
                startAngle = 140f,
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
              text = eff.speedKmh?.let { "${it.roundToInt()}" } ?: "0",
              style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
            Text("KM/H", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp), color = activeSweepColor)
            Spacer(Modifier.height(4.dp))
            Text(
              text = "${eff.rpm ?: 0} RPM",
              style = MaterialTheme.typography.labelMedium,
              color = Color(0xFF8CA3B5),
            )
          }
        }

        // Power Flow Bar
        val pwr = eff.combinedPowerW ?: 0f
        val maxPwr = 4500f
        val pwrFraction by
          animateFloatAsState(
            targetValue = (abs(pwr) / maxPwr).coerceIn(0f, 1f),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "hud_power",
          )
        val pwrColor = if (pwr < -10f) cockpit.regen else if (pwr > 2500f) cockpit.warning else cockpit.accent

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              text = if (pwr < -10f) "ENERGY REGEN" else "POWER OUTPUT",
              style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
              color = pwrColor,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${pwr.roundToInt()} W",
              style = MaterialTheme.typography.titleSmall,
              color = pwrColor,
              fontWeight = FontWeight.Bold,
            )
          }
          LinearProgressIndicator(
            progress = { pwrFraction },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)),
            color = pwrColor,
            trackColor = Color(0xFF152534),
          )
        }
      }

      // Battery & Range Bar
      CockpitCard(
        containerColor = Color(0xFF08121B),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Baterai Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = "${eff.combinedSocPct?.roundToInt() ?: "—"}% • ~${String.format(Locale.US, "%.1f", eff.rangeKm)} km",
              style = MaterialTheme.typography.titleLarge,
              color = cockpit.accent,
              fontWeight = FontWeight.Bold,
            )
          }
          ChargingStatusPill(state = chargingIndicator)
        }
      }

      // Bottom Telemetry Grid
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Trip", String.format(Locale.US, "%.2f", liveTrip.distanceKm), "km", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        StatTile("Efisiensi", "${eff.blendedWhPerKm.roundToInt()}", "Wh/km", cockpit.accent, Modifier.weight(1f))
        StatTile("Volt", eff.avgVoltageV?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "V", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
      }
    }
  }
}

private fun compassDir(deg: Float): String {
  val dirs = listOf("U", "TL", "T", "TG", "S", "BD", "B", "BL")
  val idx = (((deg % 360f + 360f) % 360f) / 45f).roundToInt() % 8
  return dirs[idx]
}
