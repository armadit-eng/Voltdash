package com.example.domain

import com.example.data.local.AtNavSlotConfig
import com.example.data.local.DashboardSettings
import com.example.model.AlertSeverity
import com.example.model.AppAlert
import com.example.model.Battery2Data
import com.example.model.BatterySourcePriority
import com.example.model.ConnectionState
import com.example.model.ERROR_DEFS
import com.example.model.EffectiveTelemetry
import com.example.model.Esp32Data
import com.example.model.FuelSavingsSummary
import com.example.model.JkBmsData
import com.example.model.LiveTripMetrics
import com.example.model.MaintHealthLevel
import com.example.model.MaintenanceStatus
import com.example.model.MotorData
import com.example.model.RidingScoreState
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ChargingIndicatorState(val label: String) {
  CHARGING("Mengisi Daya"),
  FULL("Terisi Penuh"),
  NOT_CHARGING("Tidak Mengisi Daya"),
  REGEN("Regeneratif"),
}

object TelemetryEngine {
  private const val STALE_MS = 10_000L
  private const val INTEGRATE_MAX_GAP_MS = 4_000L

  fun computeEffective(
    motor: MotorData,
    motorState: ConnectionState,
    jk: JkBmsData,
    jkState: ConnectionState,
    esp1: Esp32Data,
    esp1State: ConnectionState,
    b2: Battery2Data,
    esp2State: ConnectionState,
    gpsSpeedKmh: Float?,
    trip: LiveTripMetrics,
    settings: DashboardSettings,
    nowMs: Long = System.currentTimeMillis(),
  ): EffectiveTelemetry {
    val motorAlive = motorState == ConnectionState.CONNECTED && (nowMs - motor.lastUpdatedMs) < STALE_MS
    val jkAlive = jkState == ConnectionState.CONNECTED && (nowMs - jk.lastUpdatedMs) < STALE_MS
    val esp1Alive = esp1State == ConnectionState.CONNECTED && (nowMs - esp1.lastUpdatedMs) < STALE_MS
    val esp2Alive = esp2State == ConnectionState.CONNECTED && (nowMs - b2.lastUpdatedMs) < STALE_MS

    val speedKmh =
      when {
        motorAlive && motor.speedKmh != null -> motor.speedKmh
        esp1Alive && esp1.speedKmh != null -> esp1.speedKmh
        else -> gpsSpeedKmh
      }

    val throttlePct =
      when {
        motorAlive && motor.throttlePct != null -> motor.throttlePct
        esp1Alive && esp1.throttlePct != null -> esp1.throttlePct
        else -> null
      }

    val preferJk = settings.batteryPriority == BatterySourcePriority.JIKONG
    val b1Source: String
    val b1Soc: Float?
    val b1Vbat: Float?
    val b1Ibat: Float?
    val b1Temp: Float?
    val b1Soh: Float?

    if ((preferJk && jkAlive) || (!motorAlive && jkAlive)) {
      b1Source = "JK-BMS"
      b1Soc = jk.socPct ?: motor.socPct
      b1Vbat = jk.vpack ?: motor.vbat
      b1Ibat = jk.ibat ?: motor.ibat
      b1Temp = jk.temp1C ?: motor.tempBatteryC
      b1Soh = jk.sohPct ?: motor.sohPct
    } else if (motorAlive) {
      b1Source = "Motor MID"
      b1Soc = motor.socPct
      b1Vbat = motor.vbat
      b1Ibat = motor.ibat
      b1Temp = motor.tempBatteryC
      b1Soh = motor.sohPct
    } else {
      b1Source = "—"
      b1Soc = null
      b1Vbat = null
      b1Ibat = null
      b1Temp = null
      b1Soh = null
    }

    val b2Active = esp2Alive && (b2.socPct != null || b2.vbat != null)
    val b2Soc = if (b2Active) b2.socPct else null
    val b2Vbat = if (b2Active) b2.vbat else null
    val b2Ibat = if (b2Active) b2.ibat else null
    val b2Temp = if (b2Active) b2.tempC else null

    val w1 = settings.b1NominalV * settings.b1CapacityAh
    val w2 = settings.b2NominalV * settings.b2CapacityAh

    val combinedSoc: Float? =
      when {
        b1Soc != null && b2Soc != null -> (b1Soc * w1 + b2Soc * w2) / (w1 + w2)
        b1Soc != null -> b1Soc
        b2Soc != null -> b2Soc
        else -> null
      }

    val p1: Float? = if (b1Vbat != null && b1Ibat != null) -(b1Vbat * b1Ibat) else null
    val p2: Float? =
      b2.powerW ?: if (b2Vbat != null && b2Ibat != null) -(b2Vbat * b2Ibat) else null

    val combinedPowerW: Float? =
      when {
        p1 != null && p2 != null -> p1 + p2
        p1 != null -> p1
        p2 != null -> p2
        else -> null
      }

    val combinedCurrentA: Float? =
      when {
        b1Ibat != null && b2Ibat != null -> b1Ibat + b2Ibat
        b1Ibat != null -> b1Ibat
        b2Ibat != null -> b2Ibat
        else -> null
      }

    val avgVoltageV: Float? =
      when {
        b1Vbat != null && b2Vbat != null -> (b1Vbat + b2Vbat) / 2f
        b1Vbat != null -> b1Vbat
        b2Vbat != null -> b2Vbat
        else -> null
      }

    val spd = speedKmh ?: 0f
    val curr = combinedCurrentA ?: 0f
    val isRegen = curr > 0.3f && spd > 1.0f
    val isCharging = curr > 0.3f && spd <= 1.0f

    // Smoothed efficiency: blend measured Wh/km with reference Wh/km under 1.5 km
    val refWhKm = settings.refWhPerKm.coerceIn(10f, 90f)
    val measuredWhKm = trip.rawWhPerKm
    val blendedWhKm =
      if (trip.distanceKm >= 1.5f && measuredWhKm > 1f) {
        measuredWhKm
      } else if (trip.distanceKm > 0.1f && measuredWhKm > 1f) {
        val k0 = 0.3f * (1f - trip.distanceKm / 1.5f)
        k0 * refWhKm + (1f - k0) * measuredWhKm
      } else {
        refWhKm
      }

    val totalCapWh =
      when {
        b1Soc != null && b2Soc != null -> (w1 * (b1Soc / 100f)) + (w2 * (b2Soc / 100f))
        b1Soc != null -> w1 * (b1Soc / 100f)
        b2Soc != null -> w2 * (b2Soc / 100f)
        else -> 0f
      }
    val rangeKm = if (blendedWhKm > 1f) (totalCapWh / blendedWhKm).coerceAtLeast(0f) else 0f

    val activeErrors = ERROR_DEFS.filter { ((motor.errMask shr it.bit) and 1) == 1 }
    val odoKm = motor.odoKm ?: settings.odoBackupKm
    val modeLabel =
      when (esp1.modeIndex ?: motor.modeIndex) {
        0 -> "ECO"
        1 -> "URBAN"
        2 -> "SPORT"
        else -> "—"
      }

    return EffectiveTelemetry(
      speedKmh = speedKmh,
      rpm = speedKmh?.let { (it * 51.45f).roundToInt() },
      throttlePct = throttlePct,
      b1SocPct = b1Soc,
      b1Vbat = b1Vbat,
      b1Ibat = b1Ibat,
      b1TempC = b1Temp,
      b1SohPct = b1Soh,
      b1SourceLabel = b1Source,
      b2Active = b2Active,
      b2SocPct = b2Soc,
      b2Vbat = b2Vbat,
      b2Ibat = b2Ibat,
      b2TempC = b2Temp,
      combinedSocPct = combinedSoc,
      combinedPowerW = combinedPowerW,
      combinedCurrentA = combinedCurrentA,
      avgVoltageV = avgVoltageV,
      isCharging = isCharging,
      isRegen = isRegen,
      rangeKm = rangeKm,
      blendedWhPerKm = blendedWhKm,
      totalAvailableWh = totalCapWh,
      odoKm = odoKm,
      modeLabel = modeLabel,
      isPark = esp1.isPark ?: motor.isPark,
      sideStandDown = esp1Alive && esp1.sideStandDown,
      cruiseActive = esp1Alive && esp1.cruiseActive,
      reverseActive = esp1Alive && esp1.reverseActive,
      activeErrors = activeErrors,
    )
  }

  fun chargingIndicator(eff: EffectiveTelemetry): ChargingIndicatorState {
    val soc = eff.combinedSocPct
    return when {
      eff.isRegen -> ChargingIndicatorState.REGEN
      eff.isCharging -> ChargingIndicatorState.CHARGING
      soc != null && soc >= 99.5f -> ChargingIndicatorState.FULL
      else -> ChargingIndicatorState.NOT_CHARGING
    }
  }

  fun integrateSample(
    prevTrip: LiveTripMetrics,
    prevScore: RidingScoreState,
    eff: EffectiveTelemetry,
    nowMs: Long,
    wasMoving: Boolean,
  ): Triple<LiveTripMetrics, RidingScoreState, Boolean> {
    val trip =
      if (prevTrip.startTimeMs == 0L) {
        prevTrip.copy(startTimeMs = nowMs, startSocPct = eff.combinedSocPct, lastSampleMs = nowMs)
      } else {
        prevTrip
      }

    val dtMs = if (trip.lastSampleMs > 0L) nowMs - trip.lastSampleMs else 0L
    if (dtMs <= 0L || dtMs > INTEGRATE_MAX_GAP_MS) {
      return Triple(trip.copy(lastSampleMs = nowMs), prevScore, wasMoving)
    }

    val dtSec = dtMs / 1000f
    val dtHr = dtMs / 3_600_000f
    val spd = eff.speedKmh ?: 0f
    val nowMoving = spd > 1.5f
    val distDeltaKm = if (nowMoving) spd * dtHr else 0f

    val p1 = if (eff.b1Vbat != null && eff.b1Ibat != null) eff.b1Vbat * eff.b1Ibat else 0f
    val p2 = if (eff.b2Vbat != null && eff.b2Ibat != null) eff.b2Vbat * eff.b2Ibat else 0f
    val totalI = eff.combinedCurrentA ?: 0f
    val totalP = -(p1 + p2) // positive = discharge power, negative = regen/charge power

    var energyAdd = 0f
    var ahAdd = 0f
    var regenAdd = 0f
    var chargeAdd = 0f

    if (totalI < 0f) {
      // Discharging
      energyAdd = abs(totalP) * dtHr
      ahAdd = abs(totalI) * dtHr
    } else if (totalI > 0f && spd > 1.0f) {
      // Regenerative braking
      regenAdd = abs(totalP) * dtHr
    } else if (totalI > 0f && spd <= 1.0f) {
      // Plugged-in charging
      chargeAdd = abs(totalP) * dtHr
    }

    val stopInc = if (wasMoving && !nowMoving && trip.distanceKm > 0.05f) 1 else 0
    val moveIncSec = if (nowMoving) kotlin.math.round(dtSec).toLong().coerceAtLeast(1L) else 0L

    val nextTrip =
      trip.copy(
        distanceKm = trip.distanceKm + distDeltaKm,
        energyWh = trip.energyWh + energyAdd,
        consumedAh = trip.consumedAh + ahAdd,
        regenWh = trip.regenWh + regenAdd,
        chargeWh = trip.chargeWh + chargeAdd,
        maxSpeedKmh = maxOf(trip.maxSpeedKmh, spd),
        maxPowerW = maxOf(trip.maxPowerW, if (totalP > 0f) totalP else 0f),
        maxRegenW = maxOf(trip.maxRegenW, if (eff.isRegen) abs(totalP) else 0f),
        movingSeconds = trip.movingSeconds + moveIncSec,
        stopCount = trip.stopCount + stopInc,
        lastSampleMs = nowMs,
        startSocPct = trip.startSocPct ?: eff.combinedSocPct,
      )

    // Riding score update
    val nextScore =
      if (eff.speedKmh != null && (nowMoving || abs(totalP) > 50f)) {
        prevScore.copy(
          samples = prevScore.samples + 1,
          ecoViolations = prevScore.ecoViolations + if (totalP > 2500f) 1 else 0,
          hardBrakes = prevScore.hardBrakes + if (prevScore.lastSpeedKmh - spd > 15f) 1 else 0,
          regenEvents = prevScore.regenEvents + if (totalI > 0f && spd > 5f) 1 else 0,
          lastSpeedKmh = spd,
        )
      } else {
        prevScore.copy(lastSpeedKmh = spd)
      }

    return Triple(nextTrip, nextScore, nowMoving)
  }

  fun evaluateAlarms(
    eff: EffectiveTelemetry,
    jk: JkBmsData,
    b2: Battery2Data,
    settings: DashboardSettings,
  ): List<AppAlert> {
    if (!settings.alarmMasterOn) return emptyList()
    val alerts = mutableListOf<AppAlert>()

    val soc = eff.combinedSocPct
    if (settings.alarmSocLowOn && soc != null && soc < settings.alarmSocThreshold) {
      alerts.add(
        AppAlert(
          id = "soc_low",
          title = "Baterai Lemah (${soc.roundToInt()}%)",
          message = "SOC di bawah batas ${settings.alarmSocThreshold}%. Segera cari SPKLU terdekat.",
          severity = if (soc <= 10f) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
        )
      )
    }

    val maxTemp = listOfNotNull(eff.b1TempC, eff.b2TempC, jk.tempMosC).maxOrNull()
    if (settings.alarmTempHighOn && maxTemp != null && maxTemp > settings.alarmTempThresholdC) {
      alerts.add(
        AppAlert(
          id = "temp_high",
          title = "Suhu Baterai Tinggi (${maxTemp.roundToInt()}°C)",
          message = "Melebihi ambang ${settings.alarmTempThresholdC}°C. Kurangi beban akselerasi.",
          severity = AlertSeverity.WARNING,
        )
      )
    }

    val minSoh = listOfNotNull(eff.b1SohPct, b2.sohPct).minOrNull()
    if (settings.alarmSohLowOn && minSoh != null && minSoh < 75f) {
      alerts.add(
        AppAlert(
          id = "soh_low",
          title = "Kesehatan Baterai Menurun (${minSoh.roundToInt()}%)",
          message = "SOH di bawah 75%, pertimbangkan pengecekan sel baterai.",
          severity = AlertSeverity.INFO,
        )
      )
    }

    val maxDelta = listOfNotNull(jk.deltaCellV, b2.deltaCellV).maxOrNull()
    if (settings.alarmDeltaCellOn && maxDelta != null && maxDelta > 0.080f) {
      alerts.add(
        AppAlert(
          id = "delta_cell",
          title = "Ketidakseimbangan Sel (${String.format("%.3f", maxDelta)} V)",
          message = "Delta voltase sel > 80 mV. Lakukan balancing charger hingga 100%.",
          severity = AlertSeverity.WARNING,
        )
      )
    }

    if (eff.activeErrors.isNotEmpty()) {
      alerts.add(
        AppAlert(
          id = "motor_err",
          title = "${eff.activeErrors.size} Error Motor Aktif",
          message = eff.activeErrors.joinToString { it.label },
          severity = AlertSeverity.CRITICAL,
        )
      )
    }

    return alerts
  }

  fun computeMaintenanceStatus(odoKm: Float?, settings: DashboardSettings): MaintenanceStatus? {
    if (settings.maintLastOdoKm <= 0f) return null
    val cur = odoKm ?: 0f
    val interval = settings.maintIntervalKm.coerceAtLeast(100f)
    val next = settings.maintLastOdoKm + interval
    val remaining = (next - cur).coerceAtLeast(0f)
    val overdue = (cur - next).coerceAtLeast(0f)
    val pct = ((cur - settings.maintLastOdoKm) / interval).coerceIn(0f, 1f)
    val level =
      when {
        pct >= 1f -> MaintHealthLevel.DUE
        pct >= 0.85f -> MaintHealthLevel.WARN
        else -> MaintHealthLevel.OK
      }
    return MaintenanceStatus(
      lastServiceOdoKm = settings.maintLastOdoKm,
      intervalKm = interval,
      nextServiceOdoKm = next,
      remainingKm = remaining,
      overdueKm = overdue,
      progressPct = pct,
      level = level,
    )
  }

  fun computeFuelSavings(totalKm: Double, totalEnergyWh: Double): FuelSavingsSummary {
    val petrolLiters = totalKm / 40.0
    val petrolCost = kotlin.math.round(petrolLiters * 10_000.0).toLong()
    val elecKwh = totalEnergyWh / 1000.0
    val elecCost = kotlin.math.round(elecKwh * 1447.0).toLong()
    val petrolCo2Kg = petrolLiters * 2.31
    val gridCo2Kg = elecKwh * 0.724
    return FuelSavingsSummary(
      totalKm = totalKm,
      petrolCostRp = petrolCost,
      electricityCostRp = elecCost,
      netSavingsRp = (petrolCost - elecCost).coerceAtLeast(0L),
      co2SavedKg = (petrolCo2Kg - gridCo2Kg).coerceAtLeast(0.0),
    )
  }

  fun buildAtNavSlots(
    eff: EffectiveTelemetry,
    trip: LiveTripMetrics,
    roadName: String,
    areaName: String,
    weatherMood: String,
    slots: AtNavSlotConfig,
  ): List<String> {
    val out = mutableListOf<String>()
    if (slots.showRange) out.add("RNG ${String.format("%.1f", eff.rangeKm)} km")
    if (slots.showTrip) out.add("TRP ${String.format("%.1f", trip.distanceKm)} km")
    if (slots.showEnergy) out.add("ENG ${trip.energyWh.roundToInt()} Wh")
    if (slots.showEfficiency) out.add("EFF ${eff.blendedWhPerKm.roundToInt()}Wh/km")
    if (slots.showPower && eff.combinedPowerW != null) {
      out.add("PWR ${eff.combinedPowerW.roundToInt()} W")
    }
    if (slots.showCo2) {
      val savedKg = (trip.distanceKm / 40f * 2.31f) - (trip.energyWh / 1000f * 0.724f)
      out.add("CO2 -${String.format("%.2f", savedKg.coerceAtLeast(0f))}kg")
    }
    if (slots.showLocation && (roadName.isNotBlank() || areaName.isNotBlank())) {
      val loc = listOf(roadName, areaName).filter { it.isNotBlank() }.joinToString(", ")
      out.add(loc.take(20))
    }
    if (slots.showWeather && weatherMood.isNotBlank()) {
      out.add(weatherMood.take(20))
    }
    return out
  }
}
