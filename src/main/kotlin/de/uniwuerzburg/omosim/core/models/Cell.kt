package de.uniwuerzburg.omosim.core.models

import de.uniwuerzburg.omosim.core.LocationChoiceDCWeightFun
import org.locationtech.jts.geom.Coordinate

/**
 * Routing cell. Group of buildings used to faster calculate the approximate distance by car.
 *
 * @param id ID used for hashing
 * @param coord Coordinates of centroid in model CRS (Distance unit: meters)
 * @param latlonCoord Coordinates of centroid in lat-lon
 * @param buildings Buildings associated with the cell
 */
data class Cell (
    val id: Int,
    override val coord: Coordinate,
    override val latlonCoord: Coordinate,
    val buildings: List<Building>,
) : RealLocation, AggLocation {
    override val avgDistanceToSelf = buildings.map { a ->
        buildings.map { b ->
            a.coord.distance(b.coord)
        }
    }.flatten().sum() / (buildings.size * buildings.size)

    // Most common taz (Normally null at initialization)
    override var odZone = buildings.groupingBy { it.odZone }.eachCount().maxByOrNull { it.value }!!.key

    override val inFocusArea = buildings.any { it.inFocusArea }

    override var attractions = buildings.map { it.attractions }
        .flatMap { map -> map.entries }
        .groupBy ({ it.key },{ it.value })
        .mapValues { it.value.sum() }

    override val population = buildings.sumOf { it.population }

    override fun getAggLoc() : AggLocation {
        return this
    }

    override fun hashCode(): Int {
        return id
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Cell

        if (id != other.id) return false
        if (coord != other.coord) return false
        if (latlonCoord != other.latlonCoord) return false
        if (buildings != other.buildings) return false
        if (odZone != other.odZone) return false
        if (inFocusArea != other.inFocusArea) return false
        if (attractions != other.attractions) return false
        if (population != other.population) return false

        return true
    }

    override fun recalculateAttractions(dcFunctions: List<LocationChoiceDCWeightFun>) {
        for (building in buildings) {
            building.recalculateAttractions(dcFunctions)
        }

        attractions = buildings.map { it.attractions }
            .flatMap { map -> map.entries }
            .groupBy ({ it.key },{ it.value })
            .mapValues { it.value.sum() }
    }

    override fun setAttractionScaler(dcFunction: LocationChoiceDCWeightFun, value: Double) {
        for (building in buildings) {
            building.setAttractionScaler(dcFunction, value)
        }
        recalculateAttractions(listOf(dcFunction))
    }

    override fun getAttractionScaler(dcFunction: LocationChoiceDCWeightFun): Double {
        val scalers = buildings.map { it.getAttractionScaler(dcFunction) }
        val attractions = buildings.map { it.attractions[dcFunction.id]!! }

        var baseAttraction = 0.0
        var scaledAttraction = 0.0
        for (i in buildings.indices) {
            baseAttraction += attractions[i] / scalers[i]
            scaledAttraction += attractions[i]
        }

        return if (baseAttraction == 0.0) {
            1.0
        } else {
            scaledAttraction / baseAttraction
        }
    }

    override fun resetAttractionScaler(dcFunction: LocationChoiceDCWeightFun) {
        for (building in buildings) {
            building.resetAttractionScaler(dcFunction)
        }
        recalculateAttractions(listOf(dcFunction))
    }
}