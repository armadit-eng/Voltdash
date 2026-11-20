package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class CockpitColors(
  val accent: Color,
  val accentSecondary: Color,
  val warning: Color = StatusWarning,
  val danger: Color = StatusDanger,
  val info: Color = StatusInfo,
  val regen: Color = StatusRegen,
  val cardElevated: Color,
  val divider: Color,
)

val LocalCockpitColors = staticCompositionLocalOf {
  CockpitColors(
    accent = AccentTheme.DEFAULT.primary,
    accentSecondary = AccentTheme.DEFAULT.secondary,
    cardElevated = CockpitSurfaceElevatedDark,
    divider = CockpitOutlineDark,
  )
}

val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
  )

@Composable
fun GesitsDashTheme(
  darkTheme: Boolean = true,
  accentTheme: AccentTheme = AccentTheme.DEFAULT,
  content: @Composable () -> Unit,
) {
  val primary =
    if (!darkTheme && accentTheme == AccentTheme.WHITE) Color(0xFF455A64) else accentTheme.primary
  val secondary =
    if (!darkTheme && accentTheme == AccentTheme.WHITE) Color(0xFF607D8B) else accentTheme.secondary

  val colorScheme =
    if (darkTheme) {
      darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF04120B),
        primaryContainer = primary.copy(alpha = 0.16f),
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = Color(0xFF041418),
        secondaryContainer = secondary.copy(alpha = 0.18f),
        onSecondaryContainer = secondary,
        tertiary = StatusInfo,
        background = CockpitBgDark,
        onBackground = CockpitTextPrimaryDark,
        surface = CockpitSurfaceDark,
        onSurface = CockpitTextPrimaryDark,
        surfaceVariant = CockpitSurfaceVariantDark,
        onSurfaceVariant = CockpitTextSecondaryDark,
        outline = CockpitOutlineDark,
        outlineVariant = CockpitOutlineDark.copy(alpha = 0.6f),
        error = StatusDanger,
        onError = Color.White,
      )
    } else {
      lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.15f),
        onPrimaryContainer = Color(0xFF082418),
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = secondary.copy(alpha = 0.15f),
        onSecondaryContainer = Color(0xFF082028),
        tertiary = StatusInfo,
        background = CockpitBgLight,
        onBackground = CockpitTextPrimaryLight,
        surface = CockpitSurfaceLight,
        onSurface = CockpitTextPrimaryLight,
        surfaceVariant = CockpitSurfaceVariantLight,
        onSurfaceVariant = CockpitTextSecondaryLight,
        outline = CockpitOutlineLight,
        outlineVariant = CockpitOutlineLight.copy(alpha = 0.7f),
        error = StatusDanger,
        onError = Color.White,
      )
    }

  val cockpitColors =
    CockpitColors(
      accent = primary,
      accentSecondary = secondary,
      cardElevated = if (darkTheme) CockpitSurfaceElevatedDark else CockpitSurfaceElevatedLight,
      divider = if (darkTheme) CockpitOutlineDark else CockpitOutlineLight,
    )

  CompositionLocalProvider(LocalCockpitColors provides cockpitColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = AppShapes,
      content = content,
    )
  }
}
