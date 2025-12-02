package com.saschl.cameragps.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.saschl.cameragps.database.devices.CameraDevice
import com.saschl.cameragps.database.devices.CameraDeviceDAO
import com.saschl.cameragps.database.logging.LogDao
import com.saschl.cameragps.database.logging.LogEntry

@Database(
    entities = [LogEntry::class, CameraDevice::class],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
abstract class LogDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao

    abstract fun cameraDeviceDao(): CameraDeviceDAO

    companion object {
        @Volatile
        private var INSTANCE: LogDatabase? = null

        fun getDatabase(context: Context): LogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LogDatabase::class.java,
                    "log_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}