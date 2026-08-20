package com.SolucionesHR.nibotao.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.SolucionesHR.nibotao.data.local.dao.BatterySampleDao
import com.SolucionesHR.nibotao.data.local.dao.LocationPointDao
import com.SolucionesHR.nibotao.data.local.dao.TripDao
import com.SolucionesHR.nibotao.data.local.entity.BatterySampleEntity
import com.SolucionesHR.nibotao.data.local.entity.LocationPointEntity
import com.SolucionesHR.nibotao.data.local.entity.TripEntity

@Database(
    entities = [TripEntity::class, LocationPointEntity::class, BatterySampleEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun locationPointDao(): LocationPointDao
    abstract fun batterySampleDao(): BatterySampleDao

    companion object {
        const val DATABASE_NAME = "autonomia.db"
    }
}
