package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
  @Query("SELECT * FROM trips ORDER BY startTimeMs DESC")
  fun observeAll(): Flow<List<TripEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(trip: TripEntity): Long

  @Delete suspend fun delete(trip: TripEntity)

  @Query("DELETE FROM trips") suspend fun deleteAll()
}

@Dao
interface MaintenanceLogDao {
  @Query("SELECT * FROM maintenance_logs ORDER BY timestampMs DESC")
  fun observeAll(): Flow<List<MaintenanceLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(log: MaintenanceLogEntity): Long

  @Delete suspend fun delete(log: MaintenanceLogEntity)
}

@Dao
interface ChargeLogDao {
  @Query("SELECT * FROM charge_logs ORDER BY timestampMs DESC")
  fun observeAll(): Flow<List<ChargeLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(log: ChargeLogEntity): Long

  @Delete suspend fun delete(log: ChargeLogEntity)
}

@Dao
interface FavoritePlaceDao {
  @Query("SELECT * FROM favorite_places ORDER BY createdAtMs DESC")
  fun observeAll(): Flow<List<FavoritePlaceEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(place: FavoritePlaceEntity): Long

  @Delete suspend fun delete(place: FavoritePlaceEntity)
}

@Dao
interface FieldNoteDao {
  @Query("SELECT * FROM field_notes ORDER BY timestampMs DESC")
  fun observeAll(): Flow<List<FieldNoteEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(note: FieldNoteEntity): Long

  @Delete suspend fun delete(note: FieldNoteEntity)
}
