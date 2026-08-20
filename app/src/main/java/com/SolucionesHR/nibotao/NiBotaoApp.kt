package com.SolucionesHR.nibotao

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.SolucionesHR.nibotao.di.ServiceLocator
import org.osmdroid.config.Configuration

class NiBotaoApp : Application() {

    companion object {
        const val TRIP_NOTIFICATION_CHANNEL_ID = "trip_tracking_channel"
    }

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
        createNotificationChannel()

        // Configuración recomendada por OSMDroid para uso de tiles y caché offline.
        Configuration.getInstance().load(
            this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRIP_NOTIFICATION_CHANNEL_ID,
                "Recorrido en curso",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra los km restantes mientras la app registra tu recorrido"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
