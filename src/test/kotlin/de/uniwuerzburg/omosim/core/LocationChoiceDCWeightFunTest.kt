package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.core.models.Building
import de.uniwuerzburg.omosim.core.models.Landuse
import de.uniwuerzburg.omosim.io.geojson.property.BuildingProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import kotlin.math.exp
import kotlin.math.ln

class LocationChoiceDCWeightFunTest {
    val geometryFactory = GeometryFactory()
    val dummyCoordinate = Coordinate(-1.0, 1.0)

    fun generateTestDestinations() : List<Building> {
        return listOf(
            Building(
                -1,
                dummyCoordinate,
                dummyCoordinate,
                null,
                true,
                mutableMapOf(),
                1.0,
                geometryFactory.createPoint(dummyCoordinate),
                osmProperties = BuildingProperties(
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
            ),
            Building(
                -2,
                dummyCoordinate,
                dummyCoordinate,
                null,
                true,
                mutableMapOf(),
                1.0,
                geometryFactory.createPoint(dummyCoordinate),
                osmProperties = BuildingProperties(
                    osm_id = -2,
                    in_focus_area = true,
                    area = 50.0,
                    population = 1.0,
                    landuse = Landuse.RESIDENTIAL,
                    number_shops = 0.0,
                    number_offices = 1.0,
                    number_schools = 0.0,
                    number_universities = 0.0,
                    number_place_of_worship = 0.0,
                    number_cafe = 0.0,
                    number_fast_food = 0.0,
                    number_kindergarten = 0.0,
                    number_tourism = 0.0,
                    levels = 1
                )
            )
        )
    }

    @Test
    fun byPopulationTest() {
        val testDestinations = generateTestDestinations()
        val distribution = ByPopulation()
        testDestinations.forEach { it.recalculateAttractions(listOf(distribution)) }

        val pFirst = distribution.calcForNoOrigin(testDestinations.first())
        val pSecond = distribution.calcForNoOrigin(testDestinations[1])
        val actual = pFirst / (pFirst + pSecond)
        val expected = 12.0 / (12.0 + 1.0)
        assertEquals(expected, actual)
    }

    @Test
    fun pureAttractionTest() {
        val testDestinations = generateTestDestinations()
        val distribution = PureAttraction(
            coeffResidentialArea = 2.0,
            coeffCommercialArea = 0.0,
            coeffRetailArea = 0.0,
            coeffIndustrialArea = 0.0,
            coeffOfficeArea = 0.0,
            coeffShopArea = 0.0,
            coeffSchoolArea = 0.0,
            coeffUniversityArea = 0.0,
            coeffOtherArea = 0.0,
            coeffOfficeUnits = 0.0,
            coeffShopUnits = 0.0,
            coeffSchoolUnits = 0.0,
            coeffUniUnits = 0.0,
            coeffPlaceOfWorshipUnits = 0.0,
            coeffCafeUnits = 0.0,
            coeffFastFoodUnits = 0.0,
            coeffKinderGartenUnits = 0.0,
            coeffTourismUnits = 0.0,
            coeffBuildingUnits = 0.0,
            coeffResidentialUnits = 0.0,
            coeffCommercialUnits = 0.0,
            coeffRetailUnits = 0.0,
            coeffIndustrialUnits = 0.0,
            useLevels = true
        )
        testDestinations.forEach { it.recalculateAttractions(listOf(distribution)) }

        val pFirst = distribution.calcForNoOrigin(testDestinations.first())
        val pSecond = distribution.calcForNoOrigin(testDestinations[1])
        val actual = pFirst / (pFirst + pSecond)

        val expectedWeightFirst = 200.0 * 2.0 * 3.0 + 1
        val expectedWeightSecond = 50.0 * 2.0 + 1.0
        val expected = expectedWeightFirst / (expectedWeightFirst + expectedWeightSecond)

        assertEquals(expected, actual)
    }

    @Test
    fun logNormDCUtilTest() {
        val testDestinations = generateTestDestinations()
        val distribution = LogNormDCUtil(
            coeffResidentialArea = 2.0,
            coeffCommercialArea = 0.0,
            coeffRetailArea = 0.0,
            coeffIndustrialArea = 0.0,
            coeffOfficeArea = 0.0,
            coeffShopArea = 0.0,
            coeffSchoolArea = 0.0,
            coeffUniversityArea = 0.0,
            coeffOtherArea = 0.0,
            coeffOfficeUnits = 0.0,
            coeffShopUnits = 0.0,
            coeffSchoolUnits = 0.0,
            coeffUniUnits = 0.0,
            coeffPlaceOfWorshipUnits = 0.0,
            coeffCafeUnits = 0.0,
            coeffFastFoodUnits = 0.0,
            coeffKinderGartenUnits = 0.0,
            coeffTourismUnits = 0.0,
            coeffBuildingUnits = 0.0,
            coeffResidentialUnits = 0.0,
            coeffCommercialUnits = 0.0,
            coeffRetailUnits = 0.0,
            coeffIndustrialUnits = 0.0,
            useLevels = true,
            coeff0 = -1.0,
            coeff1 = -2.0
        )
        testDestinations.forEach { it.recalculateAttractions(listOf(distribution)) }
        val distances = listOf(1500.0, 2000.0)

        val pFirst  = distribution.calcFor(testDestinations[0], distances[0])
        val pSecond = distribution.calcFor(testDestinations[1], distances[1])

        val actual = pFirst / (pFirst + pSecond)

        val distanceKm = distances.map { it / 1000.0 }

        val deterrenceFirst = -1.0 * ln(distanceKm[0]) * ln(distanceKm[0]) - 2.0 * ln(distanceKm[0])
        val expectedWeightFirst  = (200.0 * 2.0 * 3.0 + 1.0) * exp(deterrenceFirst)

        val deterrenceSecond = -1.0 * ln(distanceKm[1]) * ln(distanceKm[1]) - 2.0 * ln(distanceKm[1])
        val expectedWeightSecond = (50.0 * 2.0 + 1.0) * exp(deterrenceSecond)

        val expected = expectedWeightFirst / (expectedWeightFirst + expectedWeightSecond)

        assertEquals(expected, actual)
    }

    @Test
    fun logNormPowerDCUtilTest() {
        val testDestinations = generateTestDestinations()
        val distribution = LogNormPowerDCUtil(
            coeffResidentialArea = 2.0,
            coeffCommercialArea = 0.0,
            coeffRetailArea = 0.0,
            coeffIndustrialArea = 0.0,
            coeffOfficeArea = 0.0,
            coeffShopArea = 0.0,
            coeffSchoolArea = 0.0,
            coeffUniversityArea = 0.0,
            coeffOtherArea = 0.0,
            coeffOfficeUnits = 0.0,
            coeffShopUnits = 0.0,
            coeffSchoolUnits = 0.0,
            coeffUniUnits = 0.0,
            coeffPlaceOfWorshipUnits = 0.0,
            coeffCafeUnits = 0.0,
            coeffFastFoodUnits = 0.0,
            coeffKinderGartenUnits = 0.0,
            coeffTourismUnits = 0.0,
            coeffBuildingUnits = 0.0,
            coeffResidentialUnits = 0.0,
            coeffCommercialUnits = 0.0,
            coeffRetailUnits = 0.0,
            coeffIndustrialUnits = 0.0,
            useLevels = true,
            coeff0 = -1.0,
            coeff1 = -2.0,
            coeff2 = -3.0
        )
        testDestinations.forEach { it.recalculateAttractions(listOf(distribution)) }
        val distances = listOf(1500.0, 2000.0)

        val pFirst  = distribution.calcFor(testDestinations[0], distances[0])
        val pSecond = distribution.calcFor(testDestinations[1], distances[1])

        val actual = pFirst / (pFirst + pSecond)

        val distanceKm = distances.map { it / 1000.0 }

        val deterrenceFirst = - 1.0 * ln(distanceKm[0]) * ln(distanceKm[0]) - 2.0 * ln(distanceKm[0]) - 3.0 * distanceKm[0]
        val expectedWeightFirst  = (200.0 * 2.0 * 3.0 + 1.0) * exp(deterrenceFirst)

        val deterrenceSecond = - 1.0 * ln(distanceKm[1]) * ln(distanceKm[1]) - 2.0 * ln(distanceKm[1]) - 3.0 * distanceKm[1]
        val expectedWeightSecond = (50.0 * 2.0 + 1.0) * exp(deterrenceSecond)

        val expected = expectedWeightFirst / (expectedWeightFirst + expectedWeightSecond)

        assertEquals(expected, actual)
    }

    @Test
    fun combinedDCUtilTest() {
        val testDestinations = generateTestDestinations()
        val distribution = CombinedDCUtil(
            coeffResidentialArea = 2.0,
            coeffCommercialArea = 0.0,
            coeffRetailArea = 0.0,
            coeffIndustrialArea = 0.0,
            coeffOfficeArea = 0.0,
            coeffShopArea = 0.0,
            coeffSchoolArea = 0.0,
            coeffUniversityArea = 0.0,
            coeffOtherArea = 0.0,
            coeffOfficeUnits = 0.0,
            coeffShopUnits = 0.0,
            coeffSchoolUnits = 0.0,
            coeffUniUnits = 0.0,
            coeffPlaceOfWorshipUnits = 0.0,
            coeffCafeUnits = 0.0,
            coeffFastFoodUnits = 0.0,
            coeffKinderGartenUnits = 0.0,
            coeffTourismUnits = 0.0,
            coeffBuildingUnits = 0.0,
            coeffResidentialUnits = 0.0,
            coeffCommercialUnits = 0.0,
            coeffRetailUnits = 0.0,
            coeffIndustrialUnits = 0.0,
            useLevels = true,
            coeff0 = -1.0,
            coeff1 = -2.0
        )
        testDestinations.forEach { it.recalculateAttractions(listOf(distribution)) }
        val distances = listOf(1500.0, 2000.0)

        val pFirst  = distribution.calcFor(testDestinations[0], distances[0])
        val pSecond = distribution.calcFor(testDestinations[1], distances[1])

        val actual = pFirst / (pFirst + pSecond)

        val distanceKm = distances.map { it / 1000.0 }

        val deterrenceFirst = - 1.0 * distanceKm[0]  - 2.0 * ln(distanceKm[0])
        val expectedWeightFirst  = (200.0 * 2.0 * 3.0 + 1.0) * exp(deterrenceFirst)

        val deterrenceSecond = - 1.0 * distanceKm[1]  - 2.0 * ln(distanceKm[1])
        val expectedWeightSecond = (50.0 * 2.0 + 1.0) * exp(deterrenceSecond)

        val expected = expectedWeightFirst / (expectedWeightFirst + expectedWeightSecond)

        assertEquals(expected, actual)
    }

    @Test
    fun ln3Test() {
        val testDestinations = generateTestDestinations()
        val distribution = Ln3(
            coeffResidentialArea = 2.0,
            coeffCommercialArea = 0.0,
            coeffRetailArea = 0.0,
            coeffIndustrialArea = 0.0,
            coeffOfficeArea = 0.0,
            coeffShopArea = 0.0,
            coeffSchoolArea = 0.0,
            coeffUniversityArea = 0.0,
            coeffOtherArea = 0.0,
            coeffOfficeUnits = 0.0,
            coeffShopUnits = 0.0,
            coeffSchoolUnits = 0.0,
            coeffUniUnits = 0.0,
            coeffPlaceOfWorshipUnits = 0.0,
            coeffCafeUnits = 0.0,
            coeffFastFoodUnits = 0.0,
            coeffKinderGartenUnits = 0.0,
            coeffTourismUnits = 0.0,
            coeffBuildingUnits = 0.0,
            coeffResidentialUnits = 0.0,
            coeffCommercialUnits = 0.0,
            coeffRetailUnits = 0.0,
            coeffIndustrialUnits = 0.0,
            useLevels = true,
            coeff0 = -1.0,
            coeff1 = -2.0,
            coeff2 = -3.0,
        )
        testDestinations.forEach { it.recalculateAttractions(listOf(distribution)) }
        val distances = listOf(1500.0, 2000.0)

        val pFirst = distribution.calcFor(testDestinations[0], distances[0])
        val pSecond = distribution.calcFor(testDestinations[1], distances[1])

        val actual = pFirst / (pFirst + pSecond)

        val distanceKm = distances.map { it / 1000.0 }

        val deterrenceFirst = (
            - 1.0 * ln(distanceKm[0]) * ln(distanceKm[0])
            - 2.0 * ln(distanceKm[0])
            - 3.0 * ln(distanceKm[0]) * ln(distanceKm[0]) * ln(distanceKm[0])
        )
        val expectedWeightFirst = (200.0 * 2.0 * 3.0 + 1.0) * exp(deterrenceFirst)
        val deterrenceSecond = (
            - 1.0 * ln(distanceKm[1]) * ln(distanceKm[1])
            - 2.0 * ln(distanceKm[1])
            - 3.0 * ln(distanceKm[1]) * ln(distanceKm[1]) * ln(distanceKm[1])
        )
        val expectedWeightSecond = (50.0 * 2.0 + 1.0) * exp(deterrenceSecond)

        val expected = expectedWeightFirst / (expectedWeightFirst + expectedWeightSecond)

        assertEquals(expected, actual)
    }
}