package com.SolucionesHR.nibotao.presentation.navigation

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Dashboard : Screen("dashboard/{tripId}/{autonomyKm}/{deviceAddress}") {
        fun createRoute(tripId: Long, autonomyKm: Float, deviceAddress: String?) =
            "dashboard/$tripId/$autonomyKm/${deviceAddress ?: "none"}"
    }
    data object History : Screen("history")
}
