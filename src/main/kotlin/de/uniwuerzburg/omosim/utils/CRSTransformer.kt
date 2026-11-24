package de.uniwuerzburg.omosim.utils

import org.geotools.api.referencing.crs.ProjectedCRS
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.geotools.referencing.ReferencingFactoryFinder
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.geotools.referencing.cs.DefaultCartesianCS
import org.geotools.referencing.factory.ReferencingFactoryContainer
import org.geotools.referencing.operation.DefaultCoordinateOperationFactory
import org.geotools.referencing.operation.projection.TransverseMercator
import org.locationtech.jts.geom.Geometry
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Converts from lat lon coordinates to internally used coordinate system (UTM) and back.
 */
class CRSTransformer (
    centerLon: Double
) {
    init {
        System.setProperty("hsqldb.reconfig_logging", "false") // Silence hsqldb
        Logger.getLogger("hsqldb.db").level = Level.WARNING
    }

    private val latlonCRS = CRS.decode("EPSG:4326")
    private val utmCRS = getModelCRS(centerLon)
    private val transformerToLatLon = CRS.findMathTransform(utmCRS, latlonCRS)
    private val transformerToUTM = CRS.findMathTransform(latlonCRS, utmCRS)

    /**
     * Convert geometry to lat lon
     *
     * @param geometry Geometry to convert
     */
    fun toLatLon(geometry: Geometry) : Geometry {
        return JTS.transform(geometry, transformerToLatLon)
    }

    /**
     * Convert geometry to internally used coordinate system (UTM)
     *
     * @param geometry Geometry to convert
     */
    fun toModelCRS(geometry: Geometry) : Geometry {
        return JTS.transform(geometry, transformerToUTM)
    }

    private fun getModelCRS(centerLon: Double): ProjectedCRS {
        val mtFactory = ReferencingFactoryFinder.getMathTransformFactory(null)
        val factories = ReferencingFactoryContainer(null)

        val geoCRS = DefaultGeographicCRS.WGS84
        val cartCS = DefaultCartesianCS.GENERIC_2D

        val parameters = mtFactory.getDefaultParameters("Transverse_Mercator")
        parameters.parameter("central_meridian").setValue(centerLon)
        parameters.parameter("latitude_of_origin").setValue(0.0)
        parameters.parameter("scale_factor").setValue(0.9996)
        parameters.parameter("false_easting").setValue(500000.0)
        parameters.parameter("false_northing").setValue(0.0)

        val properties = Collections.singletonMap<String, Any>("name", "OMoSim-Model-CRS")
        val conversion = DefaultCoordinateOperationFactory()
            .createDefiningConversion(properties, TransverseMercator.Provider(), parameters)
        return factories.crsFactory.createProjectedCRS(properties, geoCRS, conversion, cartCS)
    }
}

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
