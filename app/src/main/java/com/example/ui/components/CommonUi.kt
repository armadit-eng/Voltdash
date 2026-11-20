package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ChargingIndicatorState
import com.example.model.ConnectionState
import com.example.ui.theme.LocalCockpitColors
import kotlinx.coroutines.delay

enum class MainTab(
  val route: String,
  val label: String,
  val icon: ImageVector,
  val inactiveIcon: ImageVector,
) {
  HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
  DIAGNOSIS("diagnosis", "Diagnosis", Icons.Filled.ElectricBolt, Icons.Outlined.ElectricBolt),
  NAVIGASI("navigasi", "Navigasi", Icons.Filled.Navigation, Icons.Outlined.Navigation),
  AKUN("akun", "Akun", Icons.Filled.Person, Icons.Outlined.Person),
  STATS("stats", "Stats", Icons.Filled.Analytics, Icons.Outlined.Analytics),
}

@Composable
fun CockpitCard(
  modifier: Modifier = Modifier,
  borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
  containerColor: Color = MaterialTheme.colorScheme.surface,
  accentGlow: Color? = null,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val cockpit = LocalCockpitColors.current
  val shape = RoundedCornerShape(18.dp)
  val bgBrush =
    if (accentGlow != null) {
      Brush.verticalGradient(
        colors = listOf(
          accentGlow.copy(alpha = 0.11f),
          containerColor,
          cockpit.cardElevated.copy(alpha = 0.55f),
        )
      )
    } else {
      Brush.verticalGradient(
        colors = listOf(
          containerColor,
          containerColor.copy(alpha = 0.94f),
        )
      )
    }

  Surface(
    modifier =
      modifier.let { base ->
        if (onClick != null) base.clip(shape).clickable(onClick = onClick) else base
      },
    shape = shape,
    color = Color.Transparent,
    border = BorderStroke(1.dp, borderColor),
    tonalElevation = 2.dp,
  ) {
    Column(
      modifier = Modifier.background(bgBrush).padding(15.dp),
      content = content,
    )
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
  val dotColor by
    animateColorAsState(
      targetValue =
        when (state) {
          ConnectionState.CONNECTED -> cockpit.accent
          ConnectionState.CONNECTING,
          ConnectionState.SCANNING -> cockpit.warning
          ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        },
      label = "conn_dot",
    )
  val borderColor =
    when (state) {
      ConnectionState.CONNECTED -> cockpit.accent.copy(alpha = 0.6f)
      ConnectionState.CONNECTING,
      ConnectionState.SCANNING -> cockpit.warning.copy(alpha = 0.55f)
      ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

  Surface(
    modifier = modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    shape = RoundedCornerShape(24.dp),
    color =
      if (state == ConnectionState.CONNECTED) cockpit.accent.copy(alpha = 0.12f)
      else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    border = BorderStroke(1.dp, borderColor),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Box(
        modifier =
          Modifier.size(8.dp)
            .clip(CircleShape)
            .background(dotColor),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (state == ConnectionState.CONNECTED) FontWeight.Bold else FontWeight.SemiBold,
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
        Triple(cockpit.accent.copy(alpha = 0.18f), cockpit.accent, Icons.Filled.BatteryChargingFull)
      ChargingIndicatorState.FULL ->
        Triple(cockpit.accent.copy(alpha = 0.18f), cockpit.accent, Icons.Filled.BatteryFull)
      ChargingIndicatorState.REGEN ->
        Triple(cockpit.regen.copy(alpha = 0.18f), cockpit.regen, Icons.Filled.Bolt)
      ChargingIndicatorState.NOT_CHARGING ->
        Triple(
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
          MaterialTheme.colorScheme.onSurfaceVariant,
          Icons.Filled.BatteryStd,
        )
    }

  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(22.dp))
        .background(bgColor)
        .border(1.dp, fgColor.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
        .padding(horizontal = 11.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(icon, contentDescription = state.label, tint = fgColor, modifier = Modifier.size(15.dp))
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
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)),
  ) {
    Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(3.dp))
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = value,
          style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp, lineHeight = 25.sp),
          color = accentValueColor,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
        if (unit.isNotBlank()) {
          Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp),
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
        .heightIn(min = 52.dp)
        .clip(RoundedCornerShape(26.dp))
        .background(containerColor.copy(alpha = 0.16f))
        .border(1.5.dp, containerColor.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
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
          .background(containerColor.copy(alpha = 0.45f))
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
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    tonalElevation = 6.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 7.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MainTab.entries.forEach { tab ->
        val selected = tab == currentTab
        val fg by
          animateColorAsState(
            targetValue = if (selected) cockpit.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "tab_fg",
          )
        val pillBg by
          animateColorAsState(
            targetValue = if (selected) cockpit.accent.copy(alpha = 0.16f) else Color.Transparent,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "tab_bg",
          )
        Column(
          modifier =
            Modifier.weight(1f)
              .heightIn(min = 52.dp)
              .clip(RoundedCornerShape(16.dp))
              .clickable { onSelectTab(tab) }
              .testTag("nav_tab_${tab.route}")
              .padding(vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(14.dp))
                .background(pillBg)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (selected) tab.icon else tab.inactiveIcon,
              contentDescription = tab.label,
              tint = fg,
              modifier = Modifier.size(21.dp),
            )
          }
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
