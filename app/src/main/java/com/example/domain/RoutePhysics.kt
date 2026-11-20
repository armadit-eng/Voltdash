package com.example.domain

import com.example.model.ElevationSample
import com.example.model.GeoPoint
import com.example.model.HillGrade
import com.example.model.RouteEnergyEstimate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object RoutePhysics {
  private const val GRAVITY = 9.81
  private const val CRR = 0.013
  private const val CDA = 0.55
  private const val RHO = 1.18
  private const val ETA_DRIVE = 0.80
  private const val ETA_REGEN = 0.45
  private const val AUX_W = 25.0

  fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lng - a.lng)
    val sinLat = sin(dLat / 2.0)
    val sinLon = sin(dLon / 2.0)
    val h =
      sinLat * sinLat +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinLon * sinLon
    return 2.0 * r * atan2(sqrt(h), sqrt(1.0 - h))
  }

  fun parseCoordinatesOrGoogleMapsUrl(input: String): GeoPoint? {
    val trimmed = input.trim()
    // Direct "lat, lng"
    val direct = Regex("""(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""").find(trimmed)
    if (direct != null) {
      val lat = direct.groupValues[1].toDoubleOrNull()
      val lng = direct.groupValues[2].toDoubleOrNull()
      if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
        return GeoPoint(lat, lng)
      }
    }
    // Google Maps @lat,lng or q=lat,lng
    val gmaps = Regex("""[@/](-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)""").find(trimmed)
    if (gmaps != null) {
      val lat = gmaps.groupValues[1].toDoubleOrNull()
      val lng = gmaps.groupValues[2].toDoubleOrNull()
      if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
        return GeoPoint(lat, lng)
      }
    }
    return null
  }

  fun downsamplePoints(points: List<GeoPoint>, maxSamples: Int = 36): List<GeoPoint> {
    if (points.size <= maxSamples) return points
    val step = (points.size - 1).toDouble() / (maxSamples - 1)
    return (0 until maxSamples).map { idx ->
      points[(idx * step).toInt().coerceIn(0, points.lastIndex)]
    }
  }

  fun buildElevationAndEnergy(
    sampledPoints: List<GeoPoint>,
    elevations: List<Double>,
    totalDistanceKm: Double,
    durationMin: Double,
    bikeKg: Float,
    riderKg: Float,
    loadKg: Float,
    calFactor: Float,
    totalPackWh: Double,
    currentSocPct: Float?,
  ): Pair<List<ElevationSample>, RouteEnergyEstimate> {
    val n = minOf(sampledPoints.size, elevations.size)
    if (n < 2) {
      val fallbackWh = totalDistanceKm * 28.0 * calFactor
      val socUsed = if (totalPackWh > 0) (fallbackWh / totalPackWh) * 100.0 else 0.0
      return emptyList<ElevationSample>() to
        RouteEnergyEstimate(
          totalWh = fallbackWh,
          regenWh = 0.0,
          netWh = fallbackWh,
          whPerKm = if (totalDistanceKm > 0.1) fallbackWh / totalDistanceKm else 28.0,
          socUsedPct = socUsed,
          socAtArrivalPct = currentSocPct?.let { (it - socUsed).coerceIn(0.0, 100.0) },
          maxClimbPct = 0.0,
          totalAscentM = 0.0,
          totalDescentM = 0.0,
        )
    }

    val totalMassKg = (bikeKg + riderKg + loadKg).toDouble()
    val avgSpeedMs =
      if (durationMin > 0.1) ((totalDistanceKm * 1000.0) / (durationMin * 60.0)).coerceIn(5.0, 22.0)
      else 10.0

    val samples = mutableListOf<ElevationSample>()
    var cumDistKm = 0.0
    var driveWh = 0.0
    var regenWh = 0.0
    var ascent = 0.0
    var descent = 0.0
    var maxClimb = 0.0

    samples.add(
      ElevationSample(
        distanceKm = 0.0,
        elevationM = elevations[0],
        gradePct = 0.0,
        gradeType = HillGrade.FLAT,
        point = sampledPoints[0],
      )
    )

    for (i in 1 until n) {
      val segKm = haversineKm(sampledPoints[i - 1], sampledPoints[i]).coerceAtLeast(0.02)
      cumDistKm += segKm
      val segM = segKm * 1000.0
      val dh = elevations[i] - elevations[i - 1]
      if (dh > 0) ascent += dh else descent += -dh

      val gradePct = ((dh / segM) * 100.0).coerceIn(-35.0, 35.0)
      if (gradePct > maxClimb) maxClimb = gradePct

      val theta = atan2(dh, segM)
      val force =
        totalMassKg * GRAVITY * sin(theta) +
          CRR * totalMassKg * GRAVITY * cos(theta) +
          0.5 * RHO * CDA * avgSpeedMs.pow(2.0)

      val mechEnergyJ = force * segM
      val segTimeHr = (segM / avgSpeedMs) / 3600.0
      val auxWh = AUX_W * segTimeHr

      if (mechEnergyJ >= 0) {
        driveWh += (mechEnergyJ / 3600.0) / ETA_DRIVE + auxWh
      } else {
        regenWh += ((-mechEnergyJ) / 3600.0) * ETA_REGEN
        driveWh += auxWh
      }

      val gradeType =
        when {
          gradePct >= 6.0 -> HillGrade.UPHILL_STEEP
          gradePct >= 2.5 -> HillGrade.UPHILL_MILD
          gradePct <= -2.5 -> HillGrade.DOWNHILL
          else -> HillGrade.FLAT
        }

      samples.add(
        ElevationSample(
          distanceKm = cumDistKm,
          elevationM = elevations[i],
          gradePct = gradePct,
          gradeType = gradeType,
          point = sampledPoints[i],
        )
      )
    }

    val calDriveWh = driveWh * calFactor
    val netWh = (calDriveWh - regenWh).coerceAtLeast(totalDistanceKm * 10.0)
    val socUsed = if (totalPackWh > 0.0) (netWh / totalPackWh) * 100.0 else 0.0

    return samples to
      RouteEnergyEstimate(
        totalWh = calDriveWh,
        regenWh = regenWh,
        netWh = netWh,
        whPerKm = if (totalDistanceKm > 0.1) netWh / totalDistanceKm else 28.0,
        socUsedPct = socUsed,
        socAtArrivalPct = currentSocPct?.let { (it - socUsed).coerceIn(0.0, 100.0) },
        maxClimbPct = maxClimb,
        totalAscentM = ascent,
        totalDescentM = descent,
      )
  }

  fun translateOsrmManeuver(type: String?, modifier: String?, roadName: String?): String {
    val road = if (!roadName.isNullOrBlank()) " ke $roadName" else ""
    return when (type) {
      "depart" -> "Mulai perjalanan$road"
      "arrive" -> "Tiba di tujuan"
      "roundabout", "rotary" -> "Masuk bundaran$road"
      "merge" -> "Gabung jalur$road"
      "fork" ->
        when (modifier) {
          "left", "slight left" -> "Ambil cabang kiri$road"
          "right", "slight right" -> "Ambil cabang kanan$road"
          else -> "Tetap di jalur$road"
        }
      else ->
        when (modifier) {
          "left" -> "Belok kiri$road"
          "sharp left" -> "Belok kiri tajam$road"
          "slight left" -> "Serong kiri$road"
          "right" -> "Belok kanan$road"
          "sharp right" -> "Belok kanan tajam$road"
          "slight right" -> "Serong kanan$road"
          "uturn" -> "Putar balik$road"
          else -> "Lurus terus$road"
        }
    }
  }

  fun decodeWeatherCode(code: Int?): Pair<String, String> {
    return when (code) {
      0 -> "Cerah" to "CERAH"
      1, 2 -> "Cerah Berawan" to "BERAWAN"
      3 -> "Mendung" to "MENDUNG"
      45, 48 -> "Berkabut" to "KABUT"
      51, 53, 55 -> "Gerimis" to "GERIMIS"
      61, 63, 65, 80, 81, 82 -> "Hujan" to "HUJAN"
      95, 96, 99 -> "Badai Petir" to "BADAI"
      else -> "Berawan" to "BERAWAN"
    }
  }
}
