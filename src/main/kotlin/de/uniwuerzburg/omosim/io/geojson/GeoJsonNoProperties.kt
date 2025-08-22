package de.uniwuerzburg.omosim.io.geojson

import kotlinx.serialization.Serializable

/**
 * Work around structure for json objects with empty properties = {} and raw GeometryCollections
 */
@Serializable
sealed interface GeoJsonNoProperties