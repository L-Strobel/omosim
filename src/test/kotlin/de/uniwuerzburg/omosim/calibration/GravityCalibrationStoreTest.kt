package de.uniwuerzburg.omosim.calibration

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import de.uniwuerzburg.omosim.core.ByPopulation
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.Building
import de.uniwuerzburg.omosim.core.models.Cell
import de.uniwuerzburg.omosim.core.models.Landuse
import de.uniwuerzburg.omosim.io.geojson.property.BuildingProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import java.nio.file.FileSystem
import java.nio.file.Path

class GravityCalibrationStoreTest {
    val geometryFactory = GeometryFactory()
    lateinit var fs: FileSystem
    lateinit var memCacheDir: Path

    val dcFunctions = ActivityType.entries.associateWith { ByPopulation() }

    // Same properties for all buildings
    val properties =  BuildingProperties(
        osm_id = -1,
        in_focus_area = true,
        area = 1.0,
        population = 1.0, // Important
        landuse = Landuse.RESIDENTIAL,
        number_shops = 1.0,
        number_offices = 0.0,
        number_schools = 0.0,
        number_universities = 0.0,
        number_place_of_worship = 0.0,
        number_cafe = 0.0,
        number_fast_food = 0.0,
        number_kindergarten = 0.0,
        number_tourism = 0.0,
        levels = 1
    )

    private var bid = 0
    private fun makeBuilding(coord: Coordinate): Building {
        val attractions = dcFunctions.map { (_, v) -> v.id to v.calcAttraction(properties)}
            .toMap().toMutableMap()
        val building = Building(
            bid.toLong(),
            coord,
            coord,
            null,
            true,
            attractions,
            1.0,
            geometryFactory.createPoint(coord),
            osmProperties = properties
        )
        bid += 1
        return building
    }

    private var cid = 0
    private fun makeCell(coord: Coordinate): Cell {
        val buildings = listOf(
            makeBuilding(Coordinate(coord.x + 0.01, coord.y + 0.01)),
            makeBuilding(Coordinate(coord.x + 0.02, coord.y + 0.02)),
        )
        val cell = Cell(
            cid,
            coord,
            coord,
            buildings
        )
        buildings.forEach { it.cell = cell }
        cid += 1
        return cell
    }

    @BeforeEach
    fun setup() {
        fs = Jimfs.newFileSystem(Configuration.unix())
        memCacheDir = fs.getPath("/cache/")
    }

    @AfterEach
    fun teardown() {
        fs.close()
    }

    @Test
    fun scaleAttractionTest() {
        val cellA = makeCell(Coordinate(0.0, 0.0))
        val cellB = makeCell(Coordinate(0.1, 0.1))

        val attractionABefore = dcFunctions.values.map { cellA.attractions[it.id]!! }
        val attractionBBefore = dcFunctions.values.map { cellB.attractions[it.id]!! }

        // Scale attractions of A by 2
        for (f in dcFunctions.values) {
            cellA.setAttractionScaler(f, 2.0)
        }

        // Test
        val attractionAAfter = dcFunctions.values.map { cellA.attractions[it.id]!! }
        val attractionBAfter = dcFunctions.values.map { cellB.attractions[it.id]!! }

        for (i in dcFunctions.values.indices) {
            assertEquals(attractionABefore[i] * 2, attractionAAfter[i])
            assertEquals(attractionBBefore[i], attractionBAfter[i])
        }
    }

    @Test
    fun writeAndReadTest() {
        val cellA = makeCell(Coordinate(0.0, 0.0))
        val cellB = makeCell(Coordinate(0.1, 0.1))

        val attractionABefore = dcFunctions.values.map { cellA.attractions[it.id]!! }
        val attractionBBefore = dcFunctions.values.map { cellB.attractions[it.id]!! }

        // Scale attractions of A by 2
        for (f in dcFunctions.values) {
            cellA.setAttractionScaler(f, 2.0)
        }

        // Write
        GravityCalibrationStore.write(
            memCacheDir,
            cellA.buildings + cellB.buildings,
            dcFunctions
        )

        // Reset
        for (f in dcFunctions.values) {
            cellA.resetAttractionScaler(f)
            cellB.resetAttractionScaler(f)
        }

        // Read
        GravityCalibrationStore.read(
            memCacheDir,
            listOf(cellA, cellB),
            cellA.buildings + cellB.buildings,
            dcFunctions
        )

        // Test
        val attractionAAfter = dcFunctions.values.map { cellA.attractions[it.id]!! }
        val attractionBAfter = dcFunctions.values.map { cellB.attractions[it.id]!! }

        for (i in dcFunctions.values.indices) {
            assertEquals(attractionABefore[i] * 2, attractionAAfter[i])
            assertEquals(attractionBBefore[i], attractionBAfter[i])
        }
    }

}