package com.example.model

data class RiderLevel(
  val name: String,
  val minPoints: Int,
  val maxPoints: Int,
)

val GM_LEVELS =
  listOf(
    RiderLevel("Pemula", 0, 200),
    RiderLevel("Penjelajah", 200, 500),
    RiderLevel("Eco Rider", 500, 1000),
    RiderLevel("Maestro", 1000, 2000),
    RiderLevel("Legenda", 2000, Int.MAX_VALUE),
  )

data class GamificationStatsSnapshot(
  val tripCount: Int,
  val totalKm: Double,
  val ecoTrips: Int,
  val avgScore: Double,
  val totalRegenWh: Double,
  val chargeCount: Int,
)

data class BadgeDefinition(
  val id: String,
  val iconName: String,
  val name: String,
  val description: String,
  val check: (GamificationStatsSnapshot) -> Boolean,
)

val GM_BADGES =
  listOf(
    BadgeDefinition("first", "eco", "Trip Pertama", "Selesaikan 1 trip") { it.tripCount >= 1 },
    BadgeDefinition("km10", "route", "10 km", "Total 10 km") { it.totalKm >= 10.0 },
    BadgeDefinition("km50", "flag", "50 km", "Total 50 km") { it.totalKm >= 50.0 },
    BadgeDefinition("km100", "map", "100 km", "Total 100 km") { it.totalKm >= 100.0 },
    BadgeDefinition("eco5", "leaf", "Eco Rider", "5 trip <40 Wh/km") { it.ecoTrips >= 5 },
    BadgeDefinition("score80", "star", "Top Scorer", "Score rata2 >= 80") { it.avgScore >= 80.0 },
    BadgeDefinition("regen", "bolt", "Regen Master", "50 Wh total regen") {
      it.totalRegenWh >= 50.0
    },
    BadgeDefinition("charge5", "power", "Charge Rajin", "5x catat pengisian") {
      it.chargeCount >= 5
    },
    BadgeDefinition("km200", "rocket", "200 km", "Total 200 km") { it.totalKm >= 200.0 },
  )

enum class MaintHealthLevel(val badgeText: String) {
  OK("OK"),
  WARN("DEKAT"),
  DUE("SEGERA"),
}

data class MaintenanceStatus(
  val lastServiceOdoKm: Float,
  val intervalKm: Float,
  val nextServiceOdoKm: Float,
  val remainingKm: Float,
  val overdueKm: Float,
  val progressPct: Float,
  val level: MaintHealthLevel,
)

data class DailyBarData(
  val dayLabel: String,
  val distanceKm: Double,
  val energyWh: Double,
  val avgScore: Int,
)

data class FuelSavingsSummary(
  val totalKm: Double,
  val petrolCostRp: Long,
  val electricityCostRp: Long,
  val netSavingsRp: Long,
  val co2SavedKg: Double,
)
