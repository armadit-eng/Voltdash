package com.example.ui.screens

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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.TelemetryEngine
import com.example.model.BatterySourcePriority
import com.example.model.ConnectionState
import com.example.ui.components.CockpitCard
import com.example.ui.components.ScooterHeroCanvas
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.GarageLivery
import com.example.ui.theme.LocalCockpitColors
import com.example.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkunScreen(
  viewModel: DashboardViewModel,
  externalConnectSlot: String? = null,
  externalOpenGarage: Boolean = false,
  onExternalHandled: () -> Unit = {},
  onOpenMirrorModal: () -> Unit = {},
  onOpenAtMapsConfig: () -> Unit = {},
  onRequestBluetoothPermissions: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val mirrorActive by viewModel.mirrorActive.collectAsStateWithLifecycle()
  val mapsFramesSent by viewModel.mapsFramesSent.collectAsStateWithLifecycle()
  val mapsLastChars by viewModel.mapsLastChars.collectAsStateWithLifecycle()
  val motorState by viewModel.motorState.collectAsStateWithLifecycle()
  val jkState by viewModel.jkState.collectAsStateWithLifecycle()
  val esp1State by viewModel.esp1State.collectAsStateWithLifecycle()
  val esp2State by viewModel.esp2State.collectAsStateWithLifecycle()
  val motorDevices by viewModel.motorDevices.collectAsStateWithLifecycle()
  val jkDevices by viewModel.jkDevices.collectAsStateWithLifecycle()
  val espDevices by viewModel.espScannedDevices.collectAsStateWithLifecycle()
  val eff by viewModel.effective.collectAsStateWithLifecycle()
  val trips by viewModel.trips.collectAsStateWithLifecycle()
  val maintLogs by viewModel.maintenanceLogs.collectAsStateWithLifecycle()
  val fieldNotes by viewModel.fieldNotes.collectAsStateWithLifecycle()
  val serialLogs by viewModel.serialLogs.collectAsStateWithLifecycle()

  var activeConnectSlot by remember { mutableStateOf<String?>(null) }
  var showGarageSheet by remember { mutableStateOf(false) }
  var showMaintSheet by remember { mutableStateOf(false) }
  var showBatteryCfgSheet by remember { mutableStateOf(false) }
  var showDevLogSheet by remember { mutableStateOf(false) }
  var showProfileEdit by remember { mutableStateOf(false) }

  LaunchedEffect(externalConnectSlot, externalOpenGarage) {
    if (externalConnectSlot != null) {
      activeConnectSlot = externalConnectSlot
      onExternalHandled()
    } else if (externalOpenGarage) {
      showGarageSheet = true
      onExternalHandled()
    }
  }

  val maintStatus =
    remember(eff.odoKm, settings.maintLastOdoKm, settings.maintIntervalKm) {
      TelemetryEngine.computeMaintenanceStatus(eff.odoKm, settings)
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. Rider Profile Card
      item {
        CockpitCard(onClick = { showProfileEdit = true }, modifier = Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
              modifier = Modifier.size(52.dp).clip(CircleShape).background(cockpit.accent.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(Icons.Filled.Person, contentDescription = "Profil Rider", tint = cockpit.accent, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(settings.riderName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              val totalKm = trips.sumOf { it.distanceKm.toDouble() }
              Text(
                "${trips.size} Trip • ${String.format(Locale.US, "%.1f", totalKm)} km tercatat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text("Ubah", style = MaterialTheme.typography.labelMedium, color = cockpit.accent)
          }
        }
      }

      // 2. Koneksi Perangkat (Motor, JK-BMS, ESP32 #1, ESP32 #2)
      item {
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = cockpit.info)
            Text("Koneksi Perangkat Bluetooth & BLE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          }
          Spacer(Modifier.height(10.dp))
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceConnectionRow("Motor MID (Classic SPP / BLE)", motorState, Modifier.testTag("akun_conn_motor")) {
              onRequestBluetoothPermissions()
              activeConnectSlot = "motor"
              viewModel.motorBt.loadPairedAndScanBle()
            }
            DeviceConnectionRow("JK-BMS (BLE 0xFFE0)", jkState, Modifier.testTag("akun_conn_jk")) {
              onRequestBluetoothPermissions()
              activeConnectSlot = "jikong"
              viewModel.jkBle.scanDevices()
            }
            DeviceConnectionRow("ESP32 #1 CAN Sniffer / Matrix", esp1State, Modifier.testTag("akun_conn_esp1")) {
              onRequestBluetoothPermissions()
              activeConnectSlot = "esp1"
              viewModel.esp32Ble.scanDevices()
            }
            DeviceConnectionRow("ESP32 #2 BMS ORI (Baterai 2)", esp2State, Modifier.testTag("akun_conn_esp2")) {
              onRequestBluetoothPermissions()
              activeConnectSlot = "esp2"
              viewModel.esp32Ble.scanDevices()
            }
          }
        }
      }

      // 3. Garasi Virtual & Tema Cockpit
      item {
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.Palette, contentDescription = null, tint = cockpit.accent)
              Text("Garasi Livery & Tema Warna", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
              onClick = { showGarageSheet = true },
              modifier = Modifier.heightIn(min = 48.dp).testTag("open_garage_btn"),
            ) {
              Text("Garasi")
            }
          }
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Mode Gelap Cockpit", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = settings.darkTheme,
              onCheckedChange = { viewModel.setDarkTheme(it) },
              modifier = Modifier.testTag("dark_theme_switch"),
            )
          }
          Spacer(Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            AccentTheme.entries.forEach { t ->
              val active = settings.accentThemeId == t.id
              Box(
                modifier =
                  Modifier.size(38.dp)
                    .clip(CircleShape)
                    .background(t.primary)
                    .clickable { viewModel.setAccentTheme(t.id) }
                    .testTag("accent_${t.id}"),
                contentAlignment = Alignment.Center,
              ) {
                if (active) {
                  Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background))
                }
              }
            }
          }
        }
      }

      // 4. Servis & Maintenance Reminder
      item {
        CockpitCard(
          onClick = { showMaintSheet = true },
          modifier = Modifier.fillMaxWidth().testTag("open_maint_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.Build, contentDescription = null, tint = cockpit.warning)
              Text("Pengingat Servis Berkala", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
              maintStatus?.level?.badgeText ?: "ATUR",
              style = MaterialTheme.typography.labelMedium,
              color = cockpit.accent,
              fontWeight = FontWeight.Bold,
            )
          }
          Spacer(Modifier.height(6.dp))
          if (maintStatus != null) {
            LinearProgressIndicator(
              progress = { maintStatus.progressPct },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
              color = cockpit.accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              "Servis berikutnya ODO ${maintStatus.nextServiceOdoKm.roundToInt()} km (${maintStatus.remainingKm.roundToInt()} km lagi)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Text(
              "Ketuk untuk memasukkan ODO servis terakhir & interval servis.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // 5. Konfigurasi Baterai, Fisika & AT+NAV
      item {
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = cockpit.accent)
              Column {
                Text("Baterai, Alarm & AT+NAV", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                  "B1: ${settings.b1NominalV.roundToInt()}V ${settings.b1CapacityAh.roundToInt()}Ah • B2: ${settings.b2NominalV.roundToInt()}V ${settings.b2CapacityAh.roundToInt()}Ah",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            OutlinedButton(
              onClick = { showBatteryCfgSheet = true },
              modifier = Modifier.heightIn(min = 48.dp).testTag("open_batt_cfg_btn"),
            ) {
              Text("Atur")
            }
          }
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Prioritas Data B1", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              BatterySourcePriority.entries.forEach { p ->
                FilterChip(
                  selected = settings.batteryPriority == p,
                  onClick = { viewModel.setBatteryPriority(p) },
                  label = { Text(p.label) },
                )
              }
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Kirim Rotasi AT+NAV ke MID Motor", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = settings.atNavEnabled,
              onCheckedChange = { viewModel.setAtNavConfig(it, settings.atNavIntervalSec, settings.atNavSlots) },
            )
          }
        }
      }

      // 5b. AT+MAPS & Mirror Layar MID Card
      item {
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.Map, contentDescription = null, tint = cockpit.accent)
              Column {
                Text("AT+MAPS & Mirror Layar MID", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                  "${settings.mapsSnapWidth}x${settings.mapsSnapHeight} • $mapsFramesSent frame ($mapsLastChars chr) • " +
                    if (mirrorActive) "Mirror Aktif" else if (settings.atMapsEnabled) "AT+MAPS Siap" else "Nonaktif",
                  style = MaterialTheme.typography.bodySmall,
                  color = if (mirrorActive) cockpit.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = onOpenAtMapsConfig,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_atmaps_cfg_btn"),
            ) {
              Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Atur AT+MAPS")
            }
            Button(
              onClick = onOpenMirrorModal,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_mirror_modal_btn"),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = if (mirrorActive) cockpit.danger else cockpit.accent,
                ),
            ) {
              Icon(Icons.Filled.Cast, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (mirrorActive) "Mirror Aktif" else "Mirror Layar")
            }
          }
        }
      }

      // 6. Catatan Lapangan
      item {
        var noteTitle by remember { mutableStateOf("") }
        var noteBody by remember { mutableStateOf("") }
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.NoteAdd, contentDescription = null, tint = cockpit.info)
            Text("Catatan Lapangan (${fieldNotes.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          }
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = noteTitle,
            onValueChange = { noteTitle = it },
            label = { Text("Judul catatan") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(6.dp))
          OutlinedTextField(
            value = noteBody,
            onValueChange = { noteBody = it },
            label = { Text("Isi observasi / kondisi motor") },
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          Button(
            onClick = {
              viewModel.addFieldNote(noteTitle, noteBody)
              noteTitle = ""
              noteBody = ""
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
          ) {
            Text("Simpan Catatan")
          }
          fieldNotes.take(3).forEach { n ->
            Spacer(Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(n.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(n.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              IconButton(onClick = { viewModel.deleteFieldNote(n) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus Catatan", tint = cockpit.danger)
              }
            }
          }
        }
      }

      // 7. Developer Serial Log & AT Command Console
      item {
        CockpitCard(
          onClick = { showDevLogSheet = true },
          modifier = Modifier.fillMaxWidth().testTag("open_dev_log_btn"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.Code, contentDescription = null, tint = cockpit.warning)
              Text("Developer BLE Serial Log & AT Console", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text("${serialLogs.size}", style = MaterialTheme.typography.labelMedium, color = cockpit.accent)
          }
        }
      }

      item { Spacer(Modifier.height(14.dp)) }
    }
  }

  // Bluetooth / BLE Connection Sheet
  activeConnectSlot?.let { slot ->
    ModalBottomSheet(onDismissRequest = { activeConnectSlot = null }) {
      val list =
        when (slot) {
          "motor" -> motorDevices
          "jikong" -> jkDevices
          else -> espDevices
        }
      val curState =
        when (slot) {
          "motor" -> motorState
          "jikong" -> jkState
          "esp1" -> esp1State
          else -> esp2State
        }
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              when (slot) {
                "motor" -> "Pilih Perangkat Motor"
                "jikong" -> "Pilih JK-BMS BLE"
                "esp1" -> "Pilih ESP32 #1"
                else -> "Pilih ESP32 #2 (BMS ORI)"
              },
            style = MaterialTheme.typography.titleLarge,
          )
          if (curState == ConnectionState.CONNECTED) {
            OutlinedButton(
              onClick = {
                when (slot) {
                  "motor" -> viewModel.motorBt.disconnect()
                  "jikong" -> viewModel.jkBle.disconnect()
                  "esp1" -> viewModel.esp32Ble.disconnectSlot(1)
                  "esp2" -> viewModel.esp32Ble.disconnectSlot(2)
                }
              },
              modifier = Modifier.heightIn(min = 48.dp),
            ) {
              Text("Putuskan")
            }
          }
        }
        Button(
          onClick = {
            onRequestBluetoothPermissions()
            when (slot) {
              "motor" -> viewModel.motorBt.loadPairedAndScanBle()
              "jikong" -> viewModel.jkBle.scanDevices()
              else -> viewModel.esp32Ble.scanDevices()
            }
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
          Text("Scan Ulang Perangkat")
        }

        if (list.isEmpty()) {
          Text(
            "Belum ada perangkat terdeteksi. Pastikan Bluetooth aktif & izin diberikan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
          ) {
            items(list.size) { i ->
              val dev = list[i]
              CockpitCard(
                onClick = {
                  when (slot) {
                    "motor" -> viewModel.motorBt.connect(dev.address, dev.isBle)
                    "jikong" -> viewModel.jkBle.connect(dev.address)
                    "esp1" -> viewModel.esp32Ble.connectSlot(1, dev.address)
                    "esp2" -> viewModel.esp32Ble.connectSlot(2, dev.address)
                  }
                  activeConnectSlot = null
                },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Column {
                    Text(dev.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                      "${dev.address} • ${if (dev.isPaired) "Paired" else "BLE Scan"}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Text("Hubungkan", style = MaterialTheme.typography.labelMedium, color = cockpit.accent)
                }
              }
            }
          }
        }
      }
    }
  }

  // Virtual Garage Livery Sheet
  if (showGarageSheet) {
    ModalBottomSheet(onDismissRequest = { showGarageSheet = false }) {
      val curLivery = GarageLivery.fromId(settings.garageLiveryId)
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Garasi Virtual GESITS", style = MaterialTheme.typography.titleLarge)
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(160.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
          ScooterHeroCanvas(livery = curLivery, isCharging = false, isRegen = false, modifier = Modifier.fillMaxSize())
        }
        Text("${curLivery.displayName} — ${curLivery.description}", style = MaterialTheme.typography.bodySmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          GarageLivery.entries.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              pair.forEach { liv ->
                val sel = liv.id == curLivery.id
                Surface(
                  modifier =
                    Modifier.weight(1f)
                      .heightIn(min = 48.dp)
                      .clickable { viewModel.setGarageLivery(liv.id) }
                      .testTag("garage_${liv.id}"),
                  shape = RoundedCornerShape(12.dp),
                  color = liv.bodyColor.copy(alpha = if (sel) 0.22f else 0.08f),
                  border = BorderStroke(if (sel) 2.dp else 1.dp, liv.bodyColor.copy(alpha = if (sel) 1f else 0.4f)),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(liv.bodyColor))
                    Text(liv.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }
        Spacer(Modifier.height(18.dp))
      }
    }
  }

  // Maintenance Modal Sheet
  if (showMaintSheet) {
    ModalBottomSheet(onDismissRequest = { showMaintSheet = false }) {
      var odoText by remember(settings.maintLastOdoKm) {
        mutableStateOf(if (settings.maintLastOdoKm > 0f) settings.maintLastOdoKm.roundToInt().toString() else "")
      }
      var intText by remember(settings.maintIntervalKm) {
        mutableStateOf(settings.maintIntervalKm.roundToInt().toString())
      }
      var noteText by remember { mutableStateOf("") }
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Pengingat & Catatan Servis", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
          value = odoText,
          onValueChange = { odoText = it },
          label = { Text("ODO Servis Terakhir (km)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = intText,
          onValueChange = { intText = it },
          label = { Text("Interval Servis (km)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = noteText,
          onValueChange = { noteText = it },
          label = { Text("Catatan komponen (opsional, mis. Kampas rem, V-Belt)") },
          modifier = Modifier.fillMaxWidth(),
        )
        Button(
          onClick = {
            val odo = odoText.toFloatOrNull() ?: eff.odoKm ?: 0f
            val interval = intText.toFloatOrNull() ?: 2000f
            viewModel.saveMaintenance(odo, interval, noteText)
            showMaintSheet = false
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
          colors = ButtonDefaults.buttonColors(containerColor = cockpit.accent),
        ) {
          Text("Simpan Jadwal Servis")
        }
        if (maintLogs.isNotEmpty()) {
          val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
          maintLogs.take(4).forEach { m ->
            Text(
              "• ${sdf.format(Date(m.timestampMs))} (${m.odoKm.roundToInt()} km): ${m.note}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }

  // Battery & Physics Config Sheet
  if (showBatteryCfgSheet) {
    ModalBottomSheet(onDismissRequest = { showBatteryCfgSheet = false }) {
      var b1V by remember(settings.b1NominalV) { mutableStateOf(settings.b1NominalV.roundToInt().toString()) }
      var b1Ah by remember(settings.b1CapacityAh) { mutableStateOf(settings.b1CapacityAh.roundToInt().toString()) }
      var b2V by remember(settings.b2NominalV) { mutableStateOf(settings.b2NominalV.roundToInt().toString()) }
      var b2Ah by remember(settings.b2CapacityAh) { mutableStateOf(settings.b2CapacityAh.roundToInt().toString()) }
      var refWh by remember(settings.refWhPerKm) { mutableStateOf(settings.refWhPerKm.roundToInt().toString()) }
      var goalKm by remember(settings.weeklyGoalKm) { mutableStateOf(settings.weeklyGoalKm.roundToInt().toString()) }

      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("Konfigurasi Baterai & Target", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = b1V, onValueChange = { b1V = it }, label = { Text("B1 Volt") }, modifier = Modifier.weight(1f))
          OutlinedTextField(value = b1Ah, onValueChange = { b1Ah = it }, label = { Text("B1 Ah") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = b2V, onValueChange = { b2V = it }, label = { Text("B2 Volt") }, modifier = Modifier.weight(1f))
          OutlinedTextField(value = b2Ah, onValueChange = { b2Ah = it }, label = { Text("B2 Ah") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = refWh, onValueChange = { refWh = it }, label = { Text("Ref Wh/km") }, modifier = Modifier.weight(1f))
          OutlinedTextField(value = goalKm, onValueChange = { goalKm = it }, label = { Text("Target Mingguan km") }, modifier = Modifier.weight(1f))
        }
        Button(
          onClick = {
            viewModel.setBatterySpecs(
              b1V.toFloatOrNull() ?: 72f,
              b1Ah.toFloatOrNull() ?: 20f,
              b2V.toFloatOrNull() ?: 72f,
              b2Ah.toFloatOrNull() ?: 20f,
              refWh.toFloatOrNull() ?: 28f,
            )
            viewModel.setWeeklyGoalKm(goalKm.toFloatOrNull() ?: 100f)
            showBatteryCfgSheet = false
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
          colors = ButtonDefaults.buttonColors(containerColor = cockpit.accent),
        ) {
          Text("Simpan Konfigurasi")
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }

  // Rider Profile Edit Sheet
  if (showProfileEdit) {
    ModalBottomSheet(onDismissRequest = { showProfileEdit = false }) {
      var nameInput by remember(settings.riderName) { mutableStateOf(settings.riderName) }
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Edit Nama Rider", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
          value = nameInput,
          onValueChange = { nameInput = it },
          label = { Text("Nama tampilan") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Button(
          onClick = {
            viewModel.updateRiderName(nameInput)
            showProfileEdit = false
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
          Text("Simpan")
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }

  // Developer Serial Log & AT Command Console Sheet
  if (showDevLogSheet) {
    ModalBottomSheet(onDismissRequest = { showDevLogSheet = false }) {
      var atCmd by remember { mutableStateOf("AT+SPD?") }
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("BLE Serial Log & AT Console", style = MaterialTheme.typography.titleLarge)
          OutlinedButton(onClick = { viewModel.clearSerialLogs() }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Bersihkan")
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = atCmd,
            onValueChange = { atCmd = it },
            label = { Text("Perintah AT / ESP32") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          Button(
            onClick = { viewModel.motorBt.sendCommand(atCmd) },
            modifier = Modifier.heightIn(min = 52.dp),
          ) {
            Text("Kirim")
          }
        }
        LazyColumn(
          modifier =
            Modifier.fillMaxWidth()
              .height(220.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(8.dp),
        ) {
          items(serialLogs.size) { idx ->
            val entry = serialLogs[serialLogs.lastIndex - idx]
            Text(
              text = "[${entry.source}] ${entry.message}",
              style = MaterialTheme.typography.bodySmall,
              color = if (entry.isError) cockpit.danger else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun DeviceConnectionRow(
  title: String,
  state: ConnectionState,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val cockpit = LocalCockpitColors.current
  val statusText =
    when (state) {
      ConnectionState.CONNECTED -> "Terhubung"
      ConnectionState.CONNECTING -> "Menghubungkan…"
      ConnectionState.SCANNING -> "Memindai…"
      ConnectionState.DISCONNECTED -> "Ketuk untuk hubungkan"
    }
  val statusColor =
    when (state) {
      ConnectionState.CONNECTED -> cockpit.accent
      ConnectionState.CONNECTING,
      ConnectionState.SCANNING -> cockpit.warning
      ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Surface(
    modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    border =
      BorderStroke(
        1.dp,
        if (state == ConnectionState.CONNECTED) cockpit.accent.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
      ),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
      }
      Icon(
        Icons.Filled.Settings,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}
