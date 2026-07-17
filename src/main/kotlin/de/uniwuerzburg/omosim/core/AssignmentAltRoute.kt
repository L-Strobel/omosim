package de.uniwuerzburg.omosim.core

import com.graphhopper.GraphHopper
import de.uniwuerzburg.omosim.calibration.ODTTriple
import de.uniwuerzburg.omosim.calibration.RouteChoiceCalibrationStore
import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.core.models.RealLocation
import de.uniwuerzburg.omosim.core.models.TripVisitor
import de.uniwuerzburg.omosim.routing.routeCarAlternatives
import de.uniwuerzburg.omosim.utils.createCumDist
import de.uniwuerzburg.omosim.utils.runParallel
import de.uniwuerzburg.omosim.utils.sampleCumDist
import kotlinx.coroutines.CoroutineDispatcher
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.toDoubleArray
import kotlin.math.floor

/**
 * Assigns alternative routes to car trips based on calibration.
 */
class AssignmentAltRoute(hopper: GraphHopper, calibration: RouteChoiceCalibrationStore) : Assignment {
    val tripVisitor: TripVisitor
    var hits = AtomicInteger(0)
    var misses = AtomicInteger(0)

    init {
        val T = calibration.altPercentages.maxOf { (k, _) -> k.t } + 1

        // Get alternatives and choose according to calibration
        tripVisitor = { trip, originActivity, destinationActivity, departureTime, _, _, rng ->
            // Calibration uses aggregated locations
            val origin = originActivity.location.getAggLoc()!! as RealLocation
            val destination = destinationActivity.location.getAggLoc()!! as RealLocation

            // Determine origin-destination-time triple
            val mod = departureTime.minute + departureTime.hour * 60
            val t = floor((mod % 1440.0) / 1440.0 * T).toInt()
            val od = Pair(origin, destination)
            val odt = ODTTriple(od.first, od.second, t)

            // For all car trips
            if (trip.mode == Mode.CAR_DRIVER) {
                if (odt in calibration.altPercentages) {
                    hits.incrementAndGet()

                    // Find alternatives
                    val response = routeCarAlternatives(
                        origin,
                        destination,
                        hopper,
                        calibration.altMaxRoutes,
                        calibration.altMaxSlower,
                        calibration.altMaxSimilarity,
                    )

                    // Choose route according to calibration
                    if (!response.hasErrors()) {
                        val probs = calibration.altPercentages[odt]!!
                        val distr = createCumDist(probs.toDoubleArray())
                        val path = response.all[sampleCumDist(distr, rng!!)]

                        trip.lats = path.points.map { it.lat }
                        trip.lons = path.points.map { it.lon }
                    }
                } else {
                    misses.incrementAndGet()
                }
            }
        }
    }

    /**
     * Assign routes to agent trips. Unnecessary if the routes have already been determined during mode choice.
     */
    override fun assign(
        agents: List<MobiAgent>,
        verbose: Boolean,
        dispatcher: CoroutineDispatcher,
        rng: Random,
    ) : List<MobiAgent> {
        dispatcher.runParallel(
            agents,
            rng,
            progressBar = verbose,
            processName = "Assigning routes (Calibrated Alternatives)",
            logger = logger.get()
        ) { agent, seed ->
            val taskRng = Random(seed)
            for (diary in agent.mobilityDemand) {
                diary.visitTrips(tripVisitor, taskRng)
            }
        }
        val shareHit = hits.get() / (hits.get() + misses.get()).toDouble() * 100
        logger.get()?.info(
            "Assigning routes (Calibrated Alternatives). Calibrated %.2f %% of car trips".format(shareHit)
        )
        return agents
    }
}