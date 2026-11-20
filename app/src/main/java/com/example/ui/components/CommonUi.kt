package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.ChargingIndicatorState
import com.example.model.ConnectionState
import com.example.ui.theme.LocalCockpitColors
import kotlinx.coroutines.delay

enum class MainTab(
  val route: String,
  val label: String,
  val icon: ImageVector,
) {
  HOME("home", "Home", Icons.Filled.Home),
  DIAGNOSIS("diagnosis", "Diagnosis", Icons.Filled.ElectricBolt),
  NAVIGASI("navigasi", "Navigasi", Icons.Filled.Navigation),
  AKUN("akun", "Akun", Icons.Filled.Person),
  STATS("stats", "Stats", Icons.Filled.Analytics),
}

@Composable
fun CockpitCard(
  modifier: Modifier = Modifier,
  borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
  containerColor: Color = MaterialTheme.colorScheme.surface,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier =
      modifier.let { base ->
        if (onClick != null) base.clickable(onClick = onClick) else base
      },
    shape = MaterialTheme.shapes.medium,
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
    tonalElevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(14.dp), content = content)
  }
}

@Composable
fun ConnectionStatusStrip(
  motorState: ConnectionState,
  jkState: ConnectionState,
  esp1State: ConnectionState,
  esp2State: ConnectionState,
  onChipClick: (slot: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ConnectionChip("Motor", motorState, Modifier.testTag("conn_chip_motor")) { onChipClick("motor") }
    ConnectionChip("JK-BMS", jkState, Modifier.testTag("conn_chip_jk")) { onChipClick("jikong") }
    ConnectionChip("ESP32 #1", esp1State, Modifier.testTag("conn_chip_esp1")) { onChipClick("esp1") }
    ConnectionChip("BMS #2", esp2State, Modifier.testTag("conn_chip_esp2")) { onChipClick("esp2") }
  }
}

@Composable
fun ConnectionChip(
  label: String,
  state: ConnectionState,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val cockpit = LocalCockpitColors.current
  val dotColor =
    when (state) {
      ConnectionState.CONNECTED -> cockpit.accent
      ConnectionState.CONNECTING,
      ConnectionState.SCANNING -> cockpit.warning
      ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
  val border =
    if (state == ConnectionState.CONNECTED) {
      cockpit.accent.copy(alpha = 0.5f)
    } else {
      MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }

  Surface(
    modifier = modifier.heightIn(min = 48.dp).clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, border),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color =
          if (state == ConnectionState.CONNECTED) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun ChargingStatusPill(
  state: ChargingIndicatorState,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  val (bgColor, fgColor, icon) =
    when (state) {
      ChargingIndicatorState.CHARGING ->
        Triple(cockpit.accent.copy(alpha = 0.16f), cockpit.accent, Icons.Filled.BatteryChargingFull)
      ChargingIndicatorState.FULL ->
        Triple(cockpit.accent.copy(alpha = 0.16f), cockpit.accent, Icons.Filled.BatteryFull)
      ChargingIndicatorState.REGEN ->
        Triple(cockpit.regen.copy(alpha = 0.16f), cockpit.regen, Icons.Filled.Bolt)
      ChargingIndicatorState.NOT_CHARGING ->
        Triple(
          MaterialTheme.colorScheme.surfaceVariant,
          MaterialTheme.colorScheme.onSurfaceVariant,
          Icons.Filled.BatteryStd,
        )
    }

  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(20.dp))
        .background(bgColor)
        .border(1.dp, fgColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        .padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(icon, contentDescription = state.label, tint = fgColor, modifier = Modifier.size(14.dp))
    Text(
      text = state.label,
      style = MaterialTheme.typography.labelSmall,
      color = fgColor,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
fun StatTile(
  label: String,
  value: String,
  unit: String = "",
  accentValueColor: Color = MaterialTheme.colorScheme.onSurface,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(2.dp))
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = value,
          style = MaterialTheme.typography.titleLarge,
          color = accentValueColor,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
        if (unit.isNotBlank()) {
          Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
          )
        }
      }
    }
  }
}

@Composable
fun HoldToConfirmButton(
  label: String,
  holdDurationMs: Long = 1400L,
  containerColor: Color,
  contentColor: Color,
  onConfirmed: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var isHolding by remember { mutableStateOf(false) }
  var progress by remember { mutableFloatStateOf(0f) }
  val animProgress by animateFloatAsState(progress, label = "hold_progress")

  LaunchedEffect(isHolding) {
    if (isHolding) {
      val stepMs = 40L
      val steps = (holdDurationMs / stepMs).toInt().coerceAtLeast(1)
      for (i in 1..steps) {
        if (!isHolding) break
        progress = i.toFloat() / steps
        delay(stepMs)
      }
      if (isHolding && progress >= 0.99f) {
        isHolding = false
        progress = 0f
        onConfirmed()
      }
    } else {
      progress = 0f
    }
  }

  Box(
    modifier =
      modifier
        .heightIn(min = 50.dp)
        .clip(RoundedCornerShape(25.dp))
        .background(containerColor.copy(alpha = 0.16f))
        .border(1.5.dp, containerColor.copy(alpha = 0.65f), RoundedCornerShape(25.dp))
        .pointerInput(Unit) {
          detectTapGestures(
            onPress = {
              isHolding = true
              tryAwaitRelease()
              isHolding = false
            }
          )
        },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.matchParentSize()
          .fillMaxWidth(animProgress)
          .background(containerColor.copy(alpha = 0.4f))
    )
    Text(
      text = if (isHolding) "Tahan… ${(animProgress * 100).toInt()}%" else label,
      style = MaterialTheme.typography.labelLarge,
      color = contentColor,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
    )
  }
}

@Composable
fun CockpitBottomBar(
  currentTab: MainTab,
  onSelectTab: (MainTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MainTab.entries.forEach { tab ->
        val selected = tab == currentTab
        val fg = if (selected) cockpit.accent else MaterialTheme.colorScheme.onSurfaceVariant
        Column(
          modifier =
            Modifier.weight(1f)
              .heightIn(min = 52.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(if (selected) cockpit.accent.copy(alpha = 0.12f) else Color.Transparent)
              .clickable { onSelectTab(tab) }
              .testTag("nav_tab_${tab.route}")
              .padding(vertical = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = fg,
            modifier = Modifier.size(22.dp),
          )
          Spacer(Modifier.height(2.dp))
          Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
          )
        }
      }
    }
  }
}
