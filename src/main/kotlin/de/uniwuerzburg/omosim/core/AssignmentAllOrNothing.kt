package de.uniwuerzburg.omosim.core

import com.graphhopper.GraphHopper
import com.graphhopper.gtfs.PtRouter
import de.uniwuerzburg.omosim.core.Omosim.GTFSComponents
import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.core.models.Trip
import de.uniwuerzburg.omosim.core.models.TripVisitor
import de.uniwuerzburg.omosim.routing.Route
import de.uniwuerzburg.omosim.utils.runParallel
import kotlinx.coroutines.CoroutineDispatcher
import java.util.Random

/**
 * Assigns the best route according to GraphHopper
 */
class AssignmentAllOrNothing(
    val hopper: GraphHopper,
    val gtfsComponents: GTFSComponents? = null
) : Assignment {
    val ignorePT: Boolean = gtfsComponents == null // Skip public transit if no gtfs data is supplied
    val ptRouter: PtRouter? = gtfsComponents?.ptRouter

    /**
     * Assign routes to agent trips. Unnecessary if the routes have already been determined during mode choice.
     */
    override fun assign(
        agents: List<MobiAgent>,
        verbose: Boolean,
        dispatcher: CoroutineDispatcher,
        rng: Random,
        modeSpeedUp: Map<Mode, Double>
    ) : List<MobiAgent> {
        val tripVisitor: TripVisitor = { trip, originActivity, destinationActivity, departureTime, wd, finished, rng ->
            val origin = originActivity.location
            val destination = destinationActivity.location

            if (trip.mode != Mode.PUBLIC_TRANSIT) {
                val route = Route.getWithFallback(
                    trip.mode, origin, destination,
                    hopper, true, null, null
                )

                trip.updateWith(route, modeSpeedUp)
            } else if (!ignorePT) {
                val departureInstant = gtfsComponents!!.ptSimDays[wd]!!
                    .atTime(departureTime)
                    .atZone(gtfsComponents.timeZone.toZoneId())
                    .toInstant()

                val route = Route.getWithFallback(
                    trip.mode, origin, destination,
                    hopper, true, departureInstant, ptRouter
                )

                trip.updateWith(route, modeSpeedUp)
            }
        }

        dispatcher.runParallel(
            agents,
            rng,
            progressBar = verbose,
            processName = "Assigning routes (All-or-Nothing)",
            logger = logger.get()
        ) { agent, seed ->
            val taskRng = Random(seed)
            for (diary in agent.mobilityDemand) {
                diary.visitTrips(tripVisitor, taskRng)
            }
        }
        return agents
    }
}