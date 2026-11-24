package de.uniwuerzburg.omosim.utils

/**
 * Transforms latitude and longitude coordinates to the EPSG code
 * https://de.wikipedia.org/wiki/UTM-Koordinatensystem#/media/Datei:UTM-Zone.svg
 * Does not determine the letter as it does affect the EPSG code.
 * For the poles it will fall back to UPS.
 *
 * Example: Germany (9.X, 49.X) -> method returns "EPSG:32632"
 *
 * @param lat latitude coordinate
 * @param lon longitude coordinate
 */
fun getUTMZoneEpsgCode(lat: Double, lon: Double): String {
    require(lon in -180.0..180.0)
    require(lat in -90.0..90.0)

    // Calculate longitudinal zone number
    var zoneNumber = ((lon + 180.0) / 6.0).toInt() + 1

    // Edge Cases
    if (zoneNumber == 61) zoneNumber = 60 // 180.0 longitude edge case
    when (lat) {
        // Norway
        in 56.0..<64.0 -> {
            if (lon in 3.0..<12.0) {
                zoneNumber = 32
            }
        }
        // Svalbard
        in 72.0..84.0 -> {
            zoneNumber = when (lon) {
                in 0.0..<9.0 -> 31
                in 9.0..<21.0 -> 33
                in 21.0..<33.0 -> 35
                in 33.0..<42.0 -> 37
                else -> zoneNumber
            }
        }
    }
    if ((lat > 84.0) ||  (lat < -80.0)) { // Poles
        zoneNumber = 61 // Will results in UPS, not UTM
    }

    val epsgBase = if (lat >= 0.0) 32600 else 32700

    return "EPSG:${epsgBase + zoneNumber}"
}