package com.SolucionesHR.nibotao.data.map

/**
 * Abstracción del proveedor de mapa. La implementación por defecto usa OSMDroid
 * (OpenStreetMap) porque no requiere API key ni facturación y soporta caché de
 * tiles para uso parcialmente offline. Si en el futuro se requiere Google Maps
 * SDK, basta con crear otra implementación de esta interfaz sin tocar el resto
 * de la app (ver TripMap.kt para el composable que la consume).
 */
interface MapProvider {
    val name: String
}

class OsmDroidMapProvider : MapProvider {
    override val name: String = "OpenStreetMap (OSMDroid)"
}
