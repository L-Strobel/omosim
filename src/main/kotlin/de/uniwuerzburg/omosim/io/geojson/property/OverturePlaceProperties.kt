package de.uniwuerzburg.omosim.io.geojson.property

import de.uniwuerzburg.omosim.io.geojson.GeoJsonProperties
import kotlinx.serialization.Serializable

@Serializable
data class OverturePlaceProperties(
    val confidence: Double,
    val categories: OverturePlaceCategories
) : GeoJsonProperties()