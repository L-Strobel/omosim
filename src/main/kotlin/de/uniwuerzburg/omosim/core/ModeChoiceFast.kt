package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.core.models.*
import de.uniwuerzburg.omosim.io.ParameterReader
import de.uniwuerzburg.omosim.routing.Route
import de.uniwuerzburg.omosim.routing.RoutingCache
import de.uniwuerzburg.omosim.routing.calcDistanceBeeline
import de.uniwuerzburg.omosim.utils.ProgressBar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource

/**
 * Do mode choice with the mode options: FOOT, BICYCLE, CAR_DRIVER, CAR_PASSENGER, PUBLIC_TRANSIT
 * Uses a multinomial logit model at its core.
 *
 * The difference to ModeChoiceGTFS is that this does not use the travel time of each mode and takes the approximated
 * car distance from the routing cache. Making it significantly faster.
 *
 * @param routingCache GraphHopper for routing
 */
class ModeChoiceFast(
    private val routingCache: RoutingCache,
    parameterReader: ParameterReader
) : ModeChoice {
    val tourModeOptions = parameterReader.getTourModeUtilities()
    val tripModeOptions = parameterReader.getTripModeUtilities()

    /**
     * Determine the mode of each trip and calculate the distance and time.
     *
     * @param agents Agents with trips (usually the trips have an UNDEFINED mode at this point)
     * @param mainRng Random number generator of the main thread
     * @param dispatcher Coroutine dispatcher used for concurrency
     * @param verbose Print progressbar etc.. Doesn't affect logging.
     * @return agents. Now their trips have specified modes.
     */
    override fun doModeChoice(
        agents: List<MobiAgent>, mainRng: Random, dispatcher: CoroutineDispatcher, modeSpeedUp: Map<Mode, Double>, verbose: Boolean
    ) : List<MobiAgent> {
        val timeSource = TimeSource.Monotonic
        val timestampStartInit = timeSource.markNow()
        val progressBar = ProgressBar("Mode Choice", agents.size, enabled = verbose)

        for (chunk in agents.chunked(AppConstants.nAllowedCoroutines)) { // Don't launch to many coroutines at once
            runBlocking(dispatcher) {
                for (agent in chunk) {
                    val coroutineRng = Random(mainRng.nextLong())
                    launch(dispatcher) {
                        doModeChoiceFor(agent, coroutineRng)
                        progressBar.singleTaskComplete()
                    }
                }
            }
        }
        progressBar.done()
        logger.get()?.info("Mode Choice took: ${timeSource.markNow() - timestampStartInit}")
        return agents
    }

    /**
     * Run through day and get all HOME-HOME tours. If the day does not start with a HOME activity,
     * all the trips before the first HOME activity are counted as a tour. The same is true for all the trips after the
     * last HOME activity if the day does not end with a HOME activity.
     *
     * @param diary Mobility pattern on a given day
     * @param rng Random number generator
     * @return Tours
     */
    fun getTours(diary: Diary, rng: Random) : List<ModeChoiceGTFS.TourMCFeatures> {
        val tours = mutableListOf<ModeChoiceGTFS.TourMCFeatures>()
        var currentTour = mutableListOf<ModeChoiceGTFS.TripMCFeatures>()

        val visitor = {
            trip: Trip, originActivity: Activity, destinationActivity: Activity,
            departureTime: LocalTime, wd: Weekday, finished: Boolean, _: Random? ->

            // Routes for possible trips
            val rtDistances = mapOf(
                ActivityType.HOME to Route.sampleDistanceRoundTrip(ActivityType.HOME, rng),
                ActivityType.WORK to Route.sampleDistanceRoundTrip(ActivityType.WORK, rng),
                ActivityType.SCHOOL to Route.sampleDistanceRoundTrip(ActivityType.SCHOOL, rng)
            )

            // Get car distance and routes per mode
            val carDistance = if (
                (originActivity.type == destinationActivity.type) &&
                (
                    (originActivity.type == ActivityType.HOME) ||
                    (originActivity.type == ActivityType.WORK) ||
                    (originActivity.type == ActivityType.SCHOOL)
                )
            ) {
                // IF trip is from fixed location to same fixed location. Impute a randomly sampled Round-trip.
                rtDistances[originActivity.type]!!
            } else {
                val origin = originActivity.location
                val destination = destinationActivity.location
                val originCell = origin.getAggLoc()!!
                val destinationCell = destination.getAggLoc()!!

                if (originCell == destinationCell) {
                    calcDistanceBeeline(origin, destination) / 1000
                } else {
                    routingCache.getDistances(
                        originCell, listOf(destinationCell)
                    ).first().toDouble() / 1000
                }
            }

            val tripFeatures = ModeChoiceGTFS.TripMCFeatures(
                carDistance,
                originActivity,
                destinationActivity,
                wd,
                departureTime
            )
            currentTour.add(tripFeatures)

            // Tour ends
            if ((destinationActivity.type == ActivityType.HOME) || finished) {
                tours.add( ModeChoiceGTFS.TourMCFeatures(currentTour) )
                currentTour = mutableListOf()
            }

            // Add estimated travel time
            trip.time = null
        }
        diary.visitTrips(visitor) // Run through day
        return tours
    }

    /**
     * Do Mode choice for a single Agent.
     * The result is directly stored in the agents diaries.
     *
     * @param agent Agent
     * @param rng Random number generator
     */
    private fun doModeChoiceFor(agent: MobiAgent, rng: Random) {
        for (diary in agent.mobilityDemand) {
            val tours = getTours(diary, rng)

            // Tour mode choice
            for (tour in tours) {
                if (!tour.isHomeHome()) { continue } // Only do HOME-HOME tours as one block

                // Sample tour mode
                val mode = ModeChoiceGTFS.sampleUtilities(
                    tourModeOptions,
                    null,
                    tour.totalCarDistance(),
                    agent,
                    tour.mainPurpose(),
                    tour.weekday(),
                    rng
                )

                // If the tour is a CAR or BICYCLE all trips on the tour must be conducted with the respective vehicle
                if ((mode == Mode.CAR_DRIVER) || (mode == Mode.BICYCLE)) {
                    for (trip in tour.trips) {
                        trip.mode = mode
                    }
                }
            }

            // Trip mode choice
            for (trip in tours.flatMap { it.trips }.filter { it.mode == null }) {
                val mode = ModeChoiceGTFS.sampleUtilities(
                    tripModeOptions, null, trip.carDistance, agent, trip.toActivity.type, trip.weekday, rng
                )
                trip.mode = mode
            }

            // Format for output
            val outTrips = mutableListOf<Trip>()
            for (trip in tours.flatMap { it.trips }) {
                outTrips.add(
                    Trip(
                        trip.carDistance,
                        null,
                        mode = trip.mode!!,
                        lats = null,
                        lons = null
                    )
                )
            }
            diary.trips = outTrips
        }
    }
}