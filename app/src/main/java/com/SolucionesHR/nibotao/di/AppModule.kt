package com.SolucionesHR.nibotao.di

import android.content.Context
import androidx.room.Room
import com.SolucionesHR.nibotao.data.bluetooth.BatteryDataSource
import com.SolucionesHR.nibotao.data.bluetooth.BleRepositoryImpl
import com.SolucionesHR.nibotao.data.bluetooth.StandardGattBatteryDataSource
import com.SolucionesHR.nibotao.data.local.AppDatabase
import com.SolucionesHR.nibotao.data.local.dao.BatterySampleDao
import com.SolucionesHR.nibotao.data.local.dao.LocationPointDao
import com.SolucionesHR.nibotao.data.local.dao.TripDao
import com.SolucionesHR.nibotao.data.location.LocationRepositoryImpl
import com.SolucionesHR.nibotao.data.repository.TripRepositoryImpl
import com.SolucionesHR.nibotao.domain.repository.BleRepository
import com.SolucionesHR.nibotao.domain.repository.LocationRepository
import com.SolucionesHR.nibotao.domain.repository.TripRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration(dropAllTables = false) // ajustar a migraciones reales antes de produccion
            .build()

    @Provides fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()
    @Provides fun provideLocationPointDao(db: AppDatabase): LocationPointDao = db.locationPointDao()
    @Provides fun provideBatterySampleDao(db: AppDatabase): BatterySampleDao = db.batterySampleDao()

    @Provides
    @Singleton
    fun provideBatteryDataSource(): BatteryDataSource = StandardGattBatteryDataSource()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTripRepository(impl: TripRepositoryImpl): TripRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository
}
