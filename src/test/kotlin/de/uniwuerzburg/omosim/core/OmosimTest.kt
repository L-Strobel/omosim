package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.core.models.*
import de.uniwuerzburg.omosim.routing.calcDistanceBeeline
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class OmosimTest {
    val areaFile = File(Omosim::class.java.classLoader.getResource("smallTown/boundary.geojson")!!.file)
    val osmFile  = File(Omosim::class.java.classLoader.getResource("smallTown/starnberg.osm.pbf")!!.file)
    val gtfsFile = File(Omosim::class.java.classLoader.getResource("smallTown/starnberg_gtfs_clipped.zip")!!.file)
    lateinit var omosim: Omosim

    @BeforeEach
    fun setup(@TempDir tempDir: Path) {
        omosim = Omosim(
            areaFile,
            osmFile,
            gtfsFile = gtfsFile,
            cacheDir = tempDir
        )
    }

    @AfterEach
    fun teardown() {
        System.gc() // Workaround: Removes remaining file handles of GraphHopperGTFS
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

    private fun replaceAgentLocationsWithClosePair(agents: List<MobiAgent>) {
        // Get 2 very close buildings
        val buildingA = omosim.buildings.first()
        val buildingB = omosim.buildings
            .drop(1) // Drop buildingA
            .filter { calcDistanceBeeline(it, buildingA) > 10 } // At least 10m
            .minBy { calcDistanceBeeline(it, buildingA) } // Otherwise closest

        // Check whether a suitable building pair exist
        assertTrue { calcDistanceBeeline(buildingA, buildingB) < 500 }

        // Replace all locations with the pair
        for (agent in agents) {
            val newDiaries = mutableListOf<Diary>()
            for (diary in agent.mobilityDemand) {
                val newActivities = mutableListOf<Activity>()

                for (activity in diary.activities) {
                    val newLocation = if (activity.location == agent.home) {
                        buildingA
                    } else {
                        buildingB
                    }

                    newActivities.add(
                        Activity(activity.type, activity.stayTime, newLocation)
                    )
                }

                newDiaries.add(
                    Diary(diary.day, diary.dayType, newActivities)
                )
            }

            // Replace
            agent.mobilityDemand.clear()
            agent.mobilityDemand.addAll(newDiaries)
        }
    }

    @Test
    fun doModeChoiceTestGTFS() {
        val agents = omosim.run(100, verbose = false)
        replaceAgentLocationsWithClosePair(agents)
        omosim.doModeChoice(agents, ModeChoiceOption.GTFS, withPath = false, verbose = false)

        // Determine share of active mode trips
        val trips = agents
            .flatMap { agent ->
                agent.mobilityDemand.flatMap { diary ->
                    diary.trips.map { trip ->
                        trip.mode
                    }
                }
            }
        val nTrips = trips.size.toDouble()
        val nActive = trips.count{ (it == Mode.FOOT) or (it == Mode.BICYCLE) }.toDouble()
        val share = nActive/nTrips

        // Almost all should be on foot or bike
        assertTrue( share > 0.90)
    }

    @Test
    fun doModeChoiceTestFAST() {
        val agents = omosim.run(100, verbose = false)
        replaceAgentLocationsWithClosePair(agents)
        omosim.doModeChoice(agents, ModeChoiceOption.FAST, withPath = false, verbose = false)

        // Determine share of active mode trips
        val trips = agents
            .flatMap { agent ->
                agent.mobilityDemand.flatMap { diary ->
                    diary.trips.map { trip ->
                        trip.mode
                    }
                }
            }
        val nTrips = trips.size.toDouble()
        val nActive = trips.count{ (it == Mode.FOOT) or (it == Mode.BICYCLE) }.toDouble()
        val share = nActive/nTrips

        // Almost all should be on foot or bike
        assertTrue( share > 0.90)
    }
}