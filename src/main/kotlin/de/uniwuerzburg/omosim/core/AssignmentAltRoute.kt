package de.uniwuerzburg.omosim.core

import com.graphhopper.GraphHopper
import de.uniwuerzburg.omosim.calibration.ODTTriple
import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.core.models.RealLocation
import de.uniwuerzburg.omosim.core.models.TripVisitor
import de.uniwuerzburg.omosim.routing.routeCarAlternatives
import de.uniwuerzburg.omosim.utils.createCumDist
import de.uniwuerzburg.omosim.utils.sampleCumDist
import java.util.Random
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.toDoubleArray
import kotlin.math.floor

/**
 * Assigns alternative routes to car trips based on calibration.
 */
class AssignmentAltRoute(hopper: GraphHopper, altPercentages:  Map<ODTTriple, List<Double>>) : Assignment {
    override val tripVisitor: TripVisitor

    init {
        val T = altPercentages.maxOf { (k, _) -> k.t } + 1

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
            if ((trip.mode == Mode.CAR_DRIVER) && (odt in altPercentages)){
                // Find alternatives
                val response = routeCarAlternatives(origin, destination, hopper)

                // Choose route according to calibration
                if (!response.hasErrors()) {
                    val probs = altPercentages[odt]!!
                    val distr = createCumDist(probs.toDoubleArray())
                    val path = response.all[sampleCumDist(distr, rng!!)]

                    trip.lats = path.points.map { it.lat }
                    trip.lons = path.points.map { it.lon }
                }
            }
        }
    }
}