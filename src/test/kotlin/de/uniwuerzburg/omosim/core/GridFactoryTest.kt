package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.core.models.Building
import de.uniwuerzburg.omosim.core.models.Landuse
import de.uniwuerzburg.omosim.io.geojson.property.BuildingProperties
import de.uniwuerzburg.omosim.utils.CRSTransformer
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory

class GridFactoryTest {
    val geometryFactory = GeometryFactory()
    val transformer = CRSTransformer(0.0) // At time of writing: only used to turn coords into lat/lon not the other way round

    // Same properties for all buildings
    val properties =  BuildingProperties(
        osm_id = -1,
        in_focus_area = true,
        area = 200.0,
        population = 12.0,
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
        levels = 3
    )

    // Locations
    val coords = listOf(
        Coordinate(1.0, 1.0),
        Coordinate(2.0, 2.0),
        Coordinate(3.0, 3.0),
        Coordinate(11.0, 11.0),
        Coordinate(12.0, 12.0),
        Coordinate(13.0, 13.0),
    )

    val targetGroups = listOf(
        0,
        0,
        0,
        1,
        1,
        1
    )

    val buildings = coords.withIndex().map { (i, coord) ->
        Building(
            i.toLong(),
            coord,
            transformer.toLatLon(geometryFactory.createPoint(coord)).coordinate,
            null,
            true,
            mutableMapOf(),
            1.0,
            geometryFactory.createPoint(coord),
            osmProperties = properties
        )
    }

    @Test
    fun makeClusterGridTest() {
        val grid = makeClusterGrid(
            5.0,
            buildings,
            geometryFactory,
            transformer,
            Dispatchers.Default,
            minClusterPerGroup = 2
        )

        val groupA = buildings.zip(targetGroups).filter { (building, group) ->
            group == 0
        }.map { (building, group) -> building }

        val groupB = buildings.zip(targetGroups).filter { (building, group) ->
            group == 1
        }.map { (building, group) -> building }

        var groupsCorrect = grid.size == 2
        for (cell in grid) {
            groupsCorrect = cell.buildings.all { it in groupA } or cell.buildings.all { it in groupB }
        }
        assert(groupsCorrect)
    }
}