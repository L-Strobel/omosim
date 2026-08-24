package de.uniwuerzburg.omosim.routing

import com.graphhopper.GraphHopper
import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.Cell
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.locationtech.jts.geom.Coordinate
import java.nio.file.Path
import java.nio.file.Paths

class RoutingCacheTest {
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
    fun getDistances() {
        val routingCache = RoutingCache(RoutingMode.GRAPHHOPPER, hopper)
        val origin = Cell(
            1,
            Coordinate(-1.0, -1.0), // Dummy
            Coordinate(47.998168654396814, 11.343787040864559),
            listOf()
        )
        val destination = Cell(
            2,
            Coordinate(-1.0, -1.0), // Dummy
            Coordinate(48.00489667171546, 11.344686293730366),
            listOf()
        )
        val distances = routingCache.getDistances(origin, listOf(destination))
        val distance = distances[0]
        assert((distance > 800) and (distance < 1200)) // Bounds from Google Maps
    }
}