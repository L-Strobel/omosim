package de.uniwuerzburg.omosim.core.models

import de.uniwuerzburg.omosim.core.*
import de.uniwuerzburg.omosim.io.geojson.property.BuildingProperties
import de.uniwuerzburg.omosim.io.geojson.GeoJsonFeatureCollection
import de.uniwuerzburg.omosim.utils.CRSTransformer
import org.apache.commons.math3.ml.clustering.Clusterable
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point

/**
 * A building. Can be the location of an activity.
 *
 * @param osmID OpenStreetMap ID
 * @param coord Coordinates of centroid in model CRS (Distance unit: meters)
 * @param latlonCoord coordinates in lat-lon
 * @param odZone origin-destination zone (TAZ). Only relevant if OD-data is provided.
 * @param inFocusArea is the building inside the focus area?
 * @param attractions KEY: id, VAL: Attraction value of that building for the distance choice function with that id
 * @param point Coordinates of the building as a geometry type
 * @param cell Grid cell the building is inside in
 */
class Building  (
    val osmID: Long,
    override val coord: Coordinate,
    override val latlonCoord: Coordinate,
    override var odZone: ODZone?,
    override val inFocusArea: Boolean,
    override var attractions: MutableMap<Int, Double>,
    override val population: Double,
    val point: Point,
    var cell: Cell? = null,
    val osmProperties: BuildingProperties
) : RealLocation, Clusterable {
    override val avgDistanceToSelf = 0.0
    val attractionScaler: MutableMap<Int, Double> = mutableMapOf() // For calibration

    companion object {
        /**
         * Create a list of buildings from a GeoJSON object.
         *
         * @param collection GeoJSON object
         * @param geometryFactory GeometryFactory
         * @param transformer Used for CRS conversion
         * @param dcFunctions destination choice functions for which we need to compute the attraction value. There can
         * only be one destination function per activity type right now.
         * @return buildings
         */
        fun fromGeoJson(collection: GeoJsonFeatureCollection<BuildingProperties>, geometryFactory: GeometryFactory,
                        transformer: CRSTransformer,
                        dcFunctions: Map<ActivityType, LocationChoiceDCWeightFun>): List<Building> {
            return collection.features.map {
                val properties = it.properties
                val point = transformer.toModelCRS( it.geometry.toJTS(geometryFactory) ).centroid

                val attractions = dcFunctions.map { (_, v) -> v.id to v.calcAttraction(properties)}
                    .toMap().toMutableMap()

                Building(
                    osmID = properties.osm_id,
                    coord = point.coordinate,
                    latlonCoord = transformer.toLatLon(point).coordinate,
                    inFocusArea = properties.in_focus_area,
                    attractions = attractions,
                    population = properties.population ?: 0.0,
                    odZone = null,
                    point = point,
                    osmProperties = properties
                )
            }
        }
    }

    override fun recalculateAttractions(dcFunctions: List<LocationChoiceDCWeightFun>) {
        for (v in dcFunctions) {
            attractions[v.id] = v.calcAttraction(osmProperties) * (attractionScaler[v.id] ?: 1.0)
        }
    }

    override fun updateAttractionScaler(dcFunction: LocationChoiceDCWeightFun, value: Double) {
        attractionScaler[dcFunction.id] = value * (attractionScaler[dcFunction.id] ?: 1.0)
        recalculateAttractions(listOf(dcFunction))
    }

    override fun resetAttractionScaler(dcFunction: LocationChoiceDCWeightFun) {
        attractionScaler[dcFunction.id] = 1.0
        recalculateAttractions(listOf(dcFunction))
    }

    override fun getPoint(): DoubleArray {
        return arrayOf(coord.x, coord.y).toDoubleArray()
    }

    override fun getAggLoc() : AggLocation? {
        return cell
    }
}