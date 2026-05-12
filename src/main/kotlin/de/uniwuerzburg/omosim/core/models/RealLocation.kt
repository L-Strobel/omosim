package de.uniwuerzburg.omosim.core.models

import de.uniwuerzburg.omosim.core.LocationChoiceDCWeightFun

/**
 * A location that is inside the area where OSM data is available, i.e. not a dummy location
 */
interface RealLocation : LocationOption {
    val population: Double
    val attractions: Map<Int, Double>

    fun recalculateAttractions(dcFunctions: List<LocationChoiceDCWeightFun>)
    fun updateAttractionScaler(dcFunction: LocationChoiceDCWeightFun, value: Double)
    fun resetAttractionScaler(dcFunction: LocationChoiceDCWeightFun)
}