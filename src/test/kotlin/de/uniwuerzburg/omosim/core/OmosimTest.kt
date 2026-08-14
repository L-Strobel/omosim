package de.uniwuerzburg.omosim.core

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import de.uniwuerzburg.omosim.core.models.MapDataSource
import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.core.models.ModeChoiceOption
import de.uniwuerzburg.omosim.core.models.Weekday
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.GeometryFactory
import java.io.File

class OmosimTest {
    val areaFile = File(Omosim::class.java.classLoader.getResource("test_area.geojson")!!.file)
    val osmFile  = File(Omosim::class.java.classLoader.getResource("test.osm.pbf")!!.file)
    val omosim   = Omosim(areaFile, osmFile, cache = false)
    val geometryFactory = GeometryFactory()

    @Test
    fun getBuildingsTest() {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val memCacheDir = fileSystem.getPath("/cache/")

        val buildings = omosim.getBuildings(
            omosim.focusArea,
            omosim.focusArea,
            osmFile,
            0.0,
            omosim.transformer,
            geometryFactory,
            null,
            memCacheDir,
            false,
            mapOf(),
            MapDataSource.OSM,
            nWorker = 1
        )

        assert(buildings.sumOf { it.osmProperties.number_place_of_worship }.toInt() == 1)
        assert(buildings.size == 5)
    }

    @Test
    fun getBuildingsFromCacheTest() {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val memCacheDir = fileSystem.getPath("/cache/")

        // Fill cache
        omosim.getBuildings(
            omosim.focusArea,
            omosim.focusArea,
            osmFile,
            0.0,
            omosim.transformer,
            geometryFactory,
            null,
            memCacheDir,
            true,
            mapOf(),
            MapDataSource.OSM,
            nWorker = 1
        )

        // Load from cache
        val buildings = omosim.getBuildings(
            omosim.focusArea,
            omosim.focusArea,
            File("/does/not/exist"), // Dead file to prevent reading
            0.0,
            omosim.transformer,
            geometryFactory,
            null,
            memCacheDir,
            true,
            mapOf(),
            MapDataSource.OSM,
            nWorker = 1
        )

        assert(buildings.sumOf { it.osmProperties.number_place_of_worship }.toInt() == 1)
        assert(buildings.size == 5)
    }

    @Test
    fun runTestDays() {
        val agents = omosim.run(1, Weekday.TU, 3, verbose = false)
        val actualDays = agents.first().mobilityDemand.map { it.dayType }
        val expectedDays = listOf(Weekday.TU, Weekday.WE, Weekday.TH)

        assertEquals(expectedDays, actualDays)
    }

    @Test
    fun runTestSeed() {
        omosim.mainRng.setSeed(123) // Reset rng
        val agents1 = omosim.run(100, verbose = false)

        omosim.mainRng.setSeed(123) // Reset rng
        val agents2 = omosim.run(100, verbose = false)

        assertEquals(agents1, agents2)
    }

    @Test
    fun doModeChoiceTestCarOnly() {
        val agents = omosim.run(100, verbose = false)
        omosim.doModeChoice(agents, ModeChoiceOption.CAR_ONLY, withPath = false, verbose = false)

        val allCar = agents.
            flatMap { agent ->
                agent.mobilityDemand.flatMap {
                    diary -> diary.trips.map {
                        trip -> trip.mode
                    }
                }
            }
            .all { it == Mode.CAR_DRIVER }

        assertTrue(allCar)
    }
}