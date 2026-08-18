package de.uniwuerzburg.omosim.core

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import de.uniwuerzburg.omosim.core.models.MapDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.GeometryFactory
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.Paths

class GetBuildingsTest {
    val geometryFactory = GeometryFactory()
    val areaFile: Path = Paths.get(Omosim::class.java.classLoader.getResource("tinyTown/test_area.geojson")!!.toURI())
    val osmFile: Path  = Paths.get(Omosim::class.java.classLoader.getResource("tinyTown/test.osm.pbf")!!.toURI())
    lateinit var omosim: Omosim
    lateinit var fs: FileSystem
    lateinit var memCacheDir: Path

    @BeforeEach
    fun setup() {
        fs = Jimfs.newFileSystem(Configuration.unix())
        memCacheDir = fs.getPath("/cache/")
        omosim = Omosim(areaFile, osmFile, cacheDir = memCacheDir)
    }

    @AfterEach
    fun teardown() {
        fs.close()
    }

    @Test
    fun getBuildingsTest() {
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
            Paths.get("/does/not/exist"), // Dead file to prevent reading
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