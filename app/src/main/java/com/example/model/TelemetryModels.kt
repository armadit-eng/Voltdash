package com.example.model

enum class ConnectionState {
  DISCONNECTED,
  SCANNING,
  CONNECTING,
  CONNECTED,
}

enum class BatterySourcePriority(val id: String, val label: String) {
  MOTOR("motor", "Motor MID"),
  JIKONG("jikong", "JK-BMS"),
}

data class DiscoveredDevice(
  val name: String,
  val address: String,
  val isBle: Boolean,
  val rssi: Int = 0,
  val isPaired: Boolean = false,
)

data class MotorData(
  val speedKmh: Float? = null,
  val vbat: Float? = null,
  val ibat: Float? = null,
  val throttlePct: Float? = null,
  val socPct: Float? = null,
  val sohPct: Float? = null,
  val cbatCycles: Int? = null,
  val odoKm: Float? = null,
  val errMask: Int = 0,
  val modeIndex: Int? = null, // 0=Eco, 1=Urban, 2=Sport
  val isPark: Boolean? = null, // true=PARK, false=DRIVE
  val tempInverterC: Float? = null,
  val tempMotorC: Float? = null,
  val tempBatteryC: Float? = null,
  val lastUpdatedMs: Long = 0L,
) {
  val modeLabel: String
    get() =
      when (modeIndex) {
        0 -> "ECO"
        1 -> "URBAN"
        2 -> "SPORT"
        else -> "—"
      }

  val rpm: Int?
    get() = speedKmh?.let { kotlin.math.round(it * 51.45f).toInt() }
}

data class JkBmsData(
  val vpack: Float? = null,
  val ibat: Float? = null,
  val socPct: Float? = null,
  val sohPct: Float? = null,
  val temp1C: Float? = null,
  val temp2C: Float? = null,
  val tempMosC: Float? = null,
  val deltaCellV: Float? = null,
  val minCellV: Float? = null,
  val maxCellV: Float? = null,
  val minCellIndex: Int = -1,
  val maxCellIndex: Int = -1,
  val cellCount: Int = 0,
  val cellVoltages: List<Float> = emptyList(),
  val cycles: Int? = null,
  val remainAh: Float? = null,
  val capacityAh: Float? = null,
  val lastUpdatedMs: Long = 0L,
)

data class Battery2Data(
  val vbat: Float? = null,
  val ibat: Float? = null,
  val powerW: Float? = null,
  val socPct: Float? = null,
  val sohPct: Float? = null,
  val tempC: Float? = null,
  val deltaCellV: Float? = null,
  val cellCount: Int = 0,
  val cellVoltages: List<Float> = emptyList(),
  val cycles: Int? = null,
  val remainAh: Float? = null,
  val canMode: String = "LISTEN",
  val simMode: String? = null,
  val lastUpdatedMs: Long = 0L,
)

data class Esp32Data(
  val speedKmh: Float? = null,
  val throttlePct: Float? = null,
  val cruiseActive: Boolean = false,
  val reverseActive: Boolean = false,
  val sideStandDown: Boolean = false,
  val modeIndex: Int? = null,
  val isPark: Boolean? = null,
  val contactOn: Boolean? = null,
  val matrixOn: Boolean? = null,
  val musicTrack: Int = 1,
  val musicState: String = "STOP",
  val musicVol: Int = 20,
  val lastUpdatedMs: Long = 0L,
)

data class ErrorDefinition(
  val bit: Int,
  val label: String,
  val fix: String,
)

val ERROR_DEFS =
  listOf(
    ErrorDefinition(
      0,
      "Inverter Over Temperature",
      "Diamkan motor hingga suhu inverter normal, hindari pemakaian berat terus-menerus.",
    ),
    ErrorDefinition(
      1,
      "Motor Over Temperature",
      "Istirahatkan motor sampai suhu motor turun ke level normal.",
    ),
    ErrorDefinition(
      2,
      "Motor Fault",
      "Periksa kabel & konektor motor, bawa ke bengkel resmi jika berulang.",
    ),
    ErrorDefinition(
      3,
      "Hall Sensor Fault",
      "Cek konektor sensor hall pada motor, kemungkinan longgar/kotor.",
    ),
    ErrorDefinition(
      4,
      "Throttle Fault",
      "Periksa posisi & kabel throttle, pastikan tidak tersangkut.",
    ),
    ErrorDefinition(
      5,
      "Controller Fault",
      "Restart sistem (matikan-nyalakan). Jika tetap, bawa ke bengkel resmi.",
    ),
    ErrorDefinition(
      6,
      "Over Voltage",
      "Periksa charger & sumber listrik, pastikan tegangan sesuai spesifikasi.",
    ),
    ErrorDefinition(7, "Under Voltage", "Segera cas baterai, tegangan baterai terlalu rendah."),
    ErrorDefinition(
      11,
      "Throttle Signal Abnormal (belum terverifikasi resmi)",
      "Cek kabel & konektor throttle. Kalau berulang, laporkan kode error ini ke bengkel resmi GESITS.",
    ),
  )

data class LiveTripMetrics(
  val startTimeMs: Long = 0L,
  val distanceKm: Float = 0f,
  val energyWh: Float = 0f,
  val consumedAh: Float = 0f,
  val regenWh: Float = 0f,
  val chargeWh: Float = 0f,
  val maxSpeedKmh: Float = 0f,
  val maxPowerW: Float = 0f,
  val maxRegenW: Float = 0f,
  val movingSeconds: Long = 0L,
  val stopCount: Int = 0,
  val lastSampleMs: Long = 0L,
  val startSocPct: Float? = null,
) {
  val rawWhPerKm: Float
    get() = if (distanceKm > 0.05f) energyWh / distanceKm else 0f

  val avgSpeedKmh: Float
    get() = if (movingSeconds > 0L) distanceKm / (movingSeconds / 3600f) else 0f

  val netWhPerKm: Float
    get() = if (distanceKm > 0.05f) (energyWh - regenWh).coerceAtLeast(0f) / distanceKm else 0f
}

data class RidingScoreState(
  val samples: Int = 0,
  val ecoViolations: Int = 0,
  val hardBrakes: Int = 0,
  val regenEvents: Int = 0,
  val lastSpeedKmh: Float = 0f,
) {
  val score: Int?
    get() {
      if (samples < 3) return null
      val ecoRatio = 1f - (ecoViolations.toFloat() / samples)
      val brakeRatio = 1f - (hardBrakes.toFloat() / (samples * 0.05f)).coerceAtMost(1f)
      val regenRatio = (regenEvents.toFloat() / (samples * 0.10f)).coerceAtMost(1f)
      return kotlin.math.round(ecoRatio * 50f + brakeRatio * 30f + regenRatio * 20f).toInt()
        .coerceIn(0, 100)
    }

  val label: String
    get() =
      when (val s = score) {
        null -> "Belum ada data"
        in 80..100 -> "Efisien"
        in 60..79 -> "Normal"
        else -> "Boros"
      }
}

data class EffectiveTelemetry(
  val speedKmh: Float? = null,
  val rpm: Int? = null,
  val throttlePct: Float? = null,
  val b1SocPct: Float? = null,
  val b1Vbat: Float? = null,
  val b1Ibat: Float? = null,
  val b1TempC: Float? = null,
  val b1SohPct: Float? = null,
  val b1SourceLabel: String = "—",
  val b2Active: Boolean = false,
  val b2SocPct: Float? = null,
  val b2Vbat: Float? = null,
  val b2Ibat: Float? = null,
  val b2TempC: Float? = null,
  val combinedSocPct: Float? = null,
  val combinedPowerW: Float? = null,
  val combinedCurrentA: Float? = null,
  val avgVoltageV: Float? = null,
  val isCharging: Boolean = false,
  val isRegen: Boolean = false,
  val rangeKm: Float = 0f,
  val blendedWhPerKm: Float = 28f,
  val totalAvailableWh: Float = 0f,
  val odoKm: Float? = null,
  val modeLabel: String = "—",
  val isPark: Boolean? = null,
  val sideStandDown: Boolean = false,
  val cruiseActive: Boolean = false,
  val reverseActive: Boolean = false,
  val activeErrors: List<ErrorDefinition> = emptyList(),
)

data class AppAlert(
  val id: String,
  val title: String,
  val message: String,
  val severity: AlertSeverity,
  val timestampMs: Long = System.currentTimeMillis(),
)

enum class AlertSeverity {
  INFO,
  WARNING,
  CRITICAL,
}

data class TelemetryChartPoint(
  val timestampMs: Long,
  val speedKmh: Float,
  val throttlePct: Float,
  val powerW: Float,
  val socPct: Float,
  val voltageV: Float,
  val tempC: Float,
)

data class SerialLogEntry(
  val timestampMs: Long,
  val source: String,
  val message: String,
  val isError: Boolean = false,
)
