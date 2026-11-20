package com.example.model

data class GeoPoint(
  val lat: Double,
  val lng: Double,
  val altM: Double? = null,
)

data class GpsState(
  val hasPermission: Boolean = false,
  val isTracking: Boolean = false,
  val location: GeoPoint? = null,
  val speedKmh: Float? = null,
  val headingDeg: Float? = null,
  val accuracyM: Float? = null,
  val roadName: String = "",
  val areaName: String = "",
  val lastUpdatedMs: Long = 0L,
)

data class HourlyWeatherItem(
  val timeLabel: String,
  val tempC: Float,
  val weatherCode: Int,
  val rainProbPct: Int,
)

data class WeatherState(
  val isLoading: Boolean = false,
  val tempC: Float? = null,
  val feelsLikeC: Float? = null,
  val windKmh: Float? = null,
  val humidityPct: Int? = null,
  val rainProbPct: Int? = null,
  val weatherCode: Int? = null,
  val conditionLabel: String = "Memuat cuaca…",
  val atNavMood: String = "",
  val cityName: String = "",
  val hourly: List<HourlyWeatherItem> = emptyList(),
  val updatedAtMs: Long = 0L,
  val errorMessage: String? = null,
)

data class PlaceSearchResult(
  val displayName: String,
  val shortName: String,
  val lat: Double,
  val lng: Double,
  val category: String = "",
)

data class RouteStep(
  val instruction: String,
  val roadName: String,
  val maneuverType: String,
  val maneuverModifier: String,
  val distanceM: Double,
  val durationSec: Double,
  val location: GeoPoint,
)

enum class HillGrade {
  FLAT,
  UPHILL_MILD,
  UPHILL_STEEP,
  DOWNHILL,
}

data class ElevationSample(
  val distanceKm: Double,
  val elevationM: Double,
  val gradePct: Double,
  val gradeType: HillGrade,
  val point: GeoPoint,
)

data class RouteEnergyEstimate(
  val totalWh: Double,
  val regenWh: Double,
  val netWh: Double,
  val whPerKm: Double,
  val socUsedPct: Double,
  val socAtArrivalPct: Double?,
  val maxClimbPct: Double,
  val totalAscentM: Double,
  val totalDescentM: Double,
)

data class RouteOption(
  val id: Int,
  val label: String,
  val distanceKm: Double,
  val durationMin: Double,
  val geometry: List<GeoPoint>,
  val steps: List<RouteStep>,
  val elevationSamples: List<ElevationSample> = emptyList(),
  val energyEstimate: RouteEnergyEstimate? = null,
)

data class SpkluStation(
  val id: String,
  val name: String,
  val operatorName: String,
  val address: String,
  val lat: Double,
  val lng: Double,
  val distanceKm: Double,
  val connectorSummary: String,
  val powerKw: Double? = null,
  val source: String, // "OpenChargeMap" or "OpenStreetMap"
)

data class MultiStopItem(
  val id: String,
  val name: String,
  val lat: Double,
  val lng: Double,
)

data class NavDestination(
  val name: String,
  val lat: Double,
  val lng: Double,
)
