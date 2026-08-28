package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.Cell
import de.uniwuerzburg.omosim.core.models.Weekday
import de.uniwuerzburg.omosim.routing.RoutingMode
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import java.nio.file.Path
import java.nio.file.Paths

class TrafficCountCalibrationContextTest {
    val geometryFactory = GeometryFactory()

    companion object {
        val areaFile: Path = Paths.get(Omosim::class.java.classLoader.getResource("smallTown/boundary.geojson")!!.toURI())
        val osmFile: Path  = Paths.get(Omosim::class.java.classLoader.getResource("smallTown/starnberg.osm.pbf")!!.toURI())
        val gtfsFile: Path = Paths.get(Omosim::class.java.classLoader.getResource("smallTown/starnberg_gtfs_clipped.zip")!!.toURI())
        val sensorFile: Path = Paths.get(Omosim::class.java.classLoader.getResource("smallTown/sensor1Measurement.txt")!!.toURI())
        lateinit var omosim: Omosim
        lateinit var context: TrafficCountCalibrationContext

        @JvmStatic
        @BeforeAll
        fun setup(@TempDir tempDir: Path) {
            omosim = Omosim(
                areaFile,
                osmFile,
                gtfsFile = gtfsFile,
                cacheDir = tempDir,
                routingMode = RoutingMode.GRAPHHOPPER,
                gridPrecision = 100.0
            )
            context = TrafficCountCalibrationContext(
                sensorFile,
                omosim,
                Weekday.UNDEFINED,
                null,
                tempDir
            )
        }
    }

    private fun closestCell(latLonCoord: Coordinate): Cell {
        val latLonPoint = geometryFactory.createPoint(latLonCoord)
        val point = omosim.transformer.toModelCRS(latLonPoint)
        return omosim.grid.minBy { point.coordinate.distance(it.coord) }
    }

    @Test
    fun affectedSensorsTestIncluded() {
        val affectedSensors = context.affectedSensors()

        val origin      = Coordinate(47.99668468651497, 11.336102512543762)
        val destination = Coordinate(47.99907026745015, 11.341789074721811)

        val od = Pair(closestCell(origin), closestCell(destination))
        assert(affectedSensors[od]?.any{it.name == "sensor_1"} == true)
    }

    @Test
    fun affectedSensorsTestExcluded() {
        val affectedSensors = context.affectedSensors()

        val origin      = Coordinate(47.99726644415606, 11.345357313597564)
        val destination = Coordinate(48.000361200879595, 11.345747407762016)

        val od = Pair(closestCell(origin), closestCell(destination))
        assert((affectedSensors[od] == null) || (affectedSensors[od]!!.none { it.name == "sensor_1" }) )
    }

    @Test
    fun altAffectedSensorsTestExcludedBecauseNotAlt() {
        val affectedSensors = context.affectedSensors()

        val origin      = Coordinate(47.996117268104335, 11.341576674575617)
        val destination = Coordinate(47.99907026745015, 11.341789074721811)

        val od = Pair(closestCell(origin), closestCell(destination))
        assert((affectedSensors[od] == null) || (affectedSensors[od]!!.none { it.name == "sensor_1" }) )
    }

    @Test
    fun altAffectedSensorsTestIncluded() {
        val affectedSensors = context.altAffectedSensors()

        val origin      = Coordinate(47.99668468651497, 11.336102512543762)
        val destination = Coordinate(47.99907026745015, 11.341789074721811)

        val od = Pair(closestCell(origin), closestCell(destination))
        val sensorsOd = affectedSensors[od]?.flatten()
        assert(sensorsOd?.any{it.name == "sensor_1"} == true)
    }

    @Test
    fun altAffectedSensorsTestExcluded() {
        val affectedSensors = context.altAffectedSensors()

        val origin      = Coordinate(47.99726644415606, 11.345357313597564)
        val destination = Coordinate(48.000361200879595, 11.345747407762016)

        val od = Pair(closestCell(origin), closestCell(destination))
        val sensorsOd = affectedSensors[od]?.flatten()
        assert((sensorsOd == null) || (sensorsOd.none { it.name == "sensor_1" }) )
    }

    @Test
    fun altAffectedSensorsTestIncludedBecauseOfAlt() {
        val affectedSensors = context.altAffectedSensors(altMaxRoutes = 5, altMaxSlower = 3.0, altMaxSimilarity = 0.2)

        val origin      = Coordinate(47.996117268104335, 11.341576674575617)
        val destination = Coordinate(47.99907026745015, 11.341789074721811)

        val od = Pair(closestCell(origin), closestCell(destination))
        val sensorsOd = affectedSensors[od]?.flatten()
        assert(sensorsOd?.any{it.name == "sensor_1"} == true)
    }
}