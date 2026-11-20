package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── OPEN-METEO WEATHER ──
@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
  val current: OpenMeteoCurrent? = null,
  val hourly: OpenMeteoHourly? = null,
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrent(
  @Json(name = "temperature_2m") val temperature2m: Float? = null,
  @Json(name = "apparent_temperature") val apparentTemperature: Float? = null,
  @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int? = null,
  @Json(name = "weather_code") val weatherCode: Int? = null,
  @Json(name = "wind_speed_10m") val windSpeed10m: Float? = null,
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourly(
  val time: List<String> = emptyList(),
  @Json(name = "temperature_2m") val temperature2m: List<Float?> = emptyList(),
  @Json(name = "precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
  @Json(name = "weather_code") val weatherCode: List<Int?> = emptyList(),
)

// ── OPEN-METEO ELEVATION ──
@JsonClass(generateAdapter = true)
data class OpenMeteoElevationResponse(
  val elevation: List<Double>? = null,
)

// ── NOMINATIM GEOCODING ──
@JsonClass(generateAdapter = true)
data class NominatimPlace(
  @Json(name = "display_name") val displayName: String? = null,
  val name: String? = null,
  val lat: String? = null,
  val lon: String? = null,
  val type: String? = null,
  val address: NominatimAddress? = null,
)

@JsonClass(generateAdapter = true)
data class NominatimAddress(
  val road: String? = null,
  val suburb: String? = null,
  val village: String? = null,
  val city: String? = null,
  val town: String? = null,
  val county: String? = null,
  val state: String? = null,
)

// ── OSRM ROUTING ──
@JsonClass(generateAdapter = true)
data class OsrmRouteResponse(
  val code: String? = null,
  val routes: List<OsrmRoute> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OsrmRoute(
  val distance: Double = 0.0,
  val duration: Double = 0.0,
  val geometry: OsrmGeometry? = null,
  val legs: List<OsrmLeg> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OsrmGeometry(
  val coordinates: List<List<Double>> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OsrmLeg(
  val steps: List<OsrmStep> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OsrmStep(
  val name: String? = null,
  val distance: Double = 0.0,
  val duration: Double = 0.0,
  val maneuver: OsrmManeuver? = null,
)

@JsonClass(generateAdapter = true)
data class OsrmManeuver(
  val type: String? = null,
  val modifier: String? = null,
  val location: List<Double> = emptyList(),
)

// ── OPENCHARGEMAP ──
@JsonClass(generateAdapter = true)
data class OcmPoi(
  @Json(name = "ID") val id: Long? = null,
  @Json(name = "AddressInfo") val addressInfo: OcmAddressInfo? = null,
  @Json(name = "OperatorInfo") val operatorInfo: OcmOperatorInfo? = null,
  @Json(name = "Connections") val connections: List<OcmConnection>? = null,
)

@JsonClass(generateAdapter = true)
data class OcmAddressInfo(
  @Json(name = "Title") val title: String? = null,
  @Json(name = "AddressLine1") val addressLine1: String? = null,
  @Json(name = "Town") val town: String? = null,
  @Json(name = "Latitude") val latitude: Double? = null,
  @Json(name = "Longitude") val longitude: Double? = null,
  @Json(name = "Distance") val distance: Double? = null,
)

@JsonClass(generateAdapter = true)
data class OcmOperatorInfo(
  @Json(name = "Title") val title: String? = null,
)

@JsonClass(generateAdapter = true)
data class OcmConnection(
  @Json(name = "PowerKW") val powerKw: Double? = null,
  @Json(name = "ConnectionType") val connectionType: OcmConnectionType? = null,
)

@JsonClass(generateAdapter = true)
data class OcmConnectionType(
  @Json(name = "Title") val title: String? = null,
)

// ── OVERPASS API (OSM SPKLU) ──
@JsonClass(generateAdapter = true)
data class OverpassResponse(
  val elements: List<OverpassElement> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OverpassElement(
  val id: Long = 0L,
  val lat: Double? = null,
  val lon: Double? = null,
  val center: OverpassCenter? = null,
  val tags: Map<String, String>? = null,
)

@JsonClass(generateAdapter = true)
data class OverpassCenter(
  val lat: Double? = null,
  val lon: Double? = null,
)
