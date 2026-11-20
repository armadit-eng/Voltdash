package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BatterySourcePriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gesits_prefs")

data class AtNavSlotConfig(
  val showRange: Boolean = true,
  val showTrip: Boolean = true,
  val showEnergy: Boolean = true,
  val showEfficiency: Boolean = true,
  val showCo2: Boolean = true,
  val showLocation: Boolean = true,
  val showWeather: Boolean = true,
  val showElevation: Boolean = true,
  val showPower: Boolean = true,
)

data class DashboardSettings(
  val riderName: String = "Rider GESITS",
  val darkTheme: Boolean = true,
  val accentThemeId: String = "default",
  val garageLiveryId: String = "default",
  val b1NominalV: Float = 72f,
  val b1CapacityAh: Float = 20f,
  val b2NominalV: Float = 72f,
  val b2CapacityAh: Float = 20f,
  val b2InvertCurrent: Boolean = false,
  val refWhPerKm: Float = 28f,
  val physicsBikeKg: Float = 100f,
  val physicsRiderKg: Float = 65f,
  val physicsLoadKg: Float = 0f,
  val physicsCalFactor: Float = 1.0f,
  val alarmMasterOn: Boolean = true,
  val alarmSocLowOn: Boolean = true,
  val alarmSocThreshold: Int = 20,
  val alarmTempHighOn: Boolean = false,
  val alarmTempThresholdC: Int = 45,
  val alarmSohLowOn: Boolean = true,
  val alarmDeltaCellOn: Boolean = true,
  val batteryPriority: BatterySourcePriority = BatterySourcePriority.MOTOR,
  val atNavEnabled: Boolean = true,
  val atNavIntervalSec: Int = 4,
  val atNavSlots: AtNavSlotConfig = AtNavSlotConfig(),
  val matrixEnabled: Boolean = true,
  val matrixIntervalSec: Int = 5,
  val odoBackupKm: Float? = null,
  val maintLastOdoKm: Float = 0f,
  val maintIntervalKm: Float = 2000f,
  val weeklyGoalKm: Float = 100f,
  val lastMotorAddr: String = "",
  val lastMotorName: String = "",
  val lastJkAddr: String = "",
  val lastJkName: String = "",
  val lastEsp1Addr: String = "",
  val lastEsp1Name: String = "",
  val lastEsp2Addr: String = "",
  val lastEsp2Name: String = "",
  val parkedLat: Double? = null,
  val parkedLng: Double? = null,
  val parkedLabel: String = "",
  val parkedAtMs: Long = 0L,
  val devPin: String = "",
  // AT+MAPS & Screen Mirroring
  val atMapsEnabled: Boolean = true,
  val mapsSnapWidth: Int = 350,
  val mapsSnapHeight: Int = 220,
  val mapsMaxChars: Int = 7000,
  val mapsLayout: String = "full", // "full" or "split"
  val mapsRiderPos: String = "low", // "low" or "center"
  val leMapsChunk: Int = 100,
  val leMapsDelayMs: Int = 30,
  val customWallpaperUri: String = "",
  val customWallpaperEnabled: Boolean = false,
  val mirrorLeftPct: Int = 0,
  val mirrorRightPct: Int = 100,
  val mirrorTopPct: Int = 10,
  val mirrorBottomPct: Int = 78,
  val mirrorStripEnabled: Boolean = true,
  val mirrorStripPos: String = "top",
)

class SettingsRepository(private val context: Context) {
  private object Keys {
    val RIDER_NAME = stringPreferencesKey("rider_name")
    val DARK_THEME = booleanPreferencesKey("dark_theme")
    val ACCENT_THEME = stringPreferencesKey("accent_theme")
    val GARAGE_LIVERY = stringPreferencesKey("garage_livery")
    val B1_NOM_V = floatPreferencesKey("b1_nom_v")
    val B1_CAP_AH = floatPreferencesKey("b1_cap_ah")
    val B2_NOM_V = floatPreferencesKey("b2_nom_v")
    val B2_CAP_AH = floatPreferencesKey("b2_cap_ah")
    val B2_INVERT = booleanPreferencesKey("b2_invert_current")
    val REF_WH_KM = floatPreferencesKey("ref_wh_km")
    val PHYS_BIKE_KG = floatPreferencesKey("phys_bike_kg")
    val PHYS_RIDER_KG = floatPreferencesKey("phys_rider_kg")
    val PHYS_LOAD_KG = floatPreferencesKey("phys_load_kg")
    val PHYS_CAL = floatPreferencesKey("phys_cal")
    val ALARM_MASTER = booleanPreferencesKey("alarm_master")
    val ALARM_SOC_LOW = booleanPreferencesKey("alarm_soc_low")
    val ALARM_SOC_THRESH = intPreferencesKey("alarm_soc_thresh")
    val ALARM_TEMP_HIGH = booleanPreferencesKey("alarm_temp_high")
    val ALARM_TEMP_THRESH = intPreferencesKey("alarm_temp_thresh")
    val ALARM_SOH_LOW = booleanPreferencesKey("alarm_soh_low")
    val ALARM_DELTA_CELL = booleanPreferencesKey("alarm_delta_cell")
    val BATT_PRIORITY = stringPreferencesKey("batt_priority")
    val AT_NAV_ENABLED = booleanPreferencesKey("at_nav_enabled")
    val AT_NAV_INTERVAL = intPreferencesKey("at_nav_interval")
    val AT_NAV_SLOTS_MASK = intPreferencesKey("at_nav_slots_mask")
    val MATRIX_ENABLED = booleanPreferencesKey("matrix_enabled")
    val MATRIX_INTERVAL = intPreferencesKey("matrix_interval")
    val ODO_BACKUP = floatPreferencesKey("odo_backup")
    val MAINT_LAST_ODO = floatPreferencesKey("maint_last_odo")
    val MAINT_INTERVAL = floatPreferencesKey("maint_interval")
    val WEEKLY_GOAL_KM = floatPreferencesKey("weekly_goal_km")
    val LAST_MOTOR_ADDR = stringPreferencesKey("last_motor_addr")
    val LAST_MOTOR_NAME = stringPreferencesKey("last_motor_name")
    val LAST_JK_ADDR = stringPreferencesKey("last_jk_addr")
    val LAST_JK_NAME = stringPreferencesKey("last_jk_name")
    val LAST_ESP1_ADDR = stringPreferencesKey("last_esp1_addr")
    val LAST_ESP1_NAME = stringPreferencesKey("last_esp1_name")
    val LAST_ESP2_ADDR = stringPreferencesKey("last_esp2_addr")
    val LAST_ESP2_NAME = stringPreferencesKey("last_esp2_name")
    val PARKED_LAT = doublePreferencesKey("parked_lat")
    val PARKED_LNG = doublePreferencesKey("parked_lng")
    val PARKED_LABEL = stringPreferencesKey("parked_label")
    val PARKED_AT_MS = longPreferencesKey("parked_at_ms")
    val DEV_PIN = stringPreferencesKey("dev_pin")
    val AT_MAPS_ENABLED = booleanPreferencesKey("at_maps_enabled")
    val MAPS_W = intPreferencesKey("maps_w")
    val MAPS_H = intPreferencesKey("maps_h")
    val MAPS_MAX_CHARS = intPreferencesKey("maps_max_chars")
    val MAPS_LAYOUT = stringPreferencesKey("maps_layout")
    val MAPS_RIDER_POS = stringPreferencesKey("maps_rider_pos")
    val LE_MAPS_CHUNK = intPreferencesKey("le_maps_chunk")
    val LE_MAPS_DELAY = intPreferencesKey("le_maps_delay")
    val CUSTOM_WALLPAPER_URI = stringPreferencesKey("custom_wallpaper_uri")
    val CUSTOM_WALLPAPER_ENABLED = booleanPreferencesKey("custom_wallpaper_enabled")
    val MIRROR_LEFT = intPreferencesKey("mirror_left")
    val MIRROR_RIGHT = intPreferencesKey("mirror_right")
    val MIRROR_TOP = intPreferencesKey("mirror_top")
    val MIRROR_BOTTOM = intPreferencesKey("mirror_bottom")
    val MIRROR_STRIP = booleanPreferencesKey("mirror_strip")
    val MIRROR_STRIP_POS = stringPreferencesKey("mirror_strip_pos")
  }

  val settingsFlow: Flow<DashboardSettings> =
    context.dataStore.data.map { prefs ->
      val mask = prefs[Keys.AT_NAV_SLOTS_MASK] ?: 0x1FF
      DashboardSettings(
        riderName = prefs[Keys.RIDER_NAME] ?: "Rider GESITS",
        darkTheme = prefs[Keys.DARK_THEME] ?: true,
        accentThemeId = prefs[Keys.ACCENT_THEME] ?: "default",
        garageLiveryId = prefs[Keys.GARAGE_LIVERY] ?: "default",
        b1NominalV = prefs[Keys.B1_NOM_V] ?: 72f,
        b1CapacityAh = prefs[Keys.B1_CAP_AH] ?: 20f,
        b2NominalV = prefs[Keys.B2_NOM_V] ?: 72f,
        b2CapacityAh = prefs[Keys.B2_CAP_AH] ?: 20f,
        b2InvertCurrent = prefs[Keys.B2_INVERT] ?: false,
        refWhPerKm = prefs[Keys.REF_WH_KM] ?: 28f,
        physicsBikeKg = prefs[Keys.PHYS_BIKE_KG] ?: 100f,
        physicsRiderKg = prefs[Keys.PHYS_RIDER_KG] ?: 65f,
        physicsLoadKg = prefs[Keys.PHYS_LOAD_KG] ?: 0f,
        physicsCalFactor = prefs[Keys.PHYS_CAL] ?: 1.0f,
        alarmMasterOn = prefs[Keys.ALARM_MASTER] ?: true,
        alarmSocLowOn = prefs[Keys.ALARM_SOC_LOW] ?: true,
        alarmSocThreshold = prefs[Keys.ALARM_SOC_THRESH] ?: 20,
        alarmTempHighOn = prefs[Keys.ALARM_TEMP_HIGH] ?: false,
        alarmTempThresholdC = prefs[Keys.ALARM_TEMP_THRESH] ?: 45,
        alarmSohLowOn = prefs[Keys.ALARM_SOH_LOW] ?: true,
        alarmDeltaCellOn = prefs[Keys.ALARM_DELTA_CELL] ?: true,
        batteryPriority =
          if (prefs[Keys.BATT_PRIORITY] == BatterySourcePriority.JIKONG.id) {
            BatterySourcePriority.JIKONG
          } else {
            BatterySourcePriority.MOTOR
          },
        atNavEnabled = prefs[Keys.AT_NAV_ENABLED] ?: true,
        atNavIntervalSec = prefs[Keys.AT_NAV_INTERVAL] ?: 4,
        atNavSlots = decodeSlotsMask(mask),
        matrixEnabled = prefs[Keys.MATRIX_ENABLED] ?: true,
        matrixIntervalSec = prefs[Keys.MATRIX_INTERVAL] ?: 5,
        odoBackupKm = prefs[Keys.ODO_BACKUP],
        maintLastOdoKm = prefs[Keys.MAINT_LAST_ODO] ?: 0f,
        maintIntervalKm = prefs[Keys.MAINT_INTERVAL] ?: 2000f,
        weeklyGoalKm = prefs[Keys.WEEKLY_GOAL_KM] ?: 100f,
        lastMotorAddr = prefs[Keys.LAST_MOTOR_ADDR] ?: "",
        lastMotorName = prefs[Keys.LAST_MOTOR_NAME] ?: "",
        lastJkAddr = prefs[Keys.LAST_JK_ADDR] ?: "",
        lastJkName = prefs[Keys.LAST_JK_NAME] ?: "",
        lastEsp1Addr = prefs[Keys.LAST_ESP1_ADDR] ?: "",
        lastEsp1Name = prefs[Keys.LAST_ESP1_NAME] ?: "",
        lastEsp2Addr = prefs[Keys.LAST_ESP2_ADDR] ?: "",
        lastEsp2Name = prefs[Keys.LAST_ESP2_NAME] ?: "",
        parkedLat = prefs[Keys.PARKED_LAT],
        parkedLng = prefs[Keys.PARKED_LNG],
        parkedLabel = prefs[Keys.PARKED_LABEL] ?: "",
        parkedAtMs = prefs[Keys.PARKED_AT_MS] ?: 0L,
        devPin = prefs[Keys.DEV_PIN] ?: "",
        atMapsEnabled = prefs[Keys.AT_MAPS_ENABLED] ?: true,
        mapsSnapWidth = prefs[Keys.MAPS_W] ?: 350,
        mapsSnapHeight = prefs[Keys.MAPS_H] ?: 220,
        mapsMaxChars = prefs[Keys.MAPS_MAX_CHARS] ?: 7000,
        mapsLayout = prefs[Keys.MAPS_LAYOUT] ?: "full",
        mapsRiderPos = prefs[Keys.MAPS_RIDER_POS] ?: "low",
        leMapsChunk = prefs[Keys.LE_MAPS_CHUNK] ?: 100,
        leMapsDelayMs = prefs[Keys.LE_MAPS_DELAY] ?: 30,
        customWallpaperUri = prefs[Keys.CUSTOM_WALLPAPER_URI] ?: "",
        customWallpaperEnabled = prefs[Keys.CUSTOM_WALLPAPER_ENABLED] ?: false,
        mirrorLeftPct = prefs[Keys.MIRROR_LEFT] ?: 0,
        mirrorRightPct = prefs[Keys.MIRROR_RIGHT] ?: 100,
        mirrorTopPct = prefs[Keys.MIRROR_TOP] ?: 10,
        mirrorBottomPct = prefs[Keys.MIRROR_BOTTOM] ?: 78,
        mirrorStripEnabled = prefs[Keys.MIRROR_STRIP] ?: true,
        mirrorStripPos = prefs[Keys.MIRROR_STRIP_POS] ?: "top",
      )
    }

  suspend fun updateRiderName(name: String) {
    context.dataStore.edit { it[Keys.RIDER_NAME] = name.trim().ifEmpty { "Rider GESITS" } }
  }

  suspend fun setDarkTheme(dark: Boolean) {
    context.dataStore.edit { it[Keys.DARK_THEME] = dark }
  }

  suspend fun setAccentTheme(themeId: String) {
    context.dataStore.edit { it[Keys.ACCENT_THEME] = themeId }
  }

  suspend fun setGarageLivery(liveryId: String) {
    context.dataStore.edit { it[Keys.GARAGE_LIVERY] = liveryId }
  }

  suspend fun setBatterySpecs(b1V: Float, b1Ah: Float, b2V: Float, b2Ah: Float, refWhKm: Float) {
    context.dataStore.edit {
      it[Keys.B1_NOM_V] = b1V.coerceIn(36f, 120f)
      it[Keys.B1_CAP_AH] = b1Ah.coerceIn(5f, 200f)
      it[Keys.B2_NOM_V] = b2V.coerceIn(36f, 120f)
      it[Keys.B2_CAP_AH] = b2Ah.coerceIn(5f, 200f)
      it[Keys.REF_WH_KM] = refWhKm.coerceIn(10f, 90f)
    }
  }

  suspend fun setB2InvertCurrent(invert: Boolean) {
    context.dataStore.edit { it[Keys.B2_INVERT] = invert }
  }

  suspend fun setPhysics(bikeKg: Float, riderKg: Float, loadKg: Float, cal: Float) {
    context.dataStore.edit {
      it[Keys.PHYS_BIKE_KG] = bikeKg.coerceIn(50f, 300f)
      it[Keys.PHYS_RIDER_KG] = riderKg.coerceIn(30f, 200f)
      it[Keys.PHYS_LOAD_KG] = loadKg.coerceIn(0f, 150f)
      it[Keys.PHYS_CAL] = cal.coerceIn(0.5f, 2.0f)
    }
  }

  suspend fun setAlarms(
    masterOn: Boolean,
    socLowOn: Boolean,
    socThresh: Int,
    tempHighOn: Boolean,
    tempThresh: Int,
    sohLowOn: Boolean,
    deltaCellOn: Boolean,
  ) {
    context.dataStore.edit {
      it[Keys.ALARM_MASTER] = masterOn
      it[Keys.ALARM_SOC_LOW] = socLowOn
      it[Keys.ALARM_SOC_THRESH] = socThresh.coerceIn(5, 50)
      it[Keys.ALARM_TEMP_HIGH] = tempHighOn
      it[Keys.ALARM_TEMP_THRESH] = tempThresh.coerceIn(35, 75)
      it[Keys.ALARM_SOH_LOW] = sohLowOn
      it[Keys.ALARM_DELTA_CELL] = deltaCellOn
    }
  }

  suspend fun setBatteryPriority(priority: BatterySourcePriority) {
    context.dataStore.edit { it[Keys.BATT_PRIORITY] = priority.id }
  }

  suspend fun setAtNavConfig(enabled: Boolean, intervalSec: Int, slots: AtNavSlotConfig) {
    context.dataStore.edit {
      it[Keys.AT_NAV_ENABLED] = enabled
      it[Keys.AT_NAV_INTERVAL] = intervalSec.coerceIn(2, 15)
      it[Keys.AT_NAV_SLOTS_MASK] = encodeSlotsMask(slots)
    }
  }

  suspend fun setMatrixConfig(enabled: Boolean, intervalSec: Int) {
    context.dataStore.edit {
      it[Keys.MATRIX_ENABLED] = enabled
      it[Keys.MATRIX_INTERVAL] = intervalSec.coerceIn(2, 20)
    }
  }

  suspend fun saveOdoBackup(odoKm: Float) {
    if (odoKm > 0f && odoKm < 400_000f) {
      context.dataStore.edit { it[Keys.ODO_BACKUP] = odoKm }
    }
  }

  suspend fun saveMaintenanceSettings(lastOdoKm: Float, intervalKm: Float) {
    context.dataStore.edit {
      it[Keys.MAINT_LAST_ODO] = lastOdoKm.coerceAtLeast(0f)
      it[Keys.MAINT_INTERVAL] = intervalKm.coerceAtLeast(100f)
    }
  }

  suspend fun setWeeklyGoalKm(goalKm: Float) {
    context.dataStore.edit { it[Keys.WEEKLY_GOAL_KM] = goalKm.coerceIn(10f, 2000f) }
  }

  suspend fun saveLastDevice(slot: String, name: String, addr: String) {
    context.dataStore.edit {
      when (slot) {
        "motor" -> {
          it[Keys.LAST_MOTOR_NAME] = name
          it[Keys.LAST_MOTOR_ADDR] = addr
        }
        "jikong" -> {
          it[Keys.LAST_JK_NAME] = name
          it[Keys.LAST_JK_ADDR] = addr
        }
        "esp1" -> {
          it[Keys.LAST_ESP1_NAME] = name
          it[Keys.LAST_ESP1_ADDR] = addr
        }
        "esp2" -> {
          it[Keys.LAST_ESP2_NAME] = name
          it[Keys.LAST_ESP2_ADDR] = addr
        }
      }
    }
  }

  suspend fun saveParkedLocation(lat: Double, lng: Double, label: String) {
    context.dataStore.edit {
      it[Keys.PARKED_LAT] = lat
      it[Keys.PARKED_LNG] = lng
      it[Keys.PARKED_LABEL] = label
      it[Keys.PARKED_AT_MS] = System.currentTimeMillis()
    }
  }

  suspend fun clearParkedLocation() {
    context.dataStore.edit {
      it.remove(Keys.PARKED_LAT)
      it.remove(Keys.PARKED_LNG)
      it.remove(Keys.PARKED_LABEL)
      it.remove(Keys.PARKED_AT_MS)
    }
  }

  suspend fun saveDevPin(pin: String) {
    context.dataStore.edit { it[Keys.DEV_PIN] = pin }
  }

  suspend fun setAtMapsConfig(
    enabled: Boolean,
    width: Int,
    height: Int,
    maxChars: Int,
    layout: String,
    riderPos: String,
    leChunk: Int,
    leDelayMs: Int,
  ) {
    context.dataStore.edit {
      it[Keys.AT_MAPS_ENABLED] = enabled
      it[Keys.MAPS_W] = width.coerceIn(160, 480)
      it[Keys.MAPS_H] = height.coerceIn(120, 360)
      it[Keys.MAPS_MAX_CHARS] = maxChars.coerceIn(2500, 12000)
      it[Keys.MAPS_LAYOUT] = if (layout == "split") "split" else "full"
      it[Keys.MAPS_RIDER_POS] = if (riderPos == "center") "center" else "low"
      it[Keys.LE_MAPS_CHUNK] = leChunk.coerceIn(20, 400)
      it[Keys.LE_MAPS_DELAY] = leDelayMs.coerceIn(0, 300)
    }
  }

  suspend fun setCustomWallpaper(uri: String, enabled: Boolean) {
    context.dataStore.edit {
      it[Keys.CUSTOM_WALLPAPER_URI] = uri
      it[Keys.CUSTOM_WALLPAPER_ENABLED] = enabled
    }
  }

  suspend fun setMirrorCropAndStrip(
    leftPct: Int,
    rightPct: Int,
    topPct: Int,
    bottomPct: Int,
    stripEnabled: Boolean,
    stripPos: String,
  ) {
    val l = leftPct.coerceIn(0, 89)
    val r = rightPct.coerceIn(l + 5, 100)
    val t = topPct.coerceIn(0, 89)
    val b = bottomPct.coerceIn(t + 5, 100)
    context.dataStore.edit {
      it[Keys.MIRROR_LEFT] = l
      it[Keys.MIRROR_RIGHT] = r
      it[Keys.MIRROR_TOP] = t
      it[Keys.MIRROR_BOTTOM] = b
      it[Keys.MIRROR_STRIP] = stripEnabled
      it[Keys.MIRROR_STRIP_POS] = if (stripPos == "bottom") "bottom" else "top"
    }
  }

  private fun encodeSlotsMask(s: AtNavSlotConfig): Int {
    var m = 0
    if (s.showRange) m = m or 1
    if (s.showTrip) m = m or 2
    if (s.showEnergy) m = m or 4
    if (s.showEfficiency) m = m or 8
    if (s.showCo2) m = m or 16
    if (s.showLocation) m = m or 32
    if (s.showWeather) m = m or 64
    if (s.showElevation) m = m or 128
    if (s.showPower) m = m or 256
    return m
  }

  private fun decodeSlotsMask(m: Int): AtNavSlotConfig =
    AtNavSlotConfig(
      showRange = (m and 1) != 0,
      showTrip = (m and 2) != 0,
      showEnergy = (m and 4) != 0,
      showEfficiency = (m and 8) != 0,
      showCo2 = (m and 16) != 0,
      showLocation = (m and 32) != 0,
      showWeather = (m and 64) != 0,
      showElevation = (m and 128) != 0,
      showPower = (m and 256) != 0,
    )
}
