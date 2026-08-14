package de.uniwuerzburg.omosim.core

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import de.uniwuerzburg.omosim.core.models.MapDataSource
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.GeometryFactory
import java.io.File

class GetBuildingsTest {
    val areaFile = File(Omosim::class.java.classLoader.getResource("tinyTown/test_area.geojson")!!.file)
    val osmFile  = File(Omosim::class.java.classLoader.getResource("tinyTown/test.osm.pbf")!!.file)
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
}