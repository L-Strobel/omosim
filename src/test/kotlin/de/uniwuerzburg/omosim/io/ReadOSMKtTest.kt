package de.uniwuerzburg.omosim.io

import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.io.geojson.GeoJsonFeatureCollectionNoProperties
import de.uniwuerzburg.omosim.io.geojson.GeoJsonGeometryCollection
import de.uniwuerzburg.omosim.io.geojson.GeoJsonNoProperties
import de.uniwuerzburg.omosim.io.osm.readOSM
import de.uniwuerzburg.omosim.utils.CRSTransformer
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.GeometryFactory
import java.io.File

internal class ReadOSMKtTest {
    /**
     * Test the osm.pbf reader
     */
    @Test
    fun readOSMTest() {
        val areaString = Omosim::class.java.classLoader.getResource("test_area.geojson")!!.readText(Charsets.UTF_8)
        val osmFile = File(Omosim::class.java.classLoader.getResource("test.osm.pbf")!!.file)
        val geometryFactory = GeometryFactory()

        val areaColl: GeoJsonNoProperties = jsonHandler.decodeFromString(areaString)
        val focusArea = if (areaColl is GeoJsonFeatureCollectionNoProperties) {
            geometryFactory.createGeometryCollection(
                areaColl.features.map { it.geometry.toJTS(geometryFactory) }.toTypedArray()
            ).union()
        } else {
            (areaColl as GeoJsonGeometryCollection).toJTS(geometryFactory).union()
        }

        val transformer = CRSTransformer( focusArea.centroid.coordinate.y )
        val buildings = readOSM(focusArea, focusArea, osmFile, geometryFactory, transformer)

        assert(buildings.sumOf { it.nPlaceOfWorship }.toInt() == 1)
        assert(buildings.size == 5)
    }
}