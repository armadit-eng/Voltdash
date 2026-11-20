package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.model.GeoPoint
import com.example.model.GpsState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationTracker(private val context: Context) {
  private val fusedClient: FusedLocationProviderClient =
    LocationServices.getFusedLocationProviderClient(context)

  private val _gpsState = MutableStateFlow(GpsState())
  val gpsState: StateFlow<GpsState> = _gpsState.asStateFlow()

  private var callback: LocationCallback? = null

  fun hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
  }

  fun refreshPermissionState() {
    val granted = hasLocationPermission()
    _gpsState.value = _gpsState.value.copy(hasPermission = granted)
  }

  @SuppressLint("MissingPermission")
  fun startTracking() {
    val granted = hasLocationPermission()
    _gpsState.value = _gpsState.value.copy(hasPermission = granted)
    if (!granted || callback != null) return

    val request =
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
        .setMinUpdateIntervalMillis(1000L)
        .build()

    val cb =
      object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          val loc = result.lastLocation ?: return
          val speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f) else null
          val bearing = if (loc.hasBearing()) loc.bearing else null
          val alt = if (loc.hasAltitude()) loc.altitude else null
          _gpsState.value =
            _gpsState.value.copy(
              hasPermission = true,
              isTracking = true,
              location = GeoPoint(loc.latitude, loc.longitude, alt),
              speedKmh = speedKmh,
              headingDeg = bearing,
              accuracyM = loc.accuracy,
              lastUpdatedMs = System.currentTimeMillis(),
            )
        }
      }
    callback = cb

    try {
      fusedClient.lastLocation.addOnSuccessListener { loc ->
        if (loc != null && _gpsState.value.location == null) {
          _gpsState.value =
            _gpsState.value.copy(
              location = GeoPoint(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else null),
              accuracyM = loc.accuracy,
              lastUpdatedMs = System.currentTimeMillis(),
            )
        }
      }
      fusedClient.requestLocationUpdates(request, cb, Looper.getMainLooper())
      _gpsState.value = _gpsState.value.copy(isTracking = true)
    } catch (_: SecurityException) {
      _gpsState.value = _gpsState.value.copy(hasPermission = false, isTracking = false)
    }
  }

  fun updatePlaceNames(roadName: String, areaName: String) {
    _gpsState.value = _gpsState.value.copy(roadName = roadName, areaName = areaName)
  }

  fun stopTracking() {
    callback?.let { fusedClient.removeLocationUpdates(it) }
    callback = null
    _gpsState.value = _gpsState.value.copy(isTracking = false)
  }
}
