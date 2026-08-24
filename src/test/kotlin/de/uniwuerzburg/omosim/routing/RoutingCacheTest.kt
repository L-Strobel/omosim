package de.uniwuerzburg.omosim.routing

import com.graphhopper.GraphHopper
import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.Cell
import de.uniwuerzburg.omosim.utils.CRSTransformer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import java.nio.file.Path
import java.nio.file.Paths

class RoutingCacheTest {
    val geometryFactory = GeometryFactory()
    val transformer = CRSTransformer(11.343787040864559)
    val originPoint: Point = geometryFactory.createPoint(
        Coordinate(47.998168654396814, 11.343787040864559)
    )
    val destinationPoint: Point = geometryFactory.createPoint(
        Coordinate(48.00489667171546, 11.344686293730366)
    )
    val origin = Cell(
        1,
        transformer.toModelCRS(originPoint).coordinate, // Dummy
        originPoint.coordinate,
        listOf()
    )
    val destination = Cell(
        2,
        transformer.toModelCRS(destinationPoint).coordinate, // Dummy
        destinationPoint.coordinate,
        listOf()
    )

    companion object {
        lateinit var hopper: GraphHopper

        @JvmStatic
        @BeforeAll
        fun setup(@TempDir tempDir: Path) {
            val osmFile: Path = Paths.get(
                Omosim::class.java.classLoader.getResource("smallTown/starnberg.osm.pbf")!!.toURI()
            )
            val ghParent = tempDir.resolve("routing-graph-cache")
            val ghPath   = ghParent.resolve( "osm")
            hopper = createGraphHopper(
                osmFile.toString(),
                ghPath.toString(),
                1
            )
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            hopper.close()
        }
    }

    @Test
    fun getDistancesDirect() {
        val routingCache = RoutingCache(RoutingMode.GRAPHHOPPER, hopper)
        val distances = routingCache.getDistances(origin, listOf(destination))
        val distance = distances[0]
        assert((distance > 800) and (distance < 1200)) // Bounds from Google Maps
    }

    @Test
    fun getDistancesDirectBeeline() {
        val routingCache = RoutingCache(RoutingMode.BEELINE, null)
        val distances = routingCache.getDistances(origin, listOf(destination))
        val distance = distances[0]
        assert((distance > 700) and (distance < 800)) // Bounds from Google Maps
    }

    @Test
    fun getDistancesCache(@TempDir tempDir: Path) {
        val routingCache = RoutingCache(RoutingMode.GRAPHHOPPER, hopper)

        // Fill and save cache
        routingCache.load(
            listOf(origin, destination),
            tempDir,
            priorityValues = listOf(2.0, 1.0)
        )

        // Load from cache
        routingCache.load(
            listOf(origin, destination),
            tempDir,
            priorityValues = listOf(2.0, 1.0)
        )

        val distances = routingCache.getDistances(origin, listOf(destination))
        val distance = distances[0]
        assert((distance > 800) and (distance < 1200)) // Bounds from Google Maps
    }
}