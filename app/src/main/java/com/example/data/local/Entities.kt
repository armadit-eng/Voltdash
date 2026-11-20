package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val startTimeMs: Long,
  val endTimeMs: Long,
  val distanceKm: Float,
  val energyWh: Float,
  val regenWh: Float,
  val chargeWh: Float,
  val consumedAh: Float,
  val efficiencyWhKm: Float,
  val maxSpeedKmh: Float,
  val avgSpeedKmh: Float,
  val maxPowerW: Float,
  val maxRegenW: Float,
  val movingSeconds: Long,
  val stopCount: Int,
  val ridingScore: Int?,
  val startSocPct: Float?,
  val endSocPct: Float?,
  val startLabel: String,
  val endLabel: String,
  val pathJson: String, // JSON array of [lat, lng, spd, pwr]
  val note: String = "",
)

@Entity(tableName = "maintenance_logs")
data class MaintenanceLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestampMs: Long,
  val odoKm: Float,
  val note: String,
)

@Entity(tableName = "charge_logs")
data class ChargeLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestampMs: Long,
  val socPct: Int,
  val addedWh: Float = 0f,
  val note: String = "",
)

@Entity(tableName = "favorite_places")
data class FavoritePlaceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val lat: Double,
  val lng: Double,
  val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "field_notes")
data class FieldNoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestampMs: Long = System.currentTimeMillis(),
  val title: String,
  val content: String,
  val odoKm: Float? = null,
)
