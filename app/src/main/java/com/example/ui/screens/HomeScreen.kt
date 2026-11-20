package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.TelemetryEngine
import com.example.model.AlertSeverity
import com.example.model.ConnectionState
import com.example.ui.components.ChargingStatusPill
import com.example.ui.components.CockpitCard
import com.example.ui.components.ConnectionStatusStrip
import com.example.ui.components.HoldToConfirmButton
import com.example.ui.components.MainTab
import com.example.ui.components.ScooterHeroCanvas
import com.example.ui.components.StatTile
import com.example.ui.components.TripPathReplayCanvas
import com.example.ui.theme.GarageLivery
import com.example.ui.theme.LocalCockpitColors
import com.example.viewmodel.DashboardViewModel
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: DashboardViewModel,
  onNavigateTab: (MainTab) -> Unit,
  onOpenModeDashboard: () -> Unit,
  onOpenSpkluSearch: () -> Unit,
  onOpenConnectSheet: (slot: String) -> Unit,
  onOpenGarage: () -> Unit,
  onOpenMirrorModal: () -> Unit,
  onRequestLocationPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val mirrorActive by viewModel.mirrorActive.collectAsStateWithLifecycle()
  val motorState by viewModel.motorState.collectAsStateWithLifecycle()
  val jkState by viewModel.jkState.collectAsStateWithLifecycle()
  val esp1State by viewModel.esp1State.collectAsStateWithLifecycle()
  val esp2State by viewModel.esp2State.collectAsStateWithLifecycle()
  val esp1Data by viewModel.esp1Data.collectAsStateWithLifecycle()
  val eff by viewModel.effective.collectAsStateWithLifecycle()
  val chargingIndicator by viewModel.chargingIndicator.collectAsStateWithLifecycle()
  val liveTrip by viewModel.liveTrip.collectAsStateWithLifecycle()
  val ridingScore by viewModel.ridingScore.collectAsStateWithLifecycle()
  val weather by viewModel.weather.collectAsStateWithLifecycle()
  val gps by viewModel.gpsState.collectAsStateWithLifecycle()
  val alerts by viewModel.alerts.collectAsStateWithLifecycle()
  val trips by viewModel.trips.collectAsStateWithLifecycle()

  val livery = remember(settings.garageLiveryId) { GarageLivery.fromId(settings.garageLiveryId) }
  val maintStatus =
    remember(eff.odoKm, settings.maintLastOdoKm, settings.maintIntervalKm) {
      TelemetryEngine.computeMaintenanceStatus(eff.odoKm, settings)
    }

  var espSectionExpanded by remember { mutableStateOf(false) }
  var showAlertsSheet by remember { mutableStateOf(false) }

  val greetingPrefix =
    remember {
      val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
      when (hr) {
        in 4..10 -> "Selamat Pagi"
        in 11..14 -> "Selamat Siang"
        in 15..17 -> "Selamat Sore"
        else -> "Selamat Malam"
      }
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // 1. Top Connection Strip + Bell
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ConnectionStatusStrip(
            motorState = motorState,
            jkState = jkState,
            esp1State = esp1State,
            esp2State = esp2State,
            onChipClick = onOpenConnectSheet,
            modifier = Modifier.weight(1f),
          )
          Surface(
            modifier =
              Modifier.size(48.dp)
                .clickable { showAlertsSheet = true }
                .testTag("notifications_button"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border =
              BorderStroke(
                1.dp,
                if (alerts.isNotEmpty()) cockpit.warning.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
              ),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Filled.Notifications,
                contentDescription = "Notifikasi dan Alarm",
                tint = if (alerts.isNotEmpty()) cockpit.warning else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (alerts.isNotEmpty()) {
                Box(
                  modifier =
                    Modifier.align(Alignment.TopEnd)
                      .padding(8.dp)
                      .size(9.dp)
                      .clip(CircleShape)
                      .background(cockpit.danger)
                )
              }
            }
          }
        }
      }

      // 2. Smart Alert Banner (when active)
      if (alerts.isNotEmpty()) {
        item {
          val topAlert = alerts.first()
          val alertColor =
            when (topAlert.severity) {
              AlertSeverity.CRITICAL -> cockpit.danger
              AlertSeverity.WARNING -> cockpit.warning
              AlertSeverity.INFO -> cockpit.info
            }
          CockpitCard(
            borderColor = alertColor.copy(alpha = 0.7f),
            containerColor = alertColor.copy(alpha = 0.12f),
            onClick = { showAlertsSheet = true },
            modifier = Modifier.fillMaxWidth().testTag("smart_alert_banner"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(Icons.Filled.Warning, contentDescription = "Peringatan", tint = alertColor)
              Column(modifier = Modifier.weight(1f)) {
                Text(topAlert.title, style = MaterialTheme.typography.titleSmall, color = alertColor)
                Text(
                  topAlert.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      // 3. Greeting Card
      item {
        CockpitCard(
          borderColor = cockpit.accent.copy(alpha = 0.35f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "$greetingPrefix, ${settings.riderName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              val locTxt =
                listOf(gps.roadName, gps.areaName).filter { it.isNotBlank() }.joinToString(", ")
                  .ifBlank { if (gps.hasPermission) "Mencari lokasi GPS…" else "Izin lokasi belum aktif" }
              Text(
                text = locTxt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = eff.odoKm?.let { "ODO ${String.format(Locale.US, "%.0f", it)} km" } ?: "ODO — km",
                style = MaterialTheme.typography.labelMedium,
                color = cockpit.accent,
                fontWeight = FontWeight.Bold,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = eff.modeLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    when (eff.isPark) {
                      true -> "• PARK"
                      false -> "• DRIVE"
                      null -> ""
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color = if (eff.isPark == true) cockpit.warning else cockpit.accent,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }

      // 4. Hero EV Card (Scooter Visual + Floating Bubbles + Real-Time Battery SOC, Range & Charging Indicator)
      item {
        CockpitCard(
          borderColor = livery.bodyColor.copy(alpha = 0.45f),
          modifier = Modifier.fillMaxWidth().testTag("hero_ev_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ChargingStatusPill(state = chargingIndicator, modifier = Modifier.testTag("hero_charging_pill"))
            Text(
              text =
                if (eff.b2Active) "Dual Battery (B1+B2)"
                else if (eff.b1SourceLabel != "—") "Sumber: ${eff.b1SourceLabel}"
                else "Menunggu BMS/Motor",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Spacer(Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            // Left: Scooter Graphic + Sensor Bubbles
            Box(
              modifier =
                Modifier.weight(1.35f)
                  .height(155.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
              ScooterHeroCanvas(
                livery = livery,
                isCharging = eff.isCharging,
                isRegen = eff.isRegen,
                modifier = Modifier.fillMaxSize(),
              )

              // Floating Bubble Top-Left: Speed
              Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
              ) {
                Text(
                  text = eff.speedKmh?.let { "${it.roundToInt()} km/h" } ?: "— km/h",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }

              // Floating Bubble Top-Right: Power
              Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
              ) {
                Text(
                  text = eff.combinedPowerW?.let { "${it.roundToInt()} W" } ?: "— W",
                  style = MaterialTheme.typography.labelMedium,
                  color =
                    if ((eff.combinedPowerW ?: 0f) < -20f) cockpit.regen
                    else MaterialTheme.colorScheme.onSurface,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }

              // Floating Bubble Bottom-Left: Voltage / Temp
              Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
              ) {
                val vTxt = eff.avgVoltageV?.let { String.format(Locale.US, "%.1fV", it) } ?: "—V"
                val tTxt = eff.b1TempC?.let { "${it.roundToInt()}°C" } ?: "—°C"
                Text(
                  text = "$vTxt • $tTxt",
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }

            // Right: SOC %, Bar, Range Estimate, B1/B2 Breakdown
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = "Baterai Total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = eff.combinedSocPct?.let { "${it.roundToInt()}%" } ?: "—%",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp),
                color =
                  when {
                    eff.combinedSocPct == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    eff.combinedSocPct!! <= 15f -> cockpit.danger
                    eff.combinedSocPct!! <= 30f -> cockpit.warning
                    else -> cockpit.accent
                  },
                fontWeight = FontWeight.Bold,
              )
              LinearProgressIndicator(
                progress = { ((eff.combinedSocPct ?: 0f) / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                color = cockpit.accent,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
              )
              Text(
                text =
                  if (eff.combinedSocPct != null) {
                    "Sisa Jarak ~${String.format(Locale.US, "%.1f", eff.rangeKm)} km"
                  } else {
                    "Sisa Jarak — km"
                  },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "@ ${eff.blendedWhPerKm.roundToInt()} Wh/km",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (eff.b2Active) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    "B1: ${eff.b1SocPct?.roundToInt() ?: "—"}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = cockpit.accent,
                  )
                  Text(
                    "B2: ${eff.b2SocPct?.roundToInt() ?: "—"}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = cockpit.info,
                  )
                }
              }
            }
          }
        }
      }

      // 5. Weather Card
      item {
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                Icons.Filled.WbSunny,
                contentDescription = "Cuaca",
                tint = cockpit.warning,
                modifier = Modifier.size(28.dp),
              )
              Column {
                Text(
                  text =
                    weather.tempC?.let {
                      "${it.roundToInt()}°C • ${weather.conditionLabel}"
                    } ?: weather.conditionLabel,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    if (weather.windKmh != null) {
                      "${weather.cityName} • Angin ${weather.windKmh?.roundToInt()} km/j • Hujan ${weather.rainProbPct ?: 0}%"
                    } else if (!gps.hasPermission) {
                      "Aktifkan izin lokasi untuk cuaca lokal"
                    } else {
                      "Menunggu koordinat GPS…"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            if (!gps.hasPermission) {
              OutlinedButton(
                onClick = onRequestLocationPermission,
                modifier = Modifier.heightIn(min = 48.dp).testTag("grant_location_btn"),
              ) {
                Text("Izin GPS")
              }
            } else {
              IconButton(
                onClick = { viewModel.refreshWeatherNow() },
                modifier = Modifier.size(48.dp).testTag("refresh_weather_btn"),
              ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Perbarui Cuaca")
              }
            }
          }

          if (weather.hourly.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              weather.hourly.forEach { h ->
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                  ) {
                    Text(h.timeLabel, style = MaterialTheme.typography.labelSmall)
                    Text(
                      "${h.tempC.roundToInt()}°",
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      "${h.rainProbPct}%",
                      style = MaterialTheme.typography.labelSmall,
                      color = cockpit.info,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 6. Quick Actions Grid (2x3)
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            QuickActionButton(
              label = "Navigasi",
              icon = Icons.Filled.Navigation,
              tint = cockpit.accent,
              onClick = { onNavigateTab(MainTab.NAVIGASI) },
              modifier = Modifier.weight(1f).testTag("qa_navigasi"),
            )
            QuickActionButton(
              label = "Cari SPKLU",
              icon = Icons.Filled.EvStation,
              tint = cockpit.info,
              onClick = onOpenSpkluSearch,
              modifier = Modifier.weight(1f).testTag("qa_spklu"),
            )
            QuickActionButton(
              label = "Cockpit HUD",
              icon = Icons.Filled.Speed,
              tint = cockpit.warning,
              onClick = onOpenModeDashboard,
              modifier = Modifier.weight(1f).testTag("qa_cockpit"),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            QuickActionButton(
              label = "Riwayat Trip",
              icon = Icons.Filled.Analytics,
              tint = cockpit.accentSecondary,
              onClick = { onNavigateTab(MainTab.STATS) },
              modifier = Modifier.weight(1f).testTag("qa_stats"),
            )
            QuickActionButton(
              label = if (mirrorActive) "Mirror Aktif" else "Mirror Layar",
              icon = Icons.Filled.Cast,
              tint = if (mirrorActive) cockpit.accent else cockpit.info,
              onClick = onOpenMirrorModal,
              modifier = Modifier.weight(1f).testTag("qa_mirror"),
            )
            QuickActionButton(
              label = "Garasi Livery",
              icon = Icons.Filled.Palette,
              tint = livery.bodyColor,
              onClick = onOpenGarage,
              modifier = Modifier.weight(1f).testTag("qa_garasi"),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            QuickActionButton(
              label = "Pengaturan & AT+MAPS",
              icon = Icons.Filled.Settings,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              onClick = { onNavigateTab(MainTab.AKUN) },
              modifier = Modifier.weight(1f).testTag("qa_settings"),
            )
          }
        }
      }

      // 7. ESP32 Hardware Accordion (Contact Switch + Matrix + Music)
      item {
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { espSectionExpanded = !espSectionExpanded }
                .testTag("esp32_accordion_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = cockpit.warning)
              Column {
                Text(
                  "Kontrol ESP32, Matrix & Musik",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  if (esp1State == ConnectionState.CONNECTED) "ESP32 #1 Terhubung"
                  else "Hubungkan ESP32 #1 untuk kontrol hardware",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Icon(
              if (espSectionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
              contentDescription = "Buka/Tutup Kontrol ESP32",
            )
          }

          AnimatedVisibility(visible = espSectionExpanded) {
            Column(
              modifier = Modifier.padding(top = 10.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              // Contact Switch with safety interlock
              val contactOn = esp1Data.contactOn == true
              HoldToConfirmButton(
                label = if (contactOn) "Tahan untuk MATIKAN Kontak" else "Tahan untuk NYALAKAN Kontak",
                containerColor = if (contactOn) cockpit.danger else cockpit.accent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onConfirmed = { viewModel.toggleMotorContact(!contactOn) },
                modifier = Modifier.fillMaxWidth().testTag("esp_contact_hold_btn"),
              )

              // Matrix LED Toggle
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Layar LED Matrix MAX7219", style = MaterialTheme.typography.bodyLarge)
                Switch(
                  checked = esp1Data.matrixOn ?: settings.matrixEnabled,
                  onCheckedChange = { on -> viewModel.toggleMatrix(on) },
                  modifier = Modifier.testTag("esp_matrix_switch"),
                )
              }

              // Music DFPlayer Controls
              Column(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = cockpit.accent)
                    Text(
                      "DFPlayer Track #${esp1Data.musicTrack} (${esp1Data.musicState})",
                      style = MaterialTheme.typography.labelLarge,
                    )
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                  IconButton(
                    onClick = { viewModel.sendMusicCommand("MUSIC_PREV") },
                    modifier = Modifier.size(48.dp).testTag("music_prev_btn"),
                  ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Lagu Sebelumnya")
                  }
                  IconButton(
                    onClick = { viewModel.sendMusicCommand("MUSIC_TOGGLE") },
                    modifier = Modifier.size(48.dp).testTag("music_toggle_btn"),
                  ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Putar / Jeda")
                  }
                  IconButton(
                    onClick = { viewModel.sendMusicCommand("MUSIC_STOP") },
                    modifier = Modifier.size(48.dp).testTag("music_stop_btn"),
                  ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Hentikan Musik")
                  }
                  IconButton(
                    onClick = { viewModel.sendMusicCommand("MUSIC_NEXT") },
                    modifier = Modifier.size(48.dp).testTag("music_next_btn"),
                  ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Lagu Berikutnya")
                  }
                }
                var volSlider by remember(esp1Data.musicVol) { mutableFloatStateOf(esp1Data.musicVol.toFloat()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Filled.VolumeUp, contentDescription = "Volume", modifier = Modifier.size(18.dp))
                  Slider(
                    value = volSlider,
                    onValueChange = { volSlider = it },
                    onValueChangeFinished = {
                      viewModel.sendMusicCommand("MUSIC_VOL:${volSlider.roundToInt()}")
                    },
                    valueRange = 0f..30f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                  )
                  Text("${volSlider.roundToInt()}", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }

      // 8. Live Trip Summary + Vehicle Health
      item {
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "Perjalanan Berjalan",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Score: ${ridingScore.score ?: "—"} (${ridingScore.label})",
              style = MaterialTheme.typography.labelMedium,
              color = cockpit.accent,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            StatTile(
              "Jarak",
              String.format(Locale.US, "%.2f", liveTrip.distanceKm),
              "km",
              modifier = Modifier.weight(1f),
            )
            StatTile(
              "Energi",
              "${liveTrip.energyWh.roundToInt()}",
              "Wh",
              modifier = Modifier.weight(1f),
            )
            StatTile(
              "Regen",
              "${liveTrip.regenWh.roundToInt()}",
              "Wh",
              accentValueColor = cockpit.regen,
              modifier = Modifier.weight(1f),
            )
            StatTile(
              "Avg",
              String.format(Locale.US, "%.1f", liveTrip.avgSpeedKmh),
              "km/h",
              modifier = Modifier.weight(1f),
            )
          }
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { viewModel.finishAndSaveTrip() },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_trip_btn"),
              colors = ButtonDefaults.buttonColors(containerColor = cockpit.accent),
            ) {
              Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Selesai & Simpan")
            }
            OutlinedButton(
              onClick = { viewModel.resetLiveTrip() },
              modifier = Modifier.heightIn(min = 48.dp).testTag("reset_trip_btn"),
            ) {
              Text("Reset")
            }
          }
        }
      }

      // 9. Maintenance & Parking & Last Trip Row
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // Parking Pin Card
          CockpitCard(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.LocationOn, contentDescription = null, tint = cockpit.info, modifier = Modifier.size(18.dp))
              Text("Lokasi Parkir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                if (settings.parkedLat != null) {
                  settings.parkedLabel.ifBlank {
                    String.format(Locale.US, "%.4f, %.4f", settings.parkedLat, settings.parkedLng)
                  }
                } else {
                  "Belum ditandai"
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedButton(
                onClick = { viewModel.pinCurrentParking() },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pin_parking_btn"),
                contentPadding = PaddingValues(horizontal = 6.dp),
              ) {
                Text("Tandai", style = MaterialTheme.typography.labelSmall)
              }
              OutlinedButton(
                onClick = { viewModel.triggerFindMyBikeBeep() },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("find_bike_btn"),
                contentPadding = PaddingValues(horizontal = 6.dp),
              ) {
                Text("Bunyikan", style = MaterialTheme.typography.labelSmall)
              }
            }
          }

          // Health / Maintenance Card
          CockpitCard(
            onClick = { onNavigateTab(MainTab.DIAGNOSIS) },
            modifier = Modifier.weight(1f),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = cockpit.accent, modifier = Modifier.size(18.dp))
              Text("Kesehatan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                if (eff.activeErrors.isEmpty()) "Motor Normal (0 Error)"
                else "${eff.activeErrors.size} Error Terdeteksi",
              style = MaterialTheme.typography.bodySmall,
              color = if (eff.activeErrors.isEmpty()) cockpit.accent else cockpit.danger,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                maintStatus?.let { "Servis: ${it.remainingKm.roundToInt()} km lagi (${it.level.badgeText})" }
                  ?: "Atur ODO servis di Akun",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // 10. Last Saved Trip Preview
      trips.firstOrNull()?.let { latest ->
        item {
          CockpitCard(
            onClick = { onNavigateTab(MainTab.STATS) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text("Trip Terakhir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                  "${latest.startLabel} -> ${latest.endLabel}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                "${String.format(Locale.US, "%.2f", latest.distanceKm)} km",
                style = MaterialTheme.typography.titleMedium,
                color = cockpit.accent,
                fontWeight = FontWeight.Bold,
              )
            }
            Spacer(Modifier.height(8.dp))
            TripPathReplayCanvas(pathJson = latest.pathJson, accentColor = cockpit.accent)
          }
        }
      }

      item { Spacer(Modifier.height(12.dp)) }
    }
  }

  if (showAlertsSheet) {
    ModalBottomSheet(onDismissRequest = { showAlertsSheet = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Pusat Notifikasi & Alarm", style = MaterialTheme.typography.titleLarge)
        if (alerts.isEmpty()) {
          Text(
            "Semua parameter baterai, suhu, dan motor dalam kondisi normal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          alerts.forEach { a ->
            CockpitCard(modifier = Modifier.fillMaxWidth()) {
              Text(a.title, style = MaterialTheme.typography.titleSmall, color = cockpit.warning)
              Text(a.message, style = MaterialTheme.typography.bodySmall)
            }
          }
        }
        Spacer(Modifier.height(20.dp))
      }
    }
  }
}

@Composable
private fun QuickActionButton(
  label: String,
  icon: ImageVector,
  tint: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.heightIn(min = 56.dp).clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Box(
        modifier =
          Modifier.size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
