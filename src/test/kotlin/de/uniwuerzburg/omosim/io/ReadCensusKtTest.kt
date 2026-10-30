package de.uniwuerzburg.omosim.io

import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.PopulationConfig
import de.uniwuerzburg.omosim.io.geojson.GeoJsonFeatureCollectionNoProperties
import de.uniwuerzburg.omosim.io.geojson.GeoJsonGeometryCollection
import de.uniwuerzburg.omosim.io.geojson.GeoJsonNoProperties
import de.uniwuerzburg.omosim.io.json.readJsonStream
import de.uniwuerzburg.omosim.io.osm.BuildingData
import de.uniwuerzburg.omosim.io.osm.readOSM
import de.uniwuerzburg.omosim.utils.CRSTransformer
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.GeometryFactory
import java.nio.file.Paths
import java.util.*

class ReadCensusKtTest {
    private val geometryFactory = GeometryFactory()

    private fun setup() : Pair<List<BuildingData>, CRSTransformer> {
        val areaString = Omosim::class.java.classLoader.getResource("tinyTown/test_area.geojson")!!.readText(Charsets.UTF_8)
        val osmFile = Paths.get(Omosim::class.java.classLoader.getResource("tinyTown/test.osm.pbf")!!.toURI())

        // Read OSM-File
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

        return Pair(buildings, transformer)
    }

    /**
     * Test the ReadCensus
     */
    @Test
    fun readCensusPerfectFitTest() {
        val (buildings, transformer) = setup()
        val censusFile = Paths.get(Omosim::class.java.classLoader.getResource("tinyTown/testCensusPerfectFit.geojson")!!.toURI())

        // Read Census file
        readCensus(buildings, transformer, geometryFactory, censusFile, Random())
        val popTstBuilding = buildings
            .filter { it.osm_id == 387201621.toLong() }
            .sumOf { it.population ?: 0.0 }.toInt()
        assert(popTstBuilding == 5)
    }

    @Test
    fun readCensusLooseTest() {
        val (buildings, transformer) = setup()
        val censusFile = Paths.get(Omosim::class.java.classLoader.getResource("tinyTown/testCensusPerfectFit.geojson")!!.toURI())

        // Read Census file
        readCensus(buildings, transformer, geometryFactory, censusFile, Random())
        val popTstBuilding = buildings
            .filter { it.osm_id == 387201621.toLong() }
            .sumOf { it.population ?: 0.0 }.toInt()
        assert(popTstBuilding == 5)
    }

    @Test
    fun readCensusOneStratumTest() {
        val (buildings, transformer) = setup()
        val censusFile = Paths.get(Omosim::class.java.classLoader.getResource("tinyTown/testCensusPerfectFit.geojson")!!.toURI())

        // Read Census file
        readCensus(buildings, transformer, geometryFactory, censusFile, Random())
        val populated = buildings.filter { (it.population ?: 0.0) > 0 }
        assert(populated.isNotEmpty())

        for (building in populated) {
            val shares = building.demographics?.localStrataShares
            assert(shares != null) { "Building ${building.osm_id} has no strata shares" }
            assert(shares!!.size == 1)
            assert(shares[0] == 1.0) {
                "Building ${building.osm_id} has no 1.0 stratum"
            }
            assert(shares.sum() == 1.0) {"Building ${building.osm_id}: Stratum shares don't add up to 1.0!"}
        }
    }

    @Test
    fun readCensusTwoStrataTest() {
        val (buildings, transformer) = setup()
        val censusFile = Paths.get(Omosim::class.java.classLoader.getResource("tinyTown/testCensusLoose.geojson")!!.toURI())

        // Read Census file
        readCensus(buildings, transformer, geometryFactory, censusFile, Random())
        val populated = buildings.filter { (it.population ?: 0.0) > 0 }
        assert(populated.isNotEmpty())

        for (building in populated) {
            val shares = building.demographics?.localStrataShares
            assert(shares != null) { "Building ${building.osm_id} has no strata shares" }
            assert(shares!!.size == 2)
            assert(shares[0] == 0.4) {
                "Building ${building.osm_id} has no 0.4 stratum"
            }
            assert(shares[1] == 0.6) {
                "Building ${building.osm_id} has no 0.6 stratum"
            }
            assert(shares.sum() == 1.0)  {"Building ${building.osm_id}: Stratum shares don't add up to 1.0!"}
        }
    }

}