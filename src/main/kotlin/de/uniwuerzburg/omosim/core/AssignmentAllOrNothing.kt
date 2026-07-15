package de.uniwuerzburg.omosim.core

import com.graphhopper.GraphHopper
import com.graphhopper.gtfs.PtRouter
import de.uniwuerzburg.omosim.core.Omosim.GTFSComponents
import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.core.models.TripVisitor
import de.uniwuerzburg.omosim.routing.Route

/**
 * Assigns the best route according to GraphHopper
 */
class AssignmentAllOrNothing(
    hopper: GraphHopper,
    gtfsComponents: GTFSComponents? = null
) : Assignment {
    override val tripVisitor: TripVisitor

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
}