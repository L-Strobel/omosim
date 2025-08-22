package de.uniwuerzburg.omosim.io.geojson.property

import de.uniwuerzburg.omosim.io.geojson.GeoJsonProperties
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GeoJSON representation of a census field.
 *
 * @param population Population of geometry
 */
@Serializable
@SerialName("CensusEntry")
data class CensusProperties (
    val population: Double
) : GeoJsonProperties()