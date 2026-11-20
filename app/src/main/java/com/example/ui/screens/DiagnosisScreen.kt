package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ERROR_DEFS
import com.example.ui.components.ChargingStatusPill
import com.example.ui.components.CockpitCard
import com.example.ui.components.RadialSocGauge
import com.example.ui.components.StatTile
import com.example.ui.components.TelemetryLineChart
import com.example.ui.theme.LocalCockpitColors
import com.example.viewmodel.DashboardViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisScreen(
  viewModel: DashboardViewModel,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val motorData by viewModel.motorData.collectAsStateWithLifecycle()
  val jkData by viewModel.jkData.collectAsStateWithLifecycle()
  val b2Data by viewModel.battery2Data.collectAsStateWithLifecycle()
  val eff by viewModel.effective.collectAsStateWithLifecycle()
  val chargingIndicator by viewModel.chargingIndicator.collectAsStateWithLifecycle()
  val chartHistory by viewModel.chartHistory.collectAsStateWithLifecycle()

  var selectedMainTab by remember { mutableIntStateOf(0) } // 0 = Throttle Status, 1 = Baterai
  var selectedBattTab by remember { mutableIntStateOf(0) } // 0 = B1, 1 = B2
  var sortCellsByVoltage by remember { mutableStateOf(false) }
  var showErrorSheet by remember { mutableStateOf(false) }

  Box(
    modifier =
      modifier.fillMaxSize().drawBehind {
        drawCircle(
          brush =
            Brush.radialGradient(
              colors = listOf(cockpit.info.copy(alpha = 0.12f), Color.Transparent),
              center = Offset(size.width * 0.5f, 0f),
              radius = size.width * 0.9f,
            ),
          center = Offset(size.width * 0.5f, 0f),
          radius = size.width * 0.9f,
        )
      },
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = selectedMainTab == 0,
            onClick = { selectedMainTab = 0 },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            modifier = Modifier.testTag("diag_tab_throttle"),
          ) {
            Text("Throttle Status")
          }
          SegmentedButton(
            selected = selectedMainTab == 1,
            onClick = { selectedMainTab = 1 },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            modifier = Modifier.testTag("diag_tab_battery"),
          ) {
            Text("Status Baterai")
          }
        }
      }

      if (selectedMainTab == 0) {
        // ── THROTTLE & MOTOR DIAGNOSIS ──
        item {
          CockpitCard(
            accentGlow = cockpit.accent,
            borderColor = cockpit.accent.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Grafik Throttle & Daya Real-Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
              Text(
                text = eff.throttlePct?.let { "${it.roundToInt()}%" } ?: "—%",
                style = MaterialTheme.typography.titleMedium,
                color = cockpit.accent,
                fontWeight = FontWeight.Bold,
              )
            }
            Spacer(Modifier.height(8.dp))
            TelemetryLineChart(
              values = chartHistory.map { it.throttlePct },
              lineColor = cockpit.accent,
            )
            if (chartHistory.size < 2) {
              Spacer(Modifier.height(4.dp))
              Text(
                "Hubungkan Motor atau ESP32 untuk merekam kurva throttle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // 12 Diagnostic Grid Tiles
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatTile("Kecepatan", eff.speedKmh?.let { "${it.roundToInt()}" } ?: "—", "km/h", cockpit.accent, Modifier.weight(1f))
              StatTile("RPM Motor", eff.rpm?.toString() ?: "—", "rpm", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
              StatTile("Throttle", eff.throttlePct?.let { "${it.roundToInt()}" } ?: "—", "%", cockpit.info, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatTile("Daya", eff.combinedPowerW?.let { "${it.roundToInt()}" } ?: "—", "W", cockpit.warning, Modifier.weight(1f))
              StatTile("Tegangan", eff.avgVoltageV?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "V", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
              StatTile("Arus", eff.combinedCurrentA?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "A", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatTile("T. Inverter", motorData.tempInverterC?.let { "${it.roundToInt()}" } ?: "—", "°C", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
              StatTile("T. Motor", motorData.tempMotorC?.let { "${it.roundToInt()}" } ?: "—", "°C", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
              StatTile("T. Baterai", eff.b1TempC?.let { "${it.roundToInt()}" } ?: "—", "°C", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatTile("Mode", eff.modeLabel, "", cockpit.accent, Modifier.weight(1f))
              StatTile(
                "Status",
                when {
                  eff.reverseActive -> "MUNDUR"
                  eff.cruiseActive -> "CRUISE"
                  eff.sideStandDown -> "STANDAR"
                  eff.isPark == true -> "PARK"
                  eff.isPark == false -> "DRIVE"
                  else -> "—"
                },
                "",
                MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f),
              )
              CockpitCard(
                borderColor =
                  if (eff.activeErrors.isEmpty()) cockpit.accent.copy(alpha = 0.5f)
                  else cockpit.danger,
                onClick = { showErrorSheet = true },
                modifier = Modifier.weight(1f).testTag("error_status_card"),
              ) {
                Text("Error Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  text = if (eff.activeErrors.isEmpty()) "0 OK" else "${eff.activeErrors.size} ERR",
                  style = MaterialTheme.typography.titleLarge,
                  color = if (eff.activeErrors.isEmpty()) cockpit.accent else cockpit.danger,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      } else {
        // ── BATTERY REAL-TIME STATUS & DUAL BATTERY MONITOR ──
        item {
          CockpitCard(
            accentGlow = cockpit.accent,
            borderColor = cockpit.accent.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().testTag("battery_realtime_card"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              RadialSocGauge(
                socPct = eff.combinedSocPct,
                chargingState = chargingIndicator,
                accentColor = cockpit.accent,
              )
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ChargingStatusPill(state = chargingIndicator, modifier = Modifier.testTag("diag_charging_pill"))
                StatTile(
                  label = "Estimasi Jarak Tersisa",
                  value =
                    if (eff.combinedSocPct != null) String.format(Locale.US, "%.1f", eff.rangeKm)
                    else "—",
                  unit = "km",
                  accentValueColor = cockpit.accent,
                )
                StatTile(
                  label = "Konsumsi Saat Ini",
                  value = "${eff.blendedWhPerKm.roundToInt()}",
                  unit = "Wh/km",
                )
              }
            }
          }
        }

        // Dual Battery Switcher (B1 vs B2)
        item {
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
              selected = selectedBattTab == 0,
              onClick = { selectedBattTab = 0 },
              shape = SegmentedButtonDefaults.itemShape(0, 2),
              modifier = Modifier.testTag("batt_switch_1"),
            ) {
              Text("Baterai 1 (${eff.b1SourceLabel})")
            }
            SegmentedButton(
              selected = selectedBattTab == 1,
              onClick = { selectedBattTab = 1 },
              shape = SegmentedButtonDefaults.itemShape(1, 2),
              modifier = Modifier.testTag("batt_switch_2"),
            ) {
              Text("Baterai 2 (BMS ORI)")
            }
          }
        }

        if (selectedBattTab == 0) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("SOC B1", eff.b1SocPct?.let { "${it.roundToInt()}" } ?: "—", "%", cockpit.accent, Modifier.weight(1f))
                StatTile("Tegangan", eff.b1Vbat?.let { String.format(Locale.US, "%.2f", it) } ?: "—", "V", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                StatTile("Arus", eff.b1Ibat?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "A", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
              }
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Delta Sel", jkData.deltaCellV?.let { "${(it * 1000).roundToInt()}" } ?: "—", "mV", cockpit.warning, Modifier.weight(1f))
                StatTile("SOH", eff.b1SohPct?.let { "${it.roundToInt()}" } ?: "—", "%", cockpit.accent, Modifier.weight(1f))
                StatTile("Siklus", (jkData.cycles ?: motorData.cbatCycles)?.toString() ?: "—", "x", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
              }
            }
          }

          item {
            CellVoltageGridCard(
              cells = jkData.cellVoltages,
              sortByVoltage = sortCellsByVoltage,
              onToggleSort = { sortCellsByVoltage = !sortCellsByVoltage },
            )
          }
        } else {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("SOC B2", b2Data.socPct?.let { "${it.roundToInt()}" } ?: "—", "%", cockpit.info, Modifier.weight(1f))
                StatTile("Tegangan", b2Data.vbat?.let { String.format(Locale.US, "%.2f", it) } ?: "—", "V", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                StatTile("Arus", b2Data.ibat?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "A", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
              }
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Delta Sel", b2Data.deltaCellV?.let { "${(it * 1000).roundToInt()}" } ?: "—", "mV", cockpit.warning, Modifier.weight(1f))
                StatTile("Suhu B2", b2Data.tempC?.let { "${it.roundToInt()}" } ?: "—", "°C", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                StatTile("CAN Mode", b2Data.canMode, "", cockpit.info, Modifier.weight(1f))
              }
              CockpitCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("Balik Polaritas Arus Baterai 2", style = MaterialTheme.typography.titleSmall)
                    Text(
                      "Aktifkan jika tanda +/- arus BMS ORI terbalik",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Switch(
                    checked = settings.b2InvertCurrent,
                    onCheckedChange = { viewModel.setB2InvertCurrent(it) },
                    modifier = Modifier.testTag("b2_invert_switch"),
                  )
                }
              }
            }
          }

          item {
            CellVoltageGridCard(
              cells = b2Data.cellVoltages,
              sortByVoltage = sortCellsByVoltage,
              onToggleSort = { sortCellsByVoltage = !sortCellsByVoltage },
            )
          }
        }

        // Voltage Trend Chart
        item {
          CockpitCard(
            accentGlow = cockpit.info,
            borderColor = cockpit.info.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Tren Tegangan Pack (V)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TelemetryLineChart(
              values = chartHistory.map { it.voltageV },
              lineColor = cockpit.info,
            )
          }
        }
      }

      item { Spacer(Modifier.height(12.dp)) }
    }
  }

  if (showErrorSheet) {
    ModalBottomSheet(onDismissRequest = { showErrorSheet = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Detail Kode Error Motor (AT+ERR)", style = MaterialTheme.typography.titleLarge)
        Text(
          "Bitmask aktif: 0x${motorData.errMask.toString(16).uppercase()}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ERROR_DEFS.forEach { def ->
          val active = ((motorData.errMask shr def.bit) and 1) == 1
          CockpitCard(
            borderColor = if (active) cockpit.danger else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            containerColor =
              if (active) cockpit.danger.copy(alpha = 0.12f)
              else MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                "Bit ${def.bit}: ${def.label}",
                style = MaterialTheme.typography.titleSmall,
                color = if (active) cockpit.danger else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
              )
              Text(
                if (active) "AKTIF" else "OK",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) cockpit.danger else cockpit.accent,
                fontWeight = FontWeight.Bold,
              )
            }
            Spacer(Modifier.height(2.dp))
            Text(def.fix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Spacer(Modifier.height(20.dp))
      }
    }
  }
}

@Composable
private fun CellVoltageGridCard(
  cells: List<Float>,
  sortByVoltage: Boolean,
  onToggleSort: () -> Unit,
) {
  val cockpit = LocalCockpitColors.current
  CockpitCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "Tegangan Sel (${cells.size}S)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = if (sortByVoltage) "Urut: Voltase" else "Urut: Nomor Sel",
        style = MaterialTheme.typography.labelMedium,
        color = cockpit.accent,
        modifier =
          Modifier.clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggleSort)
            .padding(horizontal = 8.dp, vertical = 6.dp),
      )
    }
    Spacer(Modifier.height(8.dp))
    if (cells.isEmpty()) {
      Text(
        "Belum ada data per-sel. Hubungkan JK-BMS atau ESP32 #2.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      val minV = cells.minOrNull()
      val maxV = cells.maxOrNull()
      val indexed =
        cells.mapIndexed { i, v -> (i + 1) to v }.let { list ->
          if (sortByVoltage) list.sortedBy { it.second } else list
        }
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        indexed.chunked(4).forEach { rowItems ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowItems.forEach { (idx, v) ->
              val isMin = v == minV
              val isMax = v == maxV
              val tint =
                when {
                  isMax -> cockpit.accent
                  isMin -> cockpit.info
                  else -> MaterialTheme.colorScheme.onSurface
                }
              Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color =
                  if (isMin || isMax) tint.copy(alpha = 0.14f)
                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border =
                  BorderStroke(
                    if (isMin || isMax) 1.2.dp else 1.dp,
                    if (isMin || isMax) tint.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                  ),
              ) {
                Column(
                  modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Text(
                    text =
                      when {
                        isMax -> "C$idx ▲"
                        isMin -> "C$idx ▼"
                        else -> "C$idx"
                      },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isMin || isMax) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isMin || isMax) FontWeight.Bold else FontWeight.Medium,
                  )
                  Text(
                    String.format(Locale.US, "%.3fV", v),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
            repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
          }
        }
      }
    }
  }
}
