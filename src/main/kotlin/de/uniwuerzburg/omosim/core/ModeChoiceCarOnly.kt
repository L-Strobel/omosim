package de.uniwuerzburg.omosim.core

import com.graphhopper.GraphHopper
import de.uniwuerzburg.omosim.cli.main
import de.uniwuerzburg.omosim.core.models.*
import de.uniwuerzburg.omosim.routing.Route
import de.uniwuerzburg.omosim.utils.ProgressBar
import de.uniwuerzburg.omosim.utils.runParallel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource

/**
 * Turn every trip into a char trip.
 *
 * @param hopper GraphHopper for routing
 * @param withPath Return the lat-lon coordinates of the car trips.
 */
class ModeChoiceCarOnly(
    private val hopper: GraphHopper, private val withPath: Boolean
): ModeChoice {
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
        dispatcher.runParallel(
            agents,
            mainRng,
            progressBar = verbose,
            processName = "Mode Choice (Car Only)",
            logger = logger.get()
        ) { agent, seed ->
            val taskRng = Random(seed)
            for (diary in agent.mobilityDemand) {
                tripsToCar(diary, taskRng, modeSpeedUp)
            }
        }
        return agents
    }

    /**
     * Set all trips in a diary to car trips and route them,
     *
     * @param diary Mobility pattern on a day
     * @param rng Random number generator used in the thread.
     */
    private fun tripsToCar(diary: Diary, rng: Random, modeSpeedUp: Map<Mode, Double>) {
        val visitor = {
                trip: Trip, originActivity: Activity, destinationActivity: Activity,
                _: LocalTime, _: Weekday, _: Boolean, _: Random? ->
            val route = if (
                    (originActivity.type == destinationActivity.type) &&
                    (
                        (originActivity.type == ActivityType.HOME) ||
                        (originActivity.type == ActivityType.WORK) ||
                        (originActivity.type == ActivityType.SCHOOL)
                    )
                ) {
                // IF trip is from fixed location to same fixed location. Impute a randomly sampled Round-trip.
                val rtDistance = Route.sampleDistanceRoundTrip(originActivity.type, rng)
                Route.getRoundTripRoute(Mode.CAR_DRIVER, rtDistance)
            } else {
                Route.getWithFallback(
                    Mode.CAR_DRIVER, originActivity.location, destinationActivity.location,
                    hopper, withPath, null, null
                )
            }

            trip.mode = Mode.CAR_DRIVER
            trip.time = route.time / modeSpeedUp.getOrDefault(Mode.CAR_DRIVER, 1.0)
            trip.distance = route.distance
            trip.lats = route.lats
            trip.lons = route.lons
        }

        diary.visitTrips(visitor)
    }
}