package com.example

import com.example.data.local.DashboardSettings
import com.example.domain.ChargingIndicatorState
import com.example.domain.RoutePhysics
import com.example.domain.TelemetryEngine
import com.example.model.Battery2Data
import com.example.model.ConnectionState
import com.example.model.Esp32Data
import com.example.model.GeoPoint
import com.example.model.JkBmsData
import com.example.model.LiveTripMetrics
import com.example.model.MotorData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryEngineTest {
  @Test
  fun dualBatteryWeightedSocAndRangeEstimate() {
    val now = 1_000_000L
    val motor =
      MotorData(
        speedKmh = 40f,
        vbat = 72f,
        ibat = -20f,
        socPct = 80f,
        lastUpdatedMs = now,
      )
    val b2 =
      Battery2Data(
        vbat = 72f,
        ibat = -10f,
        socPct = 40f,
        lastUpdatedMs = now,
      )
    val settings =
      DashboardSettings(
        b1NominalV = 72f,
        b1CapacityAh = 20f,
        b2NominalV = 72f,
        b2CapacityAh = 20f,
        refWhPerKm = 30f,
      )

    val eff =
      TelemetryEngine.computeEffective(
        motor = motor,
        motorState = ConnectionState.CONNECTED,
        jk = JkBmsData(),
        jkState = ConnectionState.DISCONNECTED,
        esp1 = Esp32Data(),
        esp1State = ConnectionState.DISCONNECTED,
        b2 = b2,
        esp2State = ConnectionState.CONNECTED,
        gpsSpeedKmh = null,
        trip = LiveTripMetrics(),
        settings = settings,
        nowMs = now + 500,
      )

    assertTrue(eff.b2Active)
    assertEquals(60f, eff.combinedSocPct ?: 0f, 0.1f)
    assertEquals(2058, eff.rpm)
    // Available energy = (1440 * 0.8) + (1440 * 0.4) = 1728 Wh; 1728 / 30 = 57.6 km
    assertEquals(57.6f, eff.rangeKm, 0.2f)
    assertEquals(ChargingIndicatorState.NOT_CHARGING, TelemetryEngine.chargingIndicator(eff))
  }

  @Test
  fun chargingIndicatorDetectsChargingFullAndRegen() {
    val now = 2_000_000L
    val chargingMotor = MotorData(speedKmh = 0f, vbat = 80f, ibat = 10f, socPct = 75f, lastUpdatedMs = now)
    val effCharging =
      TelemetryEngine.computeEffective(
        motor = chargingMotor,
        motorState = ConnectionState.CONNECTED,
        jk = JkBmsData(),
        jkState = ConnectionState.DISCONNECTED,
        esp1 = Esp32Data(),
        esp1State = ConnectionState.DISCONNECTED,
        b2 = Battery2Data(),
        esp2State = ConnectionState.DISCONNECTED,
        gpsSpeedKmh = null,
        trip = LiveTripMetrics(),
        settings = DashboardSettings(),
        nowMs = now + 500,
      )
    assertEquals(ChargingIndicatorState.CHARGING, TelemetryEngine.chargingIndicator(effCharging))

    val fullMotor = MotorData(speedKmh = 0f, vbat = 84f, ibat = 0f, socPct = 100f, lastUpdatedMs = now)
    val effFull =
      TelemetryEngine.computeEffective(
        motor = fullMotor,
        motorState = ConnectionState.CONNECTED,
        jk = JkBmsData(),
        jkState = ConnectionState.DISCONNECTED,
        esp1 = Esp32Data(),
        esp1State = ConnectionState.DISCONNECTED,
        b2 = Battery2Data(),
        esp2State = ConnectionState.DISCONNECTED,
        gpsSpeedKmh = null,
        trip = LiveTripMetrics(),
        settings = DashboardSettings(),
        nowMs = now + 500,
      )
    assertEquals(ChargingIndicatorState.FULL, TelemetryEngine.chargingIndicator(effFull))
  }

  @Test
  fun coordinateAndGoogleMapsLinkParsing() {
    val pt = RoutePhysics.parseCoordinatesOrGoogleMapsUrl("https://maps.google.com/?q=-6.175392,106.827153")
    assertNotNull(pt)
    assertEquals(-6.175392, pt!!.lat, 0.0001)
    assertEquals(106.827153, pt.lng, 0.0001)

    val dist = RoutePhysics.haversineKm(GeoPoint(-6.1754, 106.8272), GeoPoint(-6.2088, 106.8456))
    assertTrue(dist in 3.5..5.0)
  }
}
