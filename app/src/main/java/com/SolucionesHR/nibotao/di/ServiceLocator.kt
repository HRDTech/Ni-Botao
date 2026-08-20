package com.SolucionesHR.nibotao.di

import android.content.Context
import androidx.room.Room
import com.SolucionesHR.nibotao.data.bluetooth.BleRepositoryImpl
import com.SolucionesHR.nibotao.data.bluetooth.StandardGattBatteryDataSource
import com.SolucionesHR.nibotao.data.local.AppDatabase
import com.SolucionesHR.nibotao.data.location.LocationRepositoryImpl
import com.SolucionesHR.nibotao.data.repository.TripRepositoryImpl
import com.SolucionesHR.nibotao.domain.repository.BleRepository
import com.SolucionesHR.nibotao.domain.repository.LocationRepository
import com.SolucionesHR.nibotao.domain.repository.TripRepository
import com.SolucionesHR.nibotao.domain.usecase.*
import com.SolucionesHR.nibotao.presentation.dashboard.DashboardViewModel
import com.SolucionesHR.nibotao.presentation.history.HistoryViewModel
import com.SolucionesHR.nibotao.presentation.setup.SetupViewModel

object ServiceLocator {
    private var context: Context? = null
    private var database: AppDatabase? = null
    private var _tripRepository: TripRepository? = null
    private var _bleRepository: BleRepository? = null
    private var _locationRepository: LocationRepository? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    private fun initDatabase(): AppDatabase {
        if (database == null) {
            val ctx = context ?: throw IllegalStateException("ServiceLocator not initialized")
            database = Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
        }
        return database!!
    }

    private fun initTripRepository(): TripRepository {
        if (_tripRepository == null) {
            val db = initDatabase()
            _tripRepository = TripRepositoryImpl(db.tripDao(), db.locationPointDao(), db.batterySampleDao())
        }
        return _tripRepository!!
    }

    private fun initBleRepository(): BleRepository {
        if (_bleRepository == null) {
            val ctx = context ?: throw IllegalStateException("ServiceLocator not initialized")
            _bleRepository = BleRepositoryImpl(ctx, StandardGattBatteryDataSource())
        }
        return _bleRepository!!
    }

    private fun initLocationRepository(): LocationRepository {
        if (_locationRepository == null) {
            val ctx = context ?: throw IllegalStateException("ServiceLocator not initialized")
            _locationRepository = LocationRepositoryImpl(ctx)
        }
        return _locationRepository!!
    }

    // Public accessors for Service
    val tripRepository: TripRepository get() = initTripRepository()
    val bleRepository: BleRepository get() = initBleRepository()
    val locationRepository: LocationRepository get() = initLocationRepository()

    fun createSetupViewModel(): SetupViewModel {
        val startTripUseCase = StartTripUseCase(initTripRepository())
        return SetupViewModel(startTripUseCase, initBleRepository())
    }

    fun createDashboardViewModel(): DashboardViewModel {
        return DashboardViewModel(
            initTripRepository(),
            StopTripUseCase(initTripRepository()),
            SetTripStatusUseCase(initTripRepository()),
            CanReturnToStartUseCase()
        )
    }

    fun createHistoryViewModel(): HistoryViewModel {
        return HistoryViewModel(initTripRepository())
    }
}
