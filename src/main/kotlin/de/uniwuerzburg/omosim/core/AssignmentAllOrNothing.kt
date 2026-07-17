package de.uniwuerzburg.omosim.core

import com.graphhopper.GraphHopper
import com.graphhopper.gtfs.PtRouter
import de.uniwuerzburg.omosim.core.Omosim.GTFSComponents
import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.core.models.TripVisitor
import de.uniwuerzburg.omosim.routing.Route
import de.uniwuerzburg.omosim.utils.runParallel
import kotlinx.coroutines.CoroutineDispatcher
import java.util.Random

/**
 * Assigns the best route according to GraphHopper
 */
class AssignmentAllOrNothing(
    hopper: GraphHopper,
    gtfsComponents: GTFSComponents? = null
) : Assignment {
    val tripVisitor: TripVisitor

    init {
        val ignorePT = gtfsComponents == null // Skip public transit if no gtfs data is supplied
        var ptRouter: PtRouter? = gtfsComponents?.ptRouter

        tripVisitor = { trip, originActivity, destinationActivity, departureTime, wd, finished, rng ->
            val origin = originActivity.location
            val destination = destinationActivity.location

            if (trip.mode != Mode.PUBLIC_TRANSIT) {
                val route = Route.getWithFallback(
                    trip.mode, origin, destination,
                    hopper, true, null, null
                )
                trip.lats = route.lats
                trip.lons = route.lons
            } else if (!ignorePT) {
                val departureInstant = gtfsComponents.ptSimDays[wd]!!
                    .atTime(departureTime)
                    .atZone(gtfsComponents.timeZone.toZoneId())
                    .toInstant()

                val route = Route.getWithFallback(
                    trip.mode, origin, destination,
                    hopper, true, departureInstant, ptRouter
                )
                trip.lats = route.lats
                trip.lons = route.lons
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
            progressBar = true,
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