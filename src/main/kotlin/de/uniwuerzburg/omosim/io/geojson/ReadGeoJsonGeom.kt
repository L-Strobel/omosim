package de.uniwuerzburg.omosim.io.geojson

import de.uniwuerzburg.omosim.io.json.readJson
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import java.nio.file.Path

/**
 * Reads GeoJson file. Will only use geo-information.
 * Throws away property entries.
 *
 * If the property entries are required use de.uniwuerzburg.omosim.io.json.ReadJson
 */
fun readGeoJsonGeom(areaFile: Path, geometryFactory: GeometryFactory): Geometry {
    val areaColl: GeoJsonNoProperties = readJson(areaFile)
    return if (areaColl is GeoJsonFeatureCollectionNoProperties) {
        geometryFactory.createGeometryCollection(
            areaColl.features.map { it.geometry.toJTS(geometryFactory) }.toTypedArray()
        )
    } else {
        (areaColl as GeoJsonGeom).toJTS(geometryFactory)
    }
}