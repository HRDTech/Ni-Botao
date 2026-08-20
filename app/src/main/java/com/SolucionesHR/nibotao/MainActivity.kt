package com.SolucionesHR.nibotao

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.SolucionesHR.nibotao.presentation.dashboard.DashboardRoute
import com.SolucionesHR.nibotao.presentation.history.HistoryScreen
import com.SolucionesHR.nibotao.presentation.navigation.Screen
import com.SolucionesHR.nibotao.presentation.setup.SetupScreen
import com.SolucionesHR.nibotao.service.TripForegroundService
import com.SolucionesHR.nibotao.ui.theme.AutonomiaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensurePermissions()

        setContent {
            AutonomiaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        onStartService = ::startTripService,
                        onStopService = ::stopTripService,
                        onCloseApp = ::closeApp
                    )
                }
            }
        }
    }

    private fun ensurePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions.launch(missing.toTypedArray())
    }

    private fun startTripService(tripId: Long, autonomyKm: Float, deviceAddress: String?) {
        val intent = Intent(this, TripForegroundService::class.java).apply {
            putExtra(TripForegroundService.EXTRA_TRIP_ID, tripId)
            putExtra(TripForegroundService.EXTRA_INITIAL_AUTONOMY, autonomyKm)
            deviceAddress?.let { putExtra(TripForegroundService.EXTRA_BLUETOOTH_ADDRESS, it) }
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTripService() {
        stopService(Intent(this, TripForegroundService::class.java))
    }

    private fun closeApp() {
        finishAffinity()
    }
}

@Composable
fun AppNavHost(
    onStartService: (Long, Float, String?) -> Unit,
    onStopService: () -> Unit,
    onCloseApp: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val repo = remember { com.SolucionesHR.nibotao.di.ServiceLocator.tripRepository }
    var pendingTrip by remember { mutableStateOf<com.SolucionesHR.nibotao.domain.model.Trip?>(null) }
    var askedTripId by remember { mutableStateOf<Long?>(null) }

    NavHost(navController = navController, startDestination = Screen.Setup.route) {

        composable(Screen.Setup.route) {
            SetupScreen(
                onTripStarted = { tripId, autonomyKm, _, deviceAddress ->
                    onStartService(tripId, autonomyKm, deviceAddress)
                    navController.navigate(Screen.Dashboard.createRoute(tripId, autonomyKm, deviceAddress))
                }
            )
        }

        composable(
            route = Screen.Dashboard.route,
            arguments = listOf(
                navArgument("tripId") { type = NavType.LongType },
                navArgument("autonomyKm") { type = NavType.FloatType },
                navArgument("deviceAddress") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: 0L

            DashboardRoute(
                tripId = tripId,
                onStopService = onStopService,
                onOpenHistory = {
                    navController.navigate(Screen.History.route) {
                        launchSingleTop = true
                    }
                },
                onParkAndCloseApp = onCloseApp
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }

    if (pendingTrip != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Recorrido pendiente") },
            text = { Text("Detectamos km pendientes. ¿Recargaste la batería?") },
            confirmButton = {
                TextButton(onClick = {
                    val trip = pendingTrip ?: return@TextButton
                    scope.launch {
                        onStopService()
                        repo.finishTrip(
                            tripId = trip.id,
                            endTimestamp = System.currentTimeMillis(),
                            estimatedErrorKm = null,
                            distanceTraveledKm = trip.distanceTraveledKm,
                            batteryUsedPercent = trip.batteryUsedPercent,
                            finalBatteryVoltage = trip.finalBatteryVoltage
                        )
                        askedTripId = trip.id
                        pendingTrip = null
                        navController.navigate(Screen.Setup.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                        }
                    }
                }) {
                    Text("Sí, recargué")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val trip = pendingTrip ?: return@TextButton
                    askedTripId = trip.id
                    pendingTrip = null
                    onStartService(trip.id, trip.initialAutonomyKm, null)
                    navController.navigate(Screen.Dashboard.createRoute(trip.id, trip.initialAutonomyKm, "none"))
                }) {
                    Text("No, continuar")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        repo.observeActiveTrip().collect { active ->
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            val isDashboard = currentRoute.orEmpty().startsWith("dashboard")
            if (active != null && !isDashboard && pendingTrip == null && askedTripId != active.id) {
                pendingTrip = active
            }
        }
    }
}
