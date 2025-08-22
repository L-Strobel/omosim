package de.uniwuerzburg.omosim.io.geojson.property

import de.uniwuerzburg.omosim.io.geojson.GeoJsonProperties
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OvertureLandUseProperties(
    @SerialName("class")
    val landUseClass: String
) : GeoJsonProperties()