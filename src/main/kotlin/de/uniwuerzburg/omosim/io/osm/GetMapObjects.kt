package de.uniwuerzburg.omosim.io.osm


import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.openstreetmap.osmosis.core.filter.common.IdTrackerType
import java.nio.file.Path

fun getMapObjects (
    fullArea: Geometry, osmFile: Path, geometryFactory: GeometryFactory): MutableList<MapObject> {
    // Prepare osmosis pipeline
    val reader = OSMSourceFactory.forPath(osmFile)
    val processor = OSMProcessor(IdTrackerType.Dynamic, geometryFactory)
    val geomFilter = GeometryFilter(
        fullArea,
        geometryFactory,
        IdTrackerType.Dynamic,
        clipIncompleteEntities = true,
        completeWays = false,
        completeRelations = false,
        cascadingRelations = false
    )
    geomFilter.setSink(processor)
    reader.setSink(geomFilter)

    // Read osm.pbf
    reader.run()

    // Clean up
    processor.close()

    return processor.mapObjects
}
