package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.core.models.ModeChoiceOption
import de.uniwuerzburg.omosim.core.models.Weekday
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class OmosimTest {
    val areaFile = File(Omosim::class.java.classLoader.getResource("smallTown/boundary.geojson")!!.file)
    val osmFile  = File(Omosim::class.java.classLoader.getResource("smallTown/starnberg.osm.pbf")!!.file)
    val omosim   = Omosim(areaFile, osmFile, cache = false)

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
        val agents = omosim.run(10, verbose = false)
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