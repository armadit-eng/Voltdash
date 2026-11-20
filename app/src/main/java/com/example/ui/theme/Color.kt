package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Base Cockpit Dark Palette
val CockpitBgDark = Color(0xFF050C12)
val CockpitSurfaceDark = Color(0xFF0B1722)
val CockpitSurfaceVariantDark = Color(0xFF11212E)
val CockpitSurfaceElevatedDark = Color(0xFF162939)
val CockpitOutlineDark = Color(0xFF1F3547)
val CockpitTextPrimaryDark = Color(0xFFEAF2F8)
val CockpitTextSecondaryDark = Color(0xFF8CA3B5)

// Base Cockpit Light Palette
val CockpitBgLight = Color(0xFFF2F6FA)
val CockpitSurfaceLight = Color(0xFFFFFFFF)
val CockpitSurfaceVariantLight = Color(0xFFE5EDF4)
val CockpitSurfaceElevatedLight = Color(0xFFD8E4EE)
val CockpitOutlineLight = Color(0xFFC5D5E4)
val CockpitTextPrimaryLight = Color(0xFF0D1B26)
val CockpitTextSecondaryLight = Color(0xFF53697B)

// Semantic Telemetry Colors
val StatusWarning = Color(0xFFFFA726)
val StatusDanger = Color(0xFFFF5252)
val StatusInfo = Color(0xFF29B6F6)
val StatusRegen = Color(0xFF00E5FF)
val StatusPurple = Color(0xFFCE93D8)

enum class AccentTheme(
  val id: String,
  val displayName: String,
  val primary: Color,
  val secondary: Color,
) {
  DEFAULT("default", "Electric Green", Color(0xFF00E676), Color(0xFF00BCD4)),
  CYAN("cyan", "Aqua Cyan", Color(0xFF00D4FF), Color(0xFF0088AA)),
  ORANGE("orange", "Sunset Orange", Color(0xFFFF9100), Color(0xFFFF6D00)),
  PURPLE("purple", "Royal Purple", Color(0xFFCE93D8), Color(0xFFAB47BC)),
  RED("red", "Racing Red", Color(0xFFE5383B), Color(0xFFFF6A4D)),
  YELLOW("yellow", "Taxi Yellow", Color(0xFFFFD740), Color(0xFFFFAB00)),
  WHITE("white", "Pearl White", Color(0xFFE8EAF0), Color(0xFFB0BEC5)),
  PINK("pink", "Bubblegum Pink", Color(0xFFF48FB1), Color(0xFFF06292));

  companion object {
    fun fromId(id: String): AccentTheme = entries.find { it.id == id } ?: DEFAULT
  }
}

enum class GarageLivery(
  val id: String,
  val displayName: String,
  val description: String,
  val bodyColor: Color,
  val trimColor: Color,
  val glowColor: Color,
) {
  DEFAULT(
    "default",
    "Electric Green",
    "Livery default GESITS — segar dan sporty",
    Color(0xFF00E676),
    Color(0xFF00BCD4),
    Color(0x2E00E676),
  ),
  CYAN(
    "cyan",
    "Aqua Cyan",
    "Dingin, modern, cocok buat riding malam hari",
    Color(0xFF00D4FF),
    Color(0xFF0088AA),
    Color(0x3300D4FF),
  ),
  ORANGE(
    "orange",
    "Sunset Orange",
    "Mencolok dan hangat, gampang dikenali di jalan",
    Color(0xFFFF9100),
    Color(0xFFFF6D00),
    Color(0x33FF9100),
  ),
  PURPLE(
    "purple",
    "Royal Purple",
    "Elegan dan beda dari yang lain",
    Color(0xFFCE93D8),
    Color(0xFFAB47BC),
    Color(0x33CE93D8),
  ),
  RED(
    "red",
    "Racing Red",
    "Agresif, klasik, jiwa balap",
    Color(0xFFFF5252),
    Color(0xFFD32F2F),
    Color(0x33FF5252),
  ),
  YELLOW(
    "yellow",
    "Taxi Yellow",
    "Terang & sangat terlihat — bagus buat riding siang",
    Color(0xFFFFD740),
    Color(0xFFFFAB00),
    Color(0x33FFD740),
  ),
  WHITE(
    "white",
    "Pearl White",
    "Bersih, minimalis, gampang dipadukan",
    Color(0xFFE8EAF0),
    Color(0xFF90A4AE),
    Color(0x2EE8EAF0),
  ),
  PINK(
    "pink",
    "Bubblegum Pink",
    "Playful dan beda sendiri di parkiran",
    Color(0xFFF48FB1),
    Color(0xFFF06292),
    Color(0x33F48FB1),
  );

  companion object {
    fun fromId(id: String): GarageLivery = entries.find { it.id == id } ?: DEFAULT
  }
}
