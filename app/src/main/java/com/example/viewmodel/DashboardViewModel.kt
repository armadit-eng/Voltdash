package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.AtNavSlotConfig
import com.example.data.local.ChargeLogEntity
import com.example.data.local.DashboardSettings
import com.example.data.local.FavoritePlaceEntity
import com.example.data.local.FieldNoteEntity
import com.example.data.local.MaintenanceLogEntity
import com.example.data.local.SettingsRepository
import com.example.data.local.TripEntity
import com.example.data.remote.NetworkClient
import com.example.data.remote.OsmTileLoader
import com.example.domain.ChargingIndicatorState
import com.example.domain.RoutePhysics
import com.example.domain.TelemetryEngine
import com.example.hardware.Esp32BleManager
import com.example.hardware.JkBmsBleManager
import com.example.hardware.LocationTracker
import com.example.hardware.MidMapBitmapRenderer
import com.example.hardware.MirrorCaptureConfig
import com.example.hardware.MirrorFrameBus
import com.example.hardware.MotorBluetoothManager
import com.example.hardware.ScreenMirrorService
import com.example.model.AppAlert
import com.example.model.Battery2Data
import com.example.model.BatterySourcePriority
import com.example.model.ConnectionState
import com.example.model.DiscoveredDevice
import com.example.model.EffectiveTelemetry
import com.example.model.Esp32Data
import com.example.model.GeoPoint
import com.example.model.GpsState
import com.example.model.HourlyWeatherItem
import com.example.model.JkBmsData
import com.example.model.LiveTripMetrics
import com.example.model.MotorData
import com.example.model.MultiStopItem
import com.example.model.NavDestination
import com.example.model.PlaceSearchResult
import com.example.model.RidingScoreState
import com.example.model.RouteOption
import com.example.model.RouteStep
import com.example.model.SerialLogEntry
import com.example.model.SpkluStation
import com.example.model.TelemetryChartPoint
import com.example.model.WeatherState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

data class BreadcrumbPoint(
  val lat: Double,
  val lng: Double,
  val spd: Float,
  val pwr: Float,
)

data class NavUiState(
  val searchQuery: String = "",
  val isSearching: Boolean = false,
  val searchResults: List<PlaceSearchResult> = emptyList(),
  val destination: NavDestination? = null,
  val multiStops: List<MultiStopItem> = emptyList(),
  val isRouting: Boolean = false,
  val routeError: String? = null,
  val routes: List<RouteOption> = emptyList(),
  val selectedRouteIndex: Int = 0,
  val isNavigating: Boolean = false,
  val activeStepIndex: Int = 0,
  val distToNextStepM: Double = 0.0,
  val voiceEnabled: Boolean = true,
  val spkluLoading: Boolean = false,
  val spkluStations: List<SpkluStation> = emptyList(),
  val spkluError: String? = null,
  val rideLockActive: Boolean = false,
) {
  val activeRoute: RouteOption?
    get() = routes.getOrNull(selectedRouteIndex)
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
  private val db = AppDatabase.getInstance(application)
  private val settingsRepo = SettingsRepository(application)
  val tileLoader = OsmTileLoader()

  private val _serialLogs = MutableStateFlow<List<SerialLogEntry>>(emptyList())
  val serialLogs: StateFlow<List<SerialLogEntry>> = _serialLogs.asStateFlow()

  private fun appendLog(source: String, msg: String, isError: Boolean) {
    val entry = SerialLogEntry(System.currentTimeMillis(), source, msg, isError)
    _serialLogs.value = (_serialLogs.value + entry).takeLast(140)
  }

  val motorBt =
    MotorBluetoothManager(
      context = application,
      scope = viewModelScope,
      onLog = ::appendLog,
      onValidOdoBackup = { odo -> viewModelScope.launch { settingsRepo.saveOdoBackup(odo) } },
    )
  val jkBle = JkBmsBleManager(application, viewModelScope, ::appendLog)
  val esp32Ble = Esp32BleManager(application, viewModelScope, ::appendLog)
  val locationTracker = LocationTracker(application)

  val settings: StateFlow<DashboardSettings> =
    settingsRepo.settingsFlow.stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
      DashboardSettings(),
    )

  val trips: StateFlow<List<TripEntity>> =
    db.tripDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val maintenanceLogs: StateFlow<List<MaintenanceLogEntity>> =
    db.maintenanceLogDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val chargeLogs: StateFlow<List<ChargeLogEntity>> =
    db.chargeLogDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val favorites: StateFlow<List<FavoritePlaceEntity>> =
    db.favoritePlaceDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val fieldNotes: StateFlow<List<FieldNoteEntity>> =
    db.fieldNoteDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val motorState: StateFlow<ConnectionState> = motorBt.connectionState
  val motorName: StateFlow<String> = motorBt.connectedName
  val motorData: StateFlow<MotorData> = motorBt.motorData
  val motorDevices: StateFlow<List<DiscoveredDevice>> = motorBt.discoveredDevices

  val jkState: StateFlow<ConnectionState> = jkBle.connectionState
  val jkName: StateFlow<String> = jkBle.connectedName
  val jkData: StateFlow<JkBmsData> = jkBle.jkData
  val jkDevices: StateFlow<List<DiscoveredDevice>> = jkBle.scannedDevices

  val esp1State: StateFlow<ConnectionState> = esp32Ble.esp1State
  val esp1Name: StateFlow<String> = esp32Ble.esp1Name
  val esp1Data: StateFlow<Esp32Data> = esp32Ble.esp1Data

  val esp2State: StateFlow<ConnectionState> = esp32Ble.esp2State
  val esp2Name: StateFlow<String> = esp32Ble.esp2Name
  val battery2Data: StateFlow<Battery2Data> = esp32Ble.battery2Data
  val espScannedDevices: StateFlow<List<DiscoveredDevice>> = esp32Ble.scannedDevices

  val gpsState: StateFlow<GpsState> = locationTracker.gpsState

  private val _effective = MutableStateFlow(EffectiveTelemetry())
  val effective: StateFlow<EffectiveTelemetry> = _effective.asStateFlow()

  private val _chargingIndicator = MutableStateFlow(ChargingIndicatorState.NOT_CHARGING)
  val chargingIndicator: StateFlow<ChargingIndicatorState> = _chargingIndicator.asStateFlow()

  private val _liveTrip = MutableStateFlow(LiveTripMetrics())
  val liveTrip: StateFlow<LiveTripMetrics> = _liveTrip.asStateFlow()

  private val _ridingScore = MutableStateFlow(RidingScoreState())
  val ridingScore: StateFlow<RidingScoreState> = _ridingScore.asStateFlow()

  private val _chartHistory = MutableStateFlow<List<TelemetryChartPoint>>(emptyList())
  val chartHistory: StateFlow<List<TelemetryChartPoint>> = _chartHistory.asStateFlow()

  private val _alerts = MutableStateFlow<List<AppAlert>>(emptyList())
  val alerts: StateFlow<List<AppAlert>> = _alerts.asStateFlow()

  private val _weather = MutableStateFlow(WeatherState())
  val weather: StateFlow<WeatherState> = _weather.asStateFlow()

  private val _navState = MutableStateFlow(NavUiState())
  val navState: StateFlow<NavUiState> = _navState.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  private val _mapsPreviewBitmap = MutableStateFlow<Bitmap?>(null)
  val mapsPreviewBitmap: StateFlow<Bitmap?> = _mapsPreviewBitmap.asStateFlow()

  private val _mapsPreviewChars = MutableStateFlow(0)
  val mapsPreviewChars: StateFlow<Int> = _mapsPreviewChars.asStateFlow()

  val mirrorActive: StateFlow<Boolean> = MirrorFrameBus.isRunning
  val mapsFramesSent: StateFlow<Int> = motorBt.mapsFramesSent
  val mapsLastWriteMs: StateFlow<Long> = motorBt.mapsLastWriteMs
  val mapsLastChars: StateFlow<Int> = motorBt.mapsLastChars

  private var wasMoving = false
  private val liveBreadcrumbs = mutableListOf<BreadcrumbPoint>()
  private var lastWeatherLoc: GeoPoint? = null
  private var lastWeatherFetchMs = 0L
  private var atNavSlotIdx = 0
  private var lastAtNavSentMs = 0L
  private var lastAtMapsSentMs = 0L
  private var lastMatrixSentMs = 0L
  private var lastSpokenStepIdx = -1
  private var tts: TextToSpeech? = null
  private var ttsReady = false

  init {
    locationTracker.refreshPermissionState()
    if (locationTracker.hasLocationPermission()) {
      locationTracker.startTracking()
    }
    initTts()
    startMirrorCollector()
    startTelemetryLoop()
  }

  private fun startMirrorCollector() {
    viewModelScope.launch {
      MirrorFrameBus.frames.collect { (bmp, b64) ->
        _mapsPreviewBitmap.value = bmp
        _mapsPreviewChars.value = b64.length
        if (motorState.value == ConnectionState.CONNECTED) {
          val cfg = settings.value
          motorBt.sendMapsFrame(b64, cfg.leMapsChunk, cfg.leMapsDelayMs)
        }
      }
    }
  }

  private fun initTts() {
    tts =
      TextToSpeech(getApplication()) { status ->
        if (status == TextToSpeech.SUCCESS) {
          val res = tts?.setLanguage(Locale("id", "ID"))
          ttsReady =
            res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
        }
      }
  }

  private fun speak(text: String) {
    if (_navState.value.voiceEnabled && ttsReady) {
      tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_${System.currentTimeMillis()}")
    }
  }

  fun showToast(msg: String) {
    _toastMessage.value = msg
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  fun onPermissionsUpdated() {
    locationTracker.refreshPermissionState()
    if (locationTracker.hasLocationPermission()) {
      locationTracker.startTracking()
    }
  }

  private fun startTelemetryLoop() {
    viewModelScope.launch {
      while (isActive) {
        val now = System.currentTimeMillis()
        val cfg = settings.value
        esp32Ble.invertB2Current = cfg.b2InvertCurrent
        val gps = gpsState.value

        val eff =
          TelemetryEngine.computeEffective(
            motor = motorData.value,
            motorState = motorState.value,
            jk = jkData.value,
            jkState = jkState.value,
            esp1 = esp1Data.value,
            esp1State = esp1State.value,
            b2 = battery2Data.value,
            esp2State = esp2State.value,
            gpsSpeedKmh = gps.speedKmh,
            trip = _liveTrip.value,
            settings = cfg,
            nowMs = now,
          )
        _effective.value = eff
        _chargingIndicator.value = TelemetryEngine.chargingIndicator(eff)

        val anyLive =
          motorState.value == ConnectionState.CONNECTED ||
            jkState.value == ConnectionState.CONNECTED ||
            esp1State.value == ConnectionState.CONNECTED ||
            esp2State.value == ConnectionState.CONNECTED

        if (anyLive || (gps.speedKmh ?: 0f) > 2f) {
          val (nextTrip, nextScore, nextMoving) =
            TelemetryEngine.integrateSample(
              prevTrip = _liveTrip.value,
              prevScore = _ridingScore.value,
              eff = eff,
              nowMs = now,
              wasMoving = wasMoving,
            )
          _liveTrip.value = nextTrip
          _ridingScore.value = nextScore
          wasMoving = nextMoving

          val pt = gps.location
          if (pt != null && ((eff.speedKmh ?: 0f) > 1.5f || liveBreadcrumbs.isEmpty())) {
            val last = liveBreadcrumbs.lastOrNull()
            if (
              last == null ||
                RoutePhysics.haversineKm(GeoPoint(last.lat, last.lng), pt) >= 0.025
            ) {
              liveBreadcrumbs.add(
                BreadcrumbPoint(
                  lat = pt.lat,
                  lng = pt.lng,
                  spd = eff.speedKmh ?: 0f,
                  pwr = eff.combinedPowerW ?: 0f,
                )
              )
            }
          }
        }

        if (anyLive) {
          val sample =
            TelemetryChartPoint(
              timestampMs = now,
              speedKmh = eff.speedKmh ?: 0f,
              throttlePct = eff.throttlePct ?: 0f,
              powerW = eff.combinedPowerW ?: 0f,
              socPct = eff.combinedSocPct ?: 0f,
              voltageV = eff.avgVoltageV ?: 0f,
              tempC = eff.b1TempC ?: 0f,
            )
          _chartHistory.value = (_chartHistory.value + sample).takeLast(90)
        }

        _alerts.value = TelemetryEngine.evaluateAlarms(eff, jkData.value, battery2Data.value, cfg)

        // Update weather / reverse geocode periodically
        val curLoc = gps.location
        if (curLoc != null) {
          val movedKm = lastWeatherLoc?.let { RoutePhysics.haversineKm(it, curLoc) } ?: 999.0
          if (now - lastWeatherFetchMs > 300_000L || movedKm > 1.0) {
            lastWeatherLoc = curLoc
            lastWeatherFetchMs = now
            refreshWeatherAndPlace(curLoc)
          }
          updateTurnByTurnProgress(curLoc)
        }

        // Send AT+TRIP and AT+NAV to connected Motor
        if (motorState.value == ConnectionState.CONNECTED) {
          motorBt.sendTripRange(eff.rangeKm)
          if (cfg.atNavEnabled && now - lastAtNavSentMs >= cfg.atNavIntervalSec * 1000L) {
            lastAtNavSentMs = now
            val navStateNow = _navState.value
            if (navStateNow.isNavigating && navStateNow.activeRoute != null) {
              val step = navStateNow.activeRoute?.steps?.getOrNull(navStateNow.activeStepIndex)
              if (step != null) {
                val distTxt =
                  if (navStateNow.distToNextStepM >= 1000) {
                    String.format(Locale.US, "%.1fkm", navStateNow.distToNextStepM / 1000.0)
                  } else {
                    "${navStateNow.distToNextStepM.toInt()}m"
                  }
                motorBt.sendNavText("$distTxt ${step.instruction}")
              }
            } else {
              val slots =
                TelemetryEngine.buildAtNavSlots(
                  eff = eff,
                  trip = _liveTrip.value,
                  roadName = gps.roadName,
                  areaName = gps.areaName,
                  weatherMood = _weather.value.atNavMood,
                  slots = cfg.atNavSlots,
                )
              if (slots.isNotEmpty()) {
                val text = slots[atNavSlotIdx % slots.size]
                atNavSlotIdx++
                motorBt.sendNavText(text)
              }
            }
          }
        }

        // Sync Screen Mirror overlay & crop config
        val navNow = _navState.value
        val stepNow = navNow.activeRoute?.steps?.getOrNull(navNow.activeStepIndex)
        val stripTxt =
          if (navNow.isNavigating && stepNow != null) {
            val dStr =
              if (navNow.distToNextStepM >= 1000.0) {
                String.format(Locale.US, "%.1fkm", navNow.distToNextStepM / 1000.0)
              } else {
                "${navNow.distToNextStepM.toInt()}m"
              }
            "$dStr ${stepNow.instruction}"
          } else {
            "${(eff.speedKmh ?: 0f).toInt()}km/h • ${eff.combinedSocPct?.toInt() ?: "--"}% • ${String.format(Locale.US, "%.1f", eff.rangeKm)}km"
          }
        MirrorFrameBus.config =
          MirrorCaptureConfig(
            snapWidth = cfg.mapsSnapWidth,
            snapHeight = cfg.mapsSnapHeight,
            maxChars = cfg.mapsMaxChars,
            leftPct = cfg.mirrorLeftPct,
            rightPct = cfg.mirrorRightPct,
            topPct = cfg.mirrorTopPct,
            bottomPct = cfg.mirrorBottomPct,
            stripEnabled = cfg.mirrorStripEnabled,
            stripPos = cfg.mirrorStripPos,
            stripText = stripTxt,
          )

        // Stream AT+MAPS (1s during active navigation, 3s idle) unless Screen Mirror is active
        val mapsIntervalMs = if (navNow.isNavigating) 1000L else 3000L
        if (
          cfg.atMapsEnabled &&
            !mirrorActive.value &&
            motorState.value == ConnectionState.CONNECTED &&
            !motorBt.isMapsSending &&
            now - lastAtMapsSentMs >= mapsIntervalMs
        ) {
          lastAtMapsSentMs = now
          viewModelScope.launch(Dispatchers.IO) {
            try {
              val bmp =
                MidMapBitmapRenderer.renderFrame(
                  context = getApplication(),
                  tileLoader = tileLoader,
                  settings = cfg,
                  gps = gps,
                  nav = navNow,
                  eff = eff,
                )
              val b64 = MidMapBitmapRenderer.encodeBitmapToBase64Jpeg(bmp, cfg.mapsMaxChars)
              _mapsPreviewBitmap.value = bmp
              _mapsPreviewChars.value = b64.length
              motorBt.sendMapsFrame(b64, cfg.leMapsChunk, cfg.leMapsDelayMs)
            } catch (_: Exception) {}
          }
        }

        // Send Matrix MAX7219 text independently if ESP32 #1 connected
        if (
          esp1State.value == ConnectionState.CONNECTED &&
            cfg.matrixEnabled &&
            now - lastMatrixSentMs >= cfg.matrixIntervalSec * 1000L
        ) {
          lastMatrixSentMs = now
          val socTxt = eff.combinedSocPct?.let { "${it.toInt()}%" } ?: "--%"
          val msg = "${(eff.speedKmh ?: 0f).toInt()}KMH $socTxt ${String.format("%.1f", eff.rangeKm)}KM"
          esp32Ble.sendCommand(1, "MATRIX_TEXT:$msg")
        }

        delay(1000)
      }
    }
  }

  fun refreshWeatherNow() {
    val loc = gpsState.value.location
    if (loc == null) {
      showToast("Menunggu sinyal GPS untuk memuat cuaca…")
      return
    }
    refreshWeatherAndPlace(loc)
  }

  private fun refreshWeatherAndPlace(loc: GeoPoint) {
    viewModelScope.launch(Dispatchers.IO) {
      _weather.value = _weather.value.copy(isLoading = true, errorMessage = null)
      try {
        val resp = NetworkClient.openMeteo.getForecast(loc.lat, loc.lng)
        val cur = resp.current
        val (condLabel, mood) = RoutePhysics.decodeWeatherCode(cur?.weatherCode)
        val hourlyList = mutableListOf<HourlyWeatherItem>()
        val h = resp.hourly
        if (h != null) {
          for (i in 0 until minOf(6, h.time.size)) {
            val t = h.time[i].substringAfter("T", h.time[i]).take(5)
            hourlyList.add(
              HourlyWeatherItem(
                timeLabel = t,
                tempC = h.temperature2m.getOrNull(i) ?: 0f,
                weatherCode = h.weatherCode.getOrNull(i) ?: 0,
                rainProbPct = h.precipitationProbability.getOrNull(i) ?: 0,
              )
            )
          }
        }
        val rev =
          try {
            NetworkClient.nominatim.reverse(loc.lat, loc.lng)
          } catch (_: Exception) {
            null
          }
        val road = rev?.address?.road ?: ""
        val area = rev?.address?.suburb ?: rev?.address?.city ?: rev?.address?.town ?: ""
        locationTracker.updatePlaceNames(road, area)

        _weather.value =
          WeatherState(
            isLoading = false,
            tempC = cur?.temperature2m,
            feelsLikeC = cur?.apparentTemperature,
            windKmh = cur?.windSpeed10m,
            humidityPct = cur?.relativeHumidity2m,
            rainProbPct = hourlyList.firstOrNull()?.rainProbPct,
            weatherCode = cur?.weatherCode,
            conditionLabel = condLabel,
            atNavMood = mood,
            cityName = area.ifBlank { road }.ifBlank { "Lokasi GPS" },
            hourly = hourlyList,
            updatedAtMs = System.currentTimeMillis(),
          )
      } catch (e: Exception) {
        _weather.value =
          _weather.value.copy(
            isLoading = false,
            conditionLabel = "Gagal memuat cuaca",
            errorMessage = e.message,
          )
      }
    }
  }

  // ── TRIP LIFECYCLE ──
  fun finishAndSaveTrip(note: String = "") {
    val t = _liveTrip.value
    if (t.distanceKm < 0.02f && t.energyWh < 1f) {
      showToast("Jarak trip terlalu pendek untuk disimpan.")
      return
    }
    val now = System.currentTimeMillis()
    val jsonArr = JSONArray()
    liveBreadcrumbs.takeLast(300).forEach { b ->
      jsonArr.put(
        JSONArray().apply {
          put(b.lat)
          put(b.lng)
          put(b.spd.toDouble())
          put(b.pwr.toDouble())
        }
      )
    }
    val gps = gpsState.value
    val entity =
      TripEntity(
        startTimeMs = if (t.startTimeMs > 0) t.startTimeMs else now - 60_000L,
        endTimeMs = now,
        distanceKm = t.distanceKm,
        energyWh = t.energyWh,
        regenWh = t.regenWh,
        chargeWh = t.chargeWh,
        consumedAh = t.consumedAh,
        efficiencyWhKm = if (t.distanceKm > 0.05f) t.energyWh / t.distanceKm else 0f,
        maxSpeedKmh = t.maxSpeedKmh,
        avgSpeedKmh = t.avgSpeedKmh,
        maxPowerW = t.maxPowerW,
        maxRegenW = t.maxRegenW,
        movingSeconds = t.movingSeconds,
        stopCount = t.stopCount,
        ridingScore = _ridingScore.value.score,
        startSocPct = t.startSocPct,
        endSocPct = _effective.value.combinedSocPct,
        startLabel = gps.roadName.ifBlank { "Titik Awal" },
        endLabel = _navState.value.destination?.name ?: gps.areaName.ifBlank { "Tujuan" },
        pathJson = jsonArr.toString(),
        note = note,
      )
    viewModelScope.launch {
      db.tripDao().insert(entity)
      resetLiveTrip()
      showToast("Trip ${String.format("%.2f", entity.distanceKm)} km disimpan!")
    }
  }

  fun resetLiveTrip() {
    _liveTrip.value = LiveTripMetrics(startTimeMs = System.currentTimeMillis())
    _ridingScore.value = RidingScoreState()
    liveBreadcrumbs.clear()
    wasMoving = false
  }

  fun deleteTrip(trip: TripEntity) {
    viewModelScope.launch { db.tripDao().delete(trip) }
  }

  // ── NAVIGATION, ROUTING & SPKLU ──
  fun updateSearchQuery(query: String) {
    _navState.value = _navState.value.copy(searchQuery = query)
  }

  fun searchDestination(query: String) {
    val q = query.trim()
    if (q.isEmpty()) return

    val directCoord = RoutePhysics.parseCoordinatesOrGoogleMapsUrl(q)
    if (directCoord != null) {
      selectDestination(NavDestination("Koordinat (${String.format("%.4f, %.4f", directCoord.lat, directCoord.lng)})", directCoord.lat, directCoord.lng))
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      _navState.value = _navState.value.copy(isSearching = true, routeError = null)
      try {
        val loc = gpsState.value.location
        val vb =
          loc?.let { "${it.lng - 0.6},${it.lat + 0.6},${it.lng + 0.6},${it.lat - 0.6}" }
        val res = NetworkClient.nominatim.search(query = q, viewbox = vb)
        val mapped =
          res.mapNotNull { p ->
            val lat = p.lat?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = p.lon?.toDoubleOrNull() ?: return@mapNotNull null
            val full = p.displayName ?: p.name ?: q
            val short = p.name ?: full.split(",").firstOrNull()?.trim() ?: full
            PlaceSearchResult(
              displayName = full,
              shortName = short,
              lat = lat,
              lng = lng,
              category = p.type ?: "",
            )
          }
        _navState.value =
          _navState.value.copy(
            isSearching = false,
            searchResults = mapped,
            routeError = if (mapped.isEmpty()) "Lokasi '$q' tidak ditemukan." else null,
          )
      } catch (e: Exception) {
        _navState.value =
          _navState.value.copy(isSearching = false, routeError = "Gagal mencari lokasi: ${e.message}")
      }
    }
  }

  fun selectDestination(dest: NavDestination) {
    _navState.value =
      _navState.value.copy(
        destination = dest,
        searchResults = emptyList(),
        searchQuery = dest.name,
        routeError = null,
      )
    calculateRoute(dest)
  }

  fun addMultiStop(name: String, lat: Double, lng: Double) {
    val item = MultiStopItem("${System.currentTimeMillis()}", name, lat, lng)
    _navState.value = _navState.value.copy(multiStops = _navState.value.multiStops + item)
  }

  fun removeMultiStop(id: String) {
    _navState.value =
      _navState.value.copy(multiStops = _navState.value.multiStops.filterNot { it.id == id })
  }

  fun calculateMultiStopRoute() {
    val stops = _navState.value.multiStops
    if (stops.isEmpty()) {
      showToast("Tambahkan minimal 1 pemberhentian.")
      return
    }
    val dest = NavDestination(stops.last().name, stops.last().lat, stops.last().lng)
    _navState.value = _navState.value.copy(destination = dest)
    calculateRoute(dest, stops.dropLast(1))
  }

  fun calculateRoute(dest: NavDestination, waypoints: List<MultiStopItem> = emptyList()) {
    val origin = gpsState.value.location
    if (origin == null) {
      _navState.value =
        _navState.value.copy(routeError = "GPS belum terkunci. Aktifkan izin lokasi untuk rute.")
      return
    }
    viewModelScope.launch(Dispatchers.IO) {
      _navState.value = _navState.value.copy(isRouting = true, routeError = null)
      try {
        val allPts = buildList {
          add(origin)
          waypoints.forEach { add(GeoPoint(it.lat, it.lng)) }
          add(GeoPoint(dest.lat, dest.lng))
        }
        val coordStr = allPts.joinToString(";") { "${it.lng},${it.lat}" }
        val osrm = NetworkClient.osrm.route(coords = coordStr, alternatives = waypoints.isEmpty())
        val cfg = settings.value
        val packWh =
          if (_effective.value.b2Active) {
            (cfg.b1NominalV * cfg.b1CapacityAh + cfg.b2NominalV * cfg.b2CapacityAh).toDouble()
          } else {
            (cfg.b1NominalV * cfg.b1CapacityAh).toDouble()
          }

        val options =
          osrm.routes.mapIndexed { idx, r ->
            val geo =
              r.geometry?.coordinates?.mapNotNull { c ->
                if (c.size >= 2) GeoPoint(c[1], c[0]) else null
              } ?: emptyList()

            val steps =
              r.legs.flatMap { leg ->
                leg.steps.mapNotNull { st ->
                  val loc = st.maneuver?.location
                  if (loc == null || loc.size < 2) return@mapNotNull null
                  val road = st.name.orEmpty()
                  val mType = st.maneuver.type.orEmpty()
                  val mMod = st.maneuver.modifier.orEmpty()
                  RouteStep(
                    instruction = RoutePhysics.translateOsrmManeuver(mType, mMod, road),
                    roadName = road,
                    maneuverType = mType,
                    maneuverModifier = mMod,
                    distanceM = st.distance,
                    durationSec = st.duration,
                    location = GeoPoint(loc[1], loc[0]),
                  )
                }
              }

            val sampled = RoutePhysics.downsamplePoints(geo, 32)
            val elevations =
              try {
                if (sampled.size >= 2) {
                  val lats = sampled.joinToString(",") { String.format(Locale.US, "%.5f", it.lat) }
                  val lngs = sampled.joinToString(",") { String.format(Locale.US, "%.5f", it.lng) }
                  NetworkClient.openMeteo.getElevation(lats, lngs).elevation ?: emptyList()
                } else {
                  emptyList()
                }
              } catch (_: Exception) {
                emptyList()
              }

            val distKm = r.distance / 1000.0
            val durMin = r.duration / 60.0
            val (elevSamples, energyEst) =
              RoutePhysics.buildElevationAndEnergy(
                sampledPoints = sampled,
                elevations = elevations,
                totalDistanceKm = distKm,
                durationMin = durMin,
                bikeKg = cfg.physicsBikeKg,
                riderKg = cfg.physicsRiderKg,
                loadKg = cfg.physicsLoadKg,
                calFactor = cfg.physicsCalFactor,
                totalPackWh = packWh,
                currentSocPct = _effective.value.combinedSocPct,
              )

            RouteOption(
              id = idx,
              label = if (idx == 0) "Rute Utama" else "Alternatif $idx",
              distanceKm = distKm,
              durationMin = durMin,
              geometry = geo,
              steps = steps,
              elevationSamples = elevSamples,
              energyEstimate = energyEst,
            )
          }

        _navState.value =
          _navState.value.copy(
            isRouting = false,
            routes = options,
            selectedRouteIndex = 0,
            activeStepIndex = 0,
            routeError = if (options.isEmpty()) "Tidak ditemukan rute berkendara." else null,
          )
      } catch (e: Exception) {
        _navState.value =
          _navState.value.copy(isRouting = false, routeError = "Gagal hitung rute: ${e.message}")
      }
    }
  }

  fun selectRouteOption(index: Int) {
    _navState.value = _navState.value.copy(selectedRouteIndex = index, activeStepIndex = 0)
  }

  fun startNavigation() {
    val route = _navState.value.activeRoute ?: return
    lastSpokenStepIdx = -1
    _navState.value = _navState.value.copy(isNavigating = true, activeStepIndex = 0)
    route.steps.firstOrNull()?.let {
      lastSpokenStepIdx = 0
      speak(it.instruction)
    }
  }

  fun stopNavigation() {
    _navState.value = _navState.value.copy(isNavigating = false, rideLockActive = false)
  }

  fun toggleVoiceGuidance() {
    _navState.value = _navState.value.copy(voiceEnabled = !_navState.value.voiceEnabled)
  }

  fun setRideLock(active: Boolean) {
    _navState.value = _navState.value.copy(rideLockActive = active)
  }

  private fun updateTurnByTurnProgress(curLoc: GeoPoint) {
    val nav = _navState.value
    if (!nav.isNavigating) return
    val route = nav.activeRoute ?: return
    val steps = route.steps
    if (steps.isEmpty()) return

    val idx = nav.activeStepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[idx]
    val distM = RoutePhysics.haversineKm(curLoc, step.location) * 1000.0

    var nextIdx = idx
    if (distM < 35.0 && idx < steps.lastIndex) {
      nextIdx = idx + 1
    }
    val nextDistM = RoutePhysics.haversineKm(curLoc, steps[nextIdx].location) * 1000.0

    if (nextIdx != lastSpokenStepIdx) {
      lastSpokenStepIdx = nextIdx
      speak(steps[nextIdx].instruction)
    }

    _navState.value = nav.copy(activeStepIndex = nextIdx, distToNextStepM = nextDistM)
  }

  fun searchNearbySpklu() {
    val loc = gpsState.value.location
    if (loc == null) {
      showToast("Lokasi GPS belum tersedia untuk mencari SPKLU.")
      return
    }
    viewModelScope.launch(Dispatchers.IO) {
      _navState.value = _navState.value.copy(spkluLoading = true, spkluError = null)
      val stations = mutableListOf<SpkluStation>()
      try {
        val ocmList = NetworkClient.openChargeMap.getNearbyStations(lat = loc.lat, lng = loc.lng)
        ocmList.forEach { poi ->
          val addr = poi.addressInfo ?: return@forEach
          val sLat = addr.latitude ?: return@forEach
          val sLng = addr.longitude ?: return@forEach
          val dKm = addr.distance ?: RoutePhysics.haversineKm(loc, GeoPoint(sLat, sLng))
          val maxKw = poi.connections?.mapNotNull { it.powerKw }?.maxOrNull()
          val connDesc =
            poi.connections
              ?.mapNotNull { it.connectionType?.title }
              ?.distinct()
              ?.joinToString(", ")
              .orEmpty()
              .ifBlank { "EV Charger" }
          stations.add(
            SpkluStation(
              id = "ocm_${poi.id ?: stations.size}",
              name = addr.title ?: "SPKLU",
              operatorName = poi.operatorInfo?.title ?: "SPKLU Umum",
              address = listOfNotNull(addr.addressLine1, addr.town).joinToString(", "),
              lat = sLat,
              lng = sLng,
              distanceKm = dKm,
              connectorSummary = connDesc,
              powerKw = maxKw,
              source = "OpenChargeMap",
            )
          )
        }
      } catch (_: Exception) {}

      // Also query Overpass OSM charging stations within 15 km
      try {
        val q = "[out:json][timeout:12];node[\"amenity\"=\"charging_station\"](around:15000,${loc.lat},${loc.lng});out body;"
        val osmResp = NetworkClient.overpass.query(q)
        osmResp.elements.forEach { el ->
          val sLat = el.lat ?: el.center?.lat ?: return@forEach
          val sLng = el.lon ?: el.center?.lon ?: return@forEach
          val dKm = RoutePhysics.haversineKm(loc, GeoPoint(sLat, sLng))
          val name = el.tags?.get("name") ?: "SPKLU OSM"
          val op = el.tags?.get("operator") ?: "OpenStreetMap"
          val alreadyNear =
            stations.any { RoutePhysics.haversineKm(GeoPoint(it.lat, it.lng), GeoPoint(sLat, sLng)) < 0.08 }
          if (!alreadyNear) {
            stations.add(
              SpkluStation(
                id = "osm_${el.id}",
                name = name,
                operatorName = op,
                address = el.tags?.get("addr:street") ?: "Sekitar lokasi",
                lat = sLat,
                lng = sLng,
                distanceKm = dKm,
                connectorSummary = el.tags?.get("socket:type2")?.let { "Type 2" } ?: "EV Socket",
                powerKw = null,
                source = "OpenStreetMap",
              )
            )
          }
        }
      } catch (_: Exception) {}

      val sorted = stations.sortedBy { it.distanceKm }
      _navState.value =
        _navState.value.copy(
          spkluLoading = false,
          spkluStations = sorted,
          spkluError = if (sorted.isEmpty()) "Tidak ditemukan SPKLU dalam radius 25 km." else null,
        )
    }
  }

  // ── FAVORITES, PARKING, GARAGE, SETTINGS ──
  fun saveFavoriteDestination(name: String, lat: Double, lng: Double) {
    viewModelScope.launch {
      db.favoritePlaceDao().insert(FavoritePlaceEntity(name = name, lat = lat, lng = lng))
      showToast("Disimpan ke lokasi favorit.")
    }
  }

  fun deleteFavorite(fav: FavoritePlaceEntity) {
    viewModelScope.launch { db.favoritePlaceDao().delete(fav) }
  }

  fun pinCurrentParking() {
    val gps = gpsState.value
    val loc = gps.location
    if (loc == null) {
      showToast("GPS belum tersedia untuk menandai parkir.")
      return
    }
    val label = listOf(gps.roadName, gps.areaName).filter { it.isNotBlank() }.joinToString(", ")
      .ifBlank { "Titik Parkir" }
    viewModelScope.launch {
      settingsRepo.saveParkedLocation(loc.lat, loc.lng, label)
      showToast("Lokasi parkir disimpan.")
    }
  }

  fun clearParking() {
    viewModelScope.launch { settingsRepo.clearParkedLocation() }
  }

  fun triggerFindMyBikeBeep() {
    try {
      val tg = ToneGenerator(AudioManager.STREAM_ALARM, 90)
      tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
      viewModelScope.launch {
        delay(500)
        tg.release()
      }
    } catch (_: Exception) {}
    if (esp1State.value == ConnectionState.CONNECTED) {
      esp32Ble.sendCommand(1, "MATRIX_TEXT:GESITS HERE")
    }
    showToast("Sinyal Temukan Motor dipicu!")
  }

  fun toggleMotorContact(turnOn: Boolean) {
    val eff = _effective.value
    if (!turnOn && ((eff.speedKmh ?: 0f) > 3f || (eff.throttlePct ?: 0f) > 0f)) {
      showToast("Keamanan: Tidak dapat mematikan kontak saat motor bergerak / throttle aktif!")
      return
    }
    esp32Ble.sendCommand(1, if (turnOn) "CONTACT_ON" else "CONTACT_OFF")
  }

  fun toggleMatrix(on: Boolean) {
    esp32Ble.sendCommand(1, if (on) "MATRIX_ON" else "MATRIX_OFF")
  }

  fun sendMusicCommand(cmd: String) {
    esp32Ble.sendCommand(1, cmd)
  }

  fun logManualCharge() {
    val soc = _effective.value.combinedSocPct?.toInt() ?: 100
    viewModelScope.launch {
      db.chargeLogDao().insert(ChargeLogEntity(timestampMs = System.currentTimeMillis(), socPct = soc))
      showToast("Log pengisian ($soc%) dicatat.")
    }
  }

  fun saveMaintenance(lastOdo: Float, interval: Float, serviceNote: String) {
    viewModelScope.launch {
      settingsRepo.saveMaintenanceSettings(lastOdo, interval)
      if (serviceNote.isNotBlank()) {
        db.maintenanceLogDao().insert(
          MaintenanceLogEntity(
            timestampMs = System.currentTimeMillis(),
            odoKm = lastOdo,
            note = serviceNote,
          )
        )
      }
      showToast("Pengaturan servis disimpan.")
    }
  }

  fun addFieldNote(title: String, content: String) {
    if (title.isBlank() && content.isBlank()) return
    viewModelScope.launch {
      db.fieldNoteDao().insert(
        FieldNoteEntity(
          title = title.ifBlank { "Catatan Riding" },
          content = content,
          odoKm = _effective.value.odoKm,
        )
      )
      showToast("Catatan lapangan tersimpan.")
    }
  }

  fun deleteFieldNote(note: FieldNoteEntity) {
    viewModelScope.launch { db.fieldNoteDao().delete(note) }
  }

  fun updateRiderName(name: String) = viewModelScope.launch { settingsRepo.updateRiderName(name) }

  fun setDarkTheme(dark: Boolean) = viewModelScope.launch { settingsRepo.setDarkTheme(dark) }

  fun setAccentTheme(id: String) = viewModelScope.launch { settingsRepo.setAccentTheme(id) }

  fun setGarageLivery(id: String) = viewModelScope.launch { settingsRepo.setGarageLivery(id) }

  fun setBatterySpecs(b1V: Float, b1Ah: Float, b2V: Float, b2Ah: Float, refWhKm: Float) =
    viewModelScope.launch { settingsRepo.setBatterySpecs(b1V, b1Ah, b2V, b2Ah, refWhKm) }

  fun setB2InvertCurrent(invert: Boolean) =
    viewModelScope.launch { settingsRepo.setB2InvertCurrent(invert) }

  fun setPhysics(bikeKg: Float, riderKg: Float, loadKg: Float, cal: Float) =
    viewModelScope.launch { settingsRepo.setPhysics(bikeKg, riderKg, loadKg, cal) }

  fun setAlarms(
    masterOn: Boolean,
    socLowOn: Boolean,
    socThresh: Int,
    tempHighOn: Boolean,
    tempThresh: Int,
    sohLowOn: Boolean,
    deltaCellOn: Boolean,
  ) = viewModelScope.launch {
    settingsRepo.setAlarms(masterOn, socLowOn, socThresh, tempHighOn, tempThresh, sohLowOn, deltaCellOn)
  }

  fun setBatteryPriority(priority: BatterySourcePriority) =
    viewModelScope.launch { settingsRepo.setBatteryPriority(priority) }

  fun setAtNavConfig(enabled: Boolean, intervalSec: Int, slots: AtNavSlotConfig) =
    viewModelScope.launch { settingsRepo.setAtNavConfig(enabled, intervalSec, slots) }

  fun setMatrixConfig(enabled: Boolean, intervalSec: Int) =
    viewModelScope.launch { settingsRepo.setMatrixConfig(enabled, intervalSec) }

  fun setWeeklyGoalKm(goalKm: Float) = viewModelScope.launch { settingsRepo.setWeeklyGoalKm(goalKm) }

  fun saveDevPin(pin: String) = viewModelScope.launch { settingsRepo.saveDevPin(pin) }

  fun clearSerialLogs() {
    _serialLogs.value = emptyList()
  }

  fun buildTripCsv(trip: TripEntity): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val sb = StringBuilder()
    sb.appendLine("start_time,end_time,distance_km,energy_wh,regen_wh,wh_per_km,max_speed_kmh,avg_speed_kmh,riding_score,start_soc,end_soc")
    sb.appendLine(
      "${sdf.format(Date(trip.startTimeMs))},${sdf.format(Date(trip.endTimeMs))}," +
        "${String.format(Locale.US, "%.2f", trip.distanceKm)},${trip.energyWh.toInt()},${trip.regenWh.toInt()}," +
        "${String.format(Locale.US, "%.1f", trip.efficiencyWhKm)},${String.format(Locale.US, "%.1f", trip.maxSpeedKmh)}," +
        "${String.format(Locale.US, "%.1f", trip.avgSpeedKmh)},${trip.ridingScore ?: ""},${trip.startSocPct ?: ""},${trip.endSocPct ?: ""}"
    )
    return sb.toString()
  }

  fun buildTripGpx(trip: TripEntity): String {
    val arr =
      try {
        JSONArray(trip.pathJson)
      } catch (_: Exception) {
        JSONArray()
      }
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.appendLine("""<gpx version="1.1" creator="GESITS Dash"><trk><name>${trip.startLabel} - ${trip.endLabel}</name><trkseg>""")
    for (i in 0 until arr.length()) {
      val pt = arr.optJSONArray(i) ?: continue
      val lat = pt.optDouble(0)
      val lng = pt.optDouble(1)
      sb.appendLine("""<trkpt lat="$lat" lon="$lng"></trkpt>""")
    }
    sb.appendLine("""</trkseg></trk></gpx>""")
    return sb.toString()
  }

  fun startScreenMirror(resultCode: Int, data: Intent) {
    val intent =
      Intent(getApplication(), ScreenMirrorService::class.java).apply {
        action = ScreenMirrorService.ACTION_START
        putExtra(ScreenMirrorService.EXTRA_RESULT_CODE, resultCode)
        putExtra(ScreenMirrorService.EXTRA_DATA, data)
      }
    ContextCompat.startForegroundService(getApplication(), intent)
    showToast("Mirror Layar aktif! Buka Google Maps untuk kirim ke MID.")
  }

  fun stopScreenMirror() {
    val intent =
      Intent(getApplication(), ScreenMirrorService::class.java).apply {
        action = ScreenMirrorService.ACTION_STOP
      }
    getApplication<Application>().startService(intent)
    showToast("Mirror Layar dihentikan.")
  }

  fun renderMapsPreviewAndSendOnce() {
    viewModelScope.launch(Dispatchers.IO) {
      val cfg = settings.value
      val bmp =
        MidMapBitmapRenderer.renderFrame(
          context = getApplication(),
          tileLoader = tileLoader,
          settings = cfg,
          gps = gpsState.value,
          nav = _navState.value,
          eff = _effective.value,
        )
      val b64 = MidMapBitmapRenderer.encodeBitmapToBase64Jpeg(bmp, cfg.mapsMaxChars)
      _mapsPreviewBitmap.value = bmp
      _mapsPreviewChars.value = b64.length
      if (motorState.value == ConnectionState.CONNECTED) {
        motorBt.sendMapsFrame(b64, cfg.leMapsChunk, cfg.leMapsDelayMs)
        showToast("Frame AT+MAPS dikirim (${b64.length} chr)")
      } else {
        showToast("Pratinjau AT+MAPS dibuat (${b64.length} chr). Hubungkan Motor untuk kirim.")
      }
    }
  }

  fun setAtMapsConfig(
    enabled: Boolean,
    width: Int,
    height: Int,
    maxChars: Int,
    layout: String,
    riderPos: String,
    leChunk: Int,
    leDelayMs: Int,
  ) = viewModelScope.launch {
    settingsRepo.setAtMapsConfig(enabled, width, height, maxChars, layout, riderPos, leChunk, leDelayMs)
  }

  fun setCustomWallpaper(uri: String, enabled: Boolean) =
    viewModelScope.launch { settingsRepo.setCustomWallpaper(uri, enabled) }

  fun setMirrorCropAndStrip(
    leftPct: Int,
    rightPct: Int,
    topPct: Int,
    bottomPct: Int,
    stripEnabled: Boolean,
    stripPos: String,
  ) = viewModelScope.launch {
    settingsRepo.setMirrorCropAndStrip(leftPct, rightPct, topPct, bottomPct, stripEnabled, stripPos)
  }

  override fun onCleared() {
    super.onCleared()
    if (mirrorActive.value) stopScreenMirror()
    motorBt.disconnect()
    jkBle.disconnect()
    esp32Ble.disconnectSlot(1)
    esp32Ble.disconnectSlot(2)
    locationTracker.stopTracking()
    tts?.stop()
    tts?.shutdown()
  }
}
