package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ConnectionState
import com.example.ui.theme.LocalCockpitColors
import com.example.viewmodel.DashboardViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorModalSheet(
  viewModel: DashboardViewModel,
  mirrorActive: Boolean,
  onRequestScreenCapture: () -> Unit,
  onDismiss: () -> Unit,
) {
  val cockpit = LocalCockpitColors.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val motorState by viewModel.motorState.collectAsStateWithLifecycle()
  val previewBmp by viewModel.mapsPreviewBitmap.collectAsStateWithLifecycle()
  val framesSent by viewModel.mapsFramesSent.collectAsStateWithLifecycle()
  val lastChars by viewModel.mapsLastChars.collectAsStateWithLifecycle()
  val lastWriteMs by viewModel.mapsLastWriteMs.collectAsStateWithLifecycle()

  var leftPct by remember(settings.mirrorLeftPct) { mutableFloatStateOf(settings.mirrorLeftPct.toFloat()) }
  var rightPct by remember(settings.mirrorRightPct) { mutableFloatStateOf(settings.mirrorRightPct.toFloat()) }
  var topPct by remember(settings.mirrorTopPct) { mutableFloatStateOf(settings.mirrorTopPct.toFloat()) }
  var bottomPct by remember(settings.mirrorBottomPct) { mutableFloatStateOf(settings.mirrorBottomPct.toFloat()) }
  var stripEnabled by remember(settings.mirrorStripEnabled) { mutableStateOf(settings.mirrorStripEnabled) }
  var stripPos by remember(settings.mirrorStripPos) { mutableStateOf(settings.mirrorStripPos) }

  fun commitMirrorSettings() {
    val l = leftPct.roundToInt().coerceIn(0, 89)
    val r = rightPct.roundToInt().coerceIn(l + 5, 100)
    val t = topPct.roundToInt().coerceIn(0, 89)
    val b = bottomPct.roundToInt().coerceIn(t + 5, 100)
    viewModel.setMirrorCropAndStrip(l, r, t, b, stripEnabled, stripPos)
  }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.Cast, contentDescription = null, tint = cockpit.accent)
          Column {
            Text("Mirror Layar HP -> MID", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
              "Target ${settings.mapsSnapWidth}x${settings.mapsSnapHeight} px • Maks ${settings.mapsMaxChars} chr",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Filled.Close, contentDescription = "Tutup")
        }
      }

      if (motorState != ConnectionState.CONNECTED) {
        CockpitCard(
          borderColor = cockpit.warning.copy(alpha = 0.65f),
          containerColor = cockpit.warning.copy(alpha = 0.1f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = cockpit.warning, modifier = Modifier.size(20.dp))
            Text(
              "Motor MID belum terhubung. Mirror tetap menampilkan pratinjau lokal, hubungkan Motor untuk kirim AT+MAPS.",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }

      // Live Preview + Crop Box Diagram Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Phone Crop Area Visual
        Surface(
          modifier = Modifier.width(86.dp).height(132.dp),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val l = (leftPct / 100f) * size.width
            val r = (rightPct / 100f) * size.width
            val t = (topPct / 100f) * size.height
            val b = (bottomPct / 100f) * size.height
            drawRect(
              color = cockpit.accent.copy(alpha = 0.25f),
              topLeft = Offset(l, t),
              size = Size((r - l).coerceAtLeast(4f), (b - t).coerceAtLeast(4f)),
            )
            drawRect(
              color = cockpit.accent,
              topLeft = Offset(l, t),
              size = Size((r - l).coerceAtLeast(4f), (b - t).coerceAtLeast(4f)),
              style = Stroke(width = 3f),
            )
          }
        }

        // Live Encoded MID Frame Preview
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          val aspect =
            (settings.mapsSnapWidth.toFloat() / settings.mapsSnapHeight.coerceAtLeast(1)).coerceIn(0.8f, 2.5f)
          Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0A1520),
            border = BorderStroke(1.dp, if (mirrorActive) cockpit.accent else MaterialTheme.colorScheme.outline),
          ) {
            val bmp = previewBmp
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Pratinjau frame MID",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(10.dp)) {
                Text(
                  "Tekan Mulai untuk menangkap layar Google Maps / Aplikasi Navigasi",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color(0xFFB8C7D1),
                )
              }
            }
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              "Terkirim: $framesSent frame",
              style = MaterialTheme.typography.labelSmall,
              color = cockpit.accent,
            )
            Text(
              "$lastChars chr • ${lastWriteMs}ms",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Crop Sliders
      CockpitCard(modifier = Modifier.fillMaxWidth()) {
        Text("Area Crop Layar HP", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        CropSliderRow("Kiri", leftPct)
        Slider(
          value = leftPct,
          onValueChange = { leftPct = it.coerceAtMost(rightPct - 5f) },
          onValueChangeFinished = { commitMirrorSettings() },
          valueRange = 0f..89f,
        )

        CropSliderRow("Kanan", rightPct)
        Slider(
          value = rightPct,
          onValueChange = { rightPct = it.coerceAtLeast(leftPct + 5f) },
          onValueChangeFinished = { commitMirrorSettings() },
          valueRange = 5f..100f,
        )

        CropSliderRow("Atas", topPct)
        Slider(
          value = topPct,
          onValueChange = { topPct = it.coerceAtMost(bottomPct - 5f) },
          onValueChangeFinished = { commitMirrorSettings() },
          valueRange = 0f..89f,
        )

        CropSliderRow("Bawah", bottomPct)
        Slider(
          value = bottomPct,
          onValueChange = { bottomPct = it.coerceAtLeast(topPct + 5f) },
          onValueChangeFinished = { commitMirrorSettings() },
          valueRange = 5f..100f,
        )
      }

      // Direction & ETA Strip Option
      CockpitCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Strip info arah & ETA di frame", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
              "Menampilkan jarak belokan, instruksi / kecepatan, dan SOC pada hasil mirror.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = stripEnabled,
            onCheckedChange = {
              stripEnabled = it
              commitMirrorSettings()
            },
            modifier = Modifier.testTag("mirror_strip_switch"),
          )
        }
        if (stripEnabled) {
          Spacer(Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = stripPos == "top",
              onClick = {
                stripPos = "top"
                commitMirrorSettings()
              },
              label = { Text("Posisi Atas") },
            )
            FilterChip(
              selected = stripPos == "bottom",
              onClick = {
                stripPos = "bottom"
                commitMirrorSettings()
              },
              label = { Text("Posisi Bawah") },
            )
          }
        }
      }

      // Start / Stop Mirror Action Button
      Button(
        onClick = {
          commitMirrorSettings()
          if (mirrorActive) {
            viewModel.stopScreenMirror()
          } else {
            onRequestScreenCapture()
          }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("mirror_toggle_btn"),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (mirrorActive) cockpit.danger else cockpit.accent,
          ),
      ) {
        Icon(
          if (mirrorActive) Icons.Filled.Stop else Icons.Filled.Cast,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          if (mirrorActive) "Hentikan Mirror Layar" else "Mulai Mirror Layar",
          fontWeight = FontWeight.Bold,
        )
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun CropSliderRow(
  label: String,
  value: Float,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Text(
      "${value.roundToInt()}%",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = LocalCockpitColors.current.accent,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtMapsConfigSheet(
  viewModel: DashboardViewModel,
  onDismiss: () -> Unit,
) {
  val cockpit = LocalCockpitColors.current
  val context = LocalContext.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val previewBmp by viewModel.mapsPreviewBitmap.collectAsStateWithLifecycle()
  val previewChars by viewModel.mapsPreviewChars.collectAsStateWithLifecycle()
  val framesSent by viewModel.mapsFramesSent.collectAsStateWithLifecycle()

  var enabled by remember(settings.atMapsEnabled) { mutableStateOf(settings.atMapsEnabled) }
  var layout by remember(settings.mapsLayout) { mutableStateOf(settings.mapsLayout) }
  var riderPos by remember(settings.mapsRiderPos) { mutableStateOf(settings.mapsRiderPos) }
  var widthText by remember(settings.mapsSnapWidth) { mutableStateOf(settings.mapsSnapWidth.toString()) }
  var heightText by remember(settings.mapsSnapHeight) { mutableStateOf(settings.mapsSnapHeight.toString()) }
  var maxCharsText by remember(settings.mapsMaxChars) { mutableStateOf(settings.mapsMaxChars.toString()) }
  var leChunkText by remember(settings.leMapsChunk) { mutableStateOf(settings.leMapsChunk.toString()) }
  var leDelayText by remember(settings.leMapsDelayMs) { mutableStateOf(settings.leMapsDelayMs.toString()) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        try {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        } catch (_: Exception) {}
        viewModel.setCustomWallpaper(uri.toString(), true)
      }
    }

  fun saveConfig() {
    viewModel.setAtMapsConfig(
      enabled = enabled,
      width = widthText.toIntOrNull() ?: 350,
      height = heightText.toIntOrNull() ?: 220,
      maxChars = maxCharsText.toIntOrNull() ?: 7000,
      layout = layout,
      riderPos = riderPos,
      leChunk = leChunkText.toIntOrNull() ?: 100,
      leDelayMs = leDelayText.toIntOrNull() ?: 30,
    )
  }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.Map, contentDescription = null, tint = cockpit.accent)
          Text("Pengaturan AT+MAPS MID", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Filled.Close, contentDescription = "Tutup")
        }
      }

      CockpitCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Stream Peta Live (AT+MAPS)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
              "Otomatis kirim frame JPEG Base64 setiap 1 dtk (navigasi) / 3 dtk (idle).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = enabled,
            onCheckedChange = {
              enabled = it
              saveConfig()
            },
            modifier = Modifier.testTag("atmaps_enable_switch"),
          )
        }
      }

      // Live Preview Card
      CockpitCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Pratinjau Frame MID", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(
            "$previewChars / ${settings.mapsMaxChars} chr • $framesSent terkirim",
            style = MaterialTheme.typography.labelSmall,
            color = cockpit.accent,
          )
        }
        Spacer(Modifier.height(8.dp))
        val aspect =
          (settings.mapsSnapWidth.toFloat() / settings.mapsSnapHeight.coerceAtLeast(1)).coerceIn(0.8f, 2.5f)
        Surface(
          modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF0A1520),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          val bmp = previewBmp
          if (bmp != null) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = "Pratinjau frame AT+MAPS",
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(12.dp)) {
              Text(
                "Tekan 'Uji & Kirim Frame' di bawah untuk melihat pratinjau peta / wallpaper MID.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB8C7D1),
              )
            }
          }
        }
      }

      // Layout & Rider Pos
      CockpitCard(modifier = Modifier.fillMaxWidth()) {
        Text("Tata Letak Peta MID", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = layout == "full",
            onClick = {
              layout = "full"
              saveConfig()
            },
            label = { Text("Peta Penuh") },
          )
          FilterChip(
            selected = layout == "split",
            onClick = {
              layout = "split"
              saveConfig()
            },
            label = { Text("Split (Peta + Info)") },
          )
        }
        Spacer(Modifier.height(6.dp))
        Text("Posisi Ikon Motor", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = riderPos == "low",
            onClick = {
              riderPos = "low"
              saveConfig()
            },
            label = { Text("Agak ke Bawah") },
          )
          FilterChip(
            selected = riderPos == "center",
            onClick = {
              riderPos = "center"
              saveConfig()
            },
            label = { Text("Tengah Layar") },
          )
        }
      }

      // Custom Wallpaper Picker (Zero-permission Photo Picker)
      CockpitCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Image, contentDescription = null, tint = cockpit.info)
            Text("Wallpaper Kustom MID", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          }
          Switch(
            checked = settings.customWallpaperEnabled,
            onCheckedChange = { on ->
              viewModel.setCustomWallpaper(settings.customWallpaperUri, on)
            },
            enabled = settings.customWallpaperUri.isNotBlank(),
            modifier = Modifier.testTag("atmaps_wallpaper_switch"),
          )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("atmaps_pick_wallpaper_btn"),
          ) {
            Text(if (settings.customWallpaperUri.isBlank()) "Pilih Gambar" else "Ganti Gambar")
          }
          if (settings.customWallpaperUri.isNotBlank()) {
            IconButton(
              onClick = { viewModel.setCustomWallpaper("", false) },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(Icons.Filled.Delete, contentDescription = "Hapus Wallpaper", tint = cockpit.danger)
            }
          }
        }
      }

      // Resolution & BLE Chunk Parameters
      CockpitCard(modifier = Modifier.fillMaxWidth()) {
        Text("Resolusi Snapshot & Chunk BLE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = widthText,
            onValueChange = { widthText = it },
            label = { Text("Lebar px") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = heightText,
            onValueChange = { heightText = it },
            label = { Text("Tinggi px") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = maxCharsText,
            onValueChange = { maxCharsText = it },
            label = { Text("Maks Chr") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = leChunkText,
            onValueChange = { leChunkText = it },
            label = { Text("Chunk LE (chr)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = leDelayText,
            onValueChange = { leDelayText = it },
            label = { Text("Jeda LE (ms)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
          onClick = {
            saveConfig()
            viewModel.showToast("Konfigurasi AT+MAPS disimpan.")
          },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
          Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Simpan")
        }
        Button(
          onClick = {
            saveConfig()
            viewModel.renderMapsPreviewAndSendOnce()
          },
          modifier = Modifier.weight(1.3f).heightIn(min = 48.dp).testTag("atmaps_test_send_btn"),
          colors = ButtonDefaults.buttonColors(containerColor = cockpit.accent),
        ) {
          Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Uji & Kirim Frame")
        }
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}
