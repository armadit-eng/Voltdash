package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities =
    [
      TripEntity::class,
      MaintenanceLogEntity::class,
      ChargeLogEntity::class,
      FavoritePlaceEntity::class,
      FieldNoteEntity::class,
    ],
  version = 1,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun tripDao(): TripDao

  abstract fun maintenanceLogDao(): MaintenanceLogDao

  abstract fun chargeLogDao(): ChargeLogDao

  abstract fun favoritePlaceDao(): FavoritePlaceDao

  abstract fun fieldNoteDao(): FieldNoteDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "gesits_dash_db",
              )
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
