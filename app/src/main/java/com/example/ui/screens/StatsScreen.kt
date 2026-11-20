package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.TripEntity
import com.example.domain.TelemetryEngine
import com.example.model.DailyBarData
import com.example.model.GM_BADGES
import com.example.model.GM_LEVELS
import com.example.model.GamificationStatsSnapshot
import com.example.ui.components.CockpitCard
import com.example.ui.components.DailyStatsBarCanvas
import com.example.ui.components.StatTile
import com.example.ui.components.TripPathReplayCanvas
import com.example.ui.theme.LocalCockpitColors
import com.example.viewmodel.DashboardViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
  viewModel: DashboardViewModel,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  val context = LocalContext.current
  val trips by viewModel.trips.collectAsStateWithLifecycle()
  val chargeLogs by viewModel.chargeLogs.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()

  var viewMode by remember { mutableIntStateOf(0) } // 0 = Daftar, 1 = Kalender
  var selectedTrip by remember { mutableStateOf<TripEntity?>(null) }
  var calMonthOffset by remember { mutableIntStateOf(0) }
  var selectedDayKey by remember { mutableStateOf<String?>(null) }

  val totalKm = remember(trips) { trips.sumOf { it.distanceKm.toDouble() } }
  val totalWh = remember(trips) { trips.sumOf { it.energyWh.toDouble() } }
  val totalRegenWh = remember(trips) { trips.sumOf { it.regenWh.toDouble() } }
  val savings = remember(totalKm, totalWh) { TelemetryEngine.computeFuelSavings(totalKm, totalWh) }

  val gamificationSnap =
    remember(trips, chargeLogs) {
      val scored = trips.mapNotNull { it.ridingScore }
      GamificationStatsSnapshot(
        tripCount = trips.size,
        totalKm = totalKm,
        ecoTrips = trips.count { it.efficiencyWhKm > 0f && it.efficiencyWhKm < 40f },
        avgScore = if (scored.isNotEmpty()) scored.average() else 0.0,
        totalRegenWh = totalRegenWh,
        chargeCount = chargeLogs.size,
      )
    }

  val points =
    remember(trips) {
      trips.sumOf { (it.ridingScore ?: 0) + (it.distanceKm * 2f).roundToInt() }
    }
  val currentLevel =
    remember(points) {
      GM_LEVELS.firstOrNull { points in it.minPoints until it.maxPoints } ?: GM_LEVELS.last()
    }

  // Last 7 days bar data
  val dailyBars =
    remember(trips) {
      val dayFmt = SimpleDateFormat("EEE", Locale("id", "ID"))
      val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val cal = Calendar.getInstance()
      (6 downTo 0).map { offset ->
        val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
        val key = keyFmt.format(c.time)
        val dayTrips = trips.filter { keyFmt.format(Date(it.startTimeMs)) == key }
        DailyBarData(
          dayLabel = dayFmt.format(c.time).take(3),
          distanceKm = dayTrips.sumOf { it.distanceKm.toDouble() },
          energyWh = dayTrips.sumOf { it.energyWh.toDouble() },
          avgScore = dayTrips.mapNotNull { it.ridingScore }.let { if (it.isEmpty()) 0 else it.average().roundToInt() },
        )
      }
    }

  val dateKeyFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
  val dateLabelFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }
  val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. Summary & Fuel Savings
      item {
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Text("Statistik & Penghematan BBM", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(8.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Total Jarak", String.format(Locale.US, "%.1f", totalKm), "km", cockpit.accent, Modifier.weight(1f))
            StatTile("Total Trip", "${trips.size}", "kali", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            StatTile("CO2 Hemat", String.format(Locale.US, "%.1f", savings.co2SavedKg), "kg", cockpit.info, Modifier.weight(1f))
          }
          Spacer(Modifier.height(8.dp))
          val rpFmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
          Text(
            text = "Hemat Rp ${rpFmt.format(savings.netSavingsRp)} dibanding motor bensin (40 km/L)",
            style = MaterialTheme.typography.bodySmall,
            color = cockpit.accent,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      // 2. Weekly Distance Goal & 7-day Chart
      item {
        val weekKm = dailyBars.sumOf { it.distanceKm }.toFloat()
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Target Mingguan & Grafik 7 Hari", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
              "${String.format(Locale.US, "%.1f", weekKm)} / ${settings.weeklyGoalKm.roundToInt()} km",
              style = MaterialTheme.typography.labelMedium,
              color = cockpit.accent,
            )
          }
          Spacer(Modifier.height(6.dp))
          LinearProgressIndicator(
            progress = { (weekKm / settings.weeklyGoalKm).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = cockpit.accent,
          )
          Spacer(Modifier.height(10.dp))
          DailyStatsBarCanvas(bars = dailyBars, accentColor = cockpit.accent)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            dailyBars.forEach { b ->
              Text(b.dayLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }

      // 3. Gamification & Badges
      item {
        CockpitCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = cockpit.warning)
              Column {
                Text("Level: ${currentLevel.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("$points Poin Eco", style = MaterialTheme.typography.bodySmall, color = cockpit.accent)
              }
            }
            OutlinedButton(
              onClick = { viewModel.logManualCharge() },
              modifier = Modifier.heightIn(min = 48.dp).testTag("log_charge_btn"),
            ) {
              Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Catat Cas (${chargeLogs.size})")
            }
          }
          Spacer(Modifier.height(8.dp))
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GM_BADGES.chunked(3).forEach { row ->
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { b ->
                  val unlocked = b.check(gamificationSnap)
                  Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color =
                      if (unlocked) cockpit.accent.copy(alpha = 0.14f)
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border =
                      BorderStroke(
                        1.dp,
                        if (unlocked) cockpit.accent.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                      ),
                  ) {
                    Column(
                      modifier = Modifier.padding(8.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                      Text(
                        b.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (unlocked) cockpit.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                      )
                      Text(
                        b.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 4. Riwayat Perjalanan Header + List/Calendar Switch
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Riwayat Perjalanan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
              selected = viewMode == 0,
              onClick = { viewMode = 0 },
              shape = SegmentedButtonDefaults.itemShape(0, 2),
              icon = { Icon(Icons.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp)) },
              modifier = Modifier.testTag("history_mode_list"),
            ) {
              Text("Daftar")
            }
            SegmentedButton(
              selected = viewMode == 1,
              onClick = { viewMode = 1 },
              shape = SegmentedButtonDefaults.itemShape(1, 2),
              icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
              modifier = Modifier.testTag("history_mode_calendar"),
            ) {
              Text("Kalender")
            }
          }
        }
      }

      if (viewMode == 1) {
        // Monthly Calendar Grid
        item {
          val cal =
            remember(calMonthOffset) {
              Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, calMonthOffset)
              }
            }
          val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale("id", "ID")) }
          val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
          val firstDowOffset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
          val year = cal.get(Calendar.YEAR)
          val month = cal.get(Calendar.MONTH)

          val tripsByDay =
            remember(trips) { trips.groupBy { dateKeyFmt.format(Date(it.startTimeMs)) } }

          CockpitCard(modifier = Modifier.fillMaxWidth().testTag("trip_calendar_card")) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(onClick = { calMonthOffset-- }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Bulan Sebelumnya")
              }
              Text(monthFmt.format(cal.time), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
              IconButton(onClick = { calMonthOffset++ }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Bulan Berikutnya")
              }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
              listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { d ->
                Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Spacer(Modifier.height(6.dp))
            val totalCells = firstDowOffset + daysInMonth
            val rows = (totalCells + 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  for (c in 0 until 7) {
                    val cellIdx = r * 7 + c
                    val dayNum = cellIdx - firstDowOffset + 1
                    if (dayNum in 1..daysInMonth) {
                      val dayCal =
                        Calendar.getInstance().apply { set(year, month, dayNum) }
                      val key = dateKeyFmt.format(dayCal.time)
                      val count = tripsByDay[key]?.size ?: 0
                      val isSelected = selectedDayKey == key
                      Box(
                        modifier =
                          Modifier.weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                              when {
                                isSelected -> cockpit.accent.copy(alpha = 0.28f)
                                count > 0 -> cockpit.accent.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                              }
                            )
                            .border(
                              1.dp,
                              if (isSelected) cockpit.accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                              RoundedCornerShape(10.dp),
                            )
                            .clickable {
                              selectedDayKey = if (selectedDayKey == key) null else key
                            },
                        contentAlignment = Alignment.Center,
                      ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                          Text(
                            "$dayNum",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
                          )
                          if (count > 0) {
                            Box(
                              modifier =
                                Modifier.size(5.dp).clip(CircleShape).background(cockpit.accent)
                            )
                          }
                        }
                      }
                    } else {
                      Spacer(Modifier.weight(1f))
                    }
                  }
                }
              }
            }
          }
        }
      }

      val displayedTrips =
        if (viewMode == 1 && selectedDayKey != null) {
          trips.filter { dateKeyFmt.format(Date(it.startTimeMs)) == selectedDayKey }
        } else {
          trips
        }

      if (displayedTrips.isEmpty()) {
        item {
          CockpitCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              if (viewMode == 1 && selectedDayKey != null) "Tidak ada perjalanan pada tanggal $selectedDayKey."
              else "Belum ada riwayat perjalanan tersimpan. Gunakan tombol 'Selesai & Simpan' di layar Home setelah berkendara.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        items(displayedTrips, key = { it.id }) { trip ->
          CockpitCard(
            onClick = { selectedTrip = trip },
            modifier = Modifier.fillMaxWidth().testTag("trip_item_${trip.id}"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${dateLabelFmt.format(Date(trip.startTimeMs))} • ${timeFmt.format(Date(trip.startTimeMs))} - ${timeFmt.format(Date(trip.endTimeMs))}",
                  style = MaterialTheme.typography.labelSmall,
                  color = cockpit.accent,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "${trip.startLabel} -> ${trip.endLabel}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
              Text(
                text = "${String.format(Locale.US, "%.2f", trip.distanceKm)} km",
                style = MaterialTheme.typography.titleMedium,
                color = cockpit.accent,
                fontWeight = FontWeight.Bold,
              )
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(
                "Rata² ${String.format(Locale.US, "%.1f", trip.avgSpeedKmh)} km/h • Max ${trip.maxSpeedKmh.roundToInt()} km/h",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                "${trip.energyWh.roundToInt()} Wh • Score ${trip.ridingScore ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      item { Spacer(Modifier.height(12.dp)) }
    }
  }

  // Trip Detail & Export Sheet
  selectedTrip?.let { trip ->
    ModalBottomSheet(onDismissRequest = { selectedTrip = null }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("${trip.startLabel} -> ${trip.endLabel}", style = MaterialTheme.typography.titleLarge)
        Text(
          "${dateLabelFmt.format(Date(trip.startTimeMs))} • ${timeFmt.format(Date(trip.startTimeMs))} - ${timeFmt.format(Date(trip.endTimeMs))}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TripPathReplayCanvas(pathJson = trip.pathJson, accentColor = cockpit.accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatTile("Jarak", String.format(Locale.US, "%.2f", trip.distanceKm), "km", cockpit.accent, Modifier.weight(1f))
          StatTile("Avg Speed", String.format(Locale.US, "%.1f", trip.avgSpeedKmh), "km/h", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
          StatTile("Efisiensi", String.format(Locale.US, "%.1f", trip.efficiencyWhKm), "Wh/km", cockpit.info, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatTile("Energi", "${trip.energyWh.roundToInt()}", "Wh", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
          StatTile("Regen", "${trip.regenWh.roundToInt()}", "Wh", cockpit.regen, Modifier.weight(1f))
          StatTile("Score", "${trip.ridingScore ?: "—"}", "", cockpit.warning, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              val csv = viewModel.buildTripCsv(trip)
              val send =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/csv"
                  putExtra(Intent.EXTRA_TEXT, csv)
                  putExtra(Intent.EXTRA_SUBJECT, "GESITS Trip CSV")
                }
              context.startActivity(Intent.createChooser(send, "Bagikan CSV Trip"))
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = cockpit.accent),
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Ekspor CSV")
          }
          OutlinedButton(
            onClick = {
              val gpx = viewModel.buildTripGpx(trip)
              val send =
                Intent(Intent.ACTION_SEND).apply {
                  type = "application/gpx+xml"
                  putExtra(Intent.EXTRA_TEXT, gpx)
                  putExtra(Intent.EXTRA_SUBJECT, "GESITS Trip GPX")
                }
              context.startActivity(Intent.createChooser(send, "Bagikan GPX Trip"))
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          ) {
            Text("Ekspor GPX")
          }
          IconButton(
            onClick = {
              viewModel.deleteTrip(trip)
              selectedTrip = null
            },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Filled.Delete, contentDescription = "Hapus Trip", tint = cockpit.danger)
          }
        }
        Spacer(Modifier.height(18.dp))
      }
    }
  }
}
