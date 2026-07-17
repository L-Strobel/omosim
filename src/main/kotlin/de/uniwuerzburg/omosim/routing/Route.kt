package de.uniwuerzburg.omosim.routing

import com.graphhopper.GHResponse
import com.graphhopper.GraphHopper
import com.graphhopper.gtfs.PtRouter
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.LocationOption
import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.core.models.RealLocation
import de.uniwuerzburg.omosim.io.json.OutputPTLeg
import java.time.Instant
import java.util.*
import kotlin.math.ln

class Route (
    val distance: Double,           // Unit: kilometer
    var time: Double,               // Unit: minutes
    val lats: List<Double>?,
    val lons: List<Double>?,
    val ptLegs: List<OutputPTLeg>? = null,
    val onlyWalk: Boolean = false
) {
    companion object {
        fun getWithFallback(
            mode: Mode, origin: LocationOption, destination: LocationOption, hopper: GraphHopper, withPath: Boolean,
            departureTime: Instant?, ptRouter: PtRouter?
        ): Route {
            if (((departureTime == null) || (ptRouter == null)) && (mode == Mode.PUBLIC_TRANSIT)) {
                throw IllegalArgumentException("A ptRouter and departureTime is required for public transit routing!")
            }

            val route = if ((origin !is RealLocation) || (destination !is RealLocation)) {
                routeFallback(mode, origin, destination)
            } else {
                val response = when (mode) {
                    Mode.PUBLIC_TRANSIT -> { routeGTFS(origin, destination, departureTime!!, ptRouter!!, hopper) }
                    Mode.FOOT           -> routeWith("foot", origin, destination, hopper)
                    Mode.BICYCLE        -> routeWith("bike", origin, destination, hopper)
                    else                -> routeWith("car", origin, destination, hopper)
                }
                if (response.hasErrors()) {
                    routeFallback(mode, origin, destination)
                } else {
                    fromGHResponse(mode, response, withPath)
                }
            }
            return addConstantTimeCost(mode, route)
        }

        private fun fromGHResponse(mode: Mode, response: GHResponse, withPath: Boolean) : Route {
            val best = response.best
            val legs = best.legs

            // Check if only walking occurred
            val onlyWalk = legs.all { it.type == "walk" }

            // Route
            var ptLegs: List<OutputPTLeg>? = null
            var lats: List<Double>? = null
            var lons: List<Double>? = null
            if (withPath) {
                lats = best.points.map { it.lat }
                lons = best.points.map { it.lon }

                if ((mode == Mode.PUBLIC_TRANSIT) and !onlyWalk and legs.isNotEmpty()) {
                    ptLegs = mutableListOf()
                    for (leg in legs) {
                        val legMode = when (leg.type) {
                            "walk" -> Mode.FOOT
                            "pt" -> Mode.PUBLIC_TRANSIT
                            else -> {
                                logger.debug(
                                    "Found unexpected pt leg mode ${leg.type}. Setting to ${Mode.PUBLIC_TRANSIT.name}."
                                )
                                Mode.PUBLIC_TRANSIT
                            }
                        }
                        val time = ((leg.arrivalTime.time - leg.departureTime.time) / 1000 / 60).toDouble()
                        val dStop = if (legMode == Mode.PUBLIC_TRANSIT) leg.departureLocation else null

                        // GTFS routing currently always returns 0.0 for distance
                        val distance = if ((leg.distance == 0.0) and (time > 0.0)) null else leg.distance / 1000

                        ptLegs.add(
                            OutputPTLeg(
                                mode = legMode,
                                timeMinute = time,
                                distanceKilometer = distance,
                                departureStop = dStop
                            )
                        )
                    }
                }
            }

            return Route (
                best.distance / 1000,
                (best.time / 1000 / 60).toDouble(),
                lats,
                lons,
                ptLegs,
                onlyWalk
            )
        }

        private fun routeFallback(mode: Mode, origin: LocationOption, destination: LocationOption): Route {
            val beelineDistance = calcDistanceBeeline(origin, destination) / 1000
            return routeFallbackFromDistance(mode, beelineDistance)
        }

        /**
         * @param mode Travel mode
         * @param distance Distance of trip. Unit: Kilometer
         */
        private fun routeFallbackFromDistance(mode: Mode, distance: Double): Route {
            val time = when (mode) {
                Mode.PUBLIC_TRANSIT -> distance / 22.5 * 60 // 22.5 km/h
                Mode.FOOT -> distance / 5 * 60 // 5 km/h
                Mode.BICYCLE -> distance / 18 * 60 // 18 km/h
                else -> distance / 75  * 60 // 75 km/h + 5 min for Parking
            }
            return Route(distance, time,null, null)
        }

        fun sampleDistanceRoundTrip(activityType: ActivityType, rng: Random): Double {
            val lambda: Double = when (activityType) {
                ActivityType.HOME   -> 1.0 / 11.9
                ActivityType.WORK   -> 1.0 / 12.7
                ActivityType.SCHOOL -> 1.0 / 7.7
                else -> throw IllegalArgumentException(
                    "Round-trips must start and end at a fixed location (HOME, WORK, SCHOOL)."
                )
            }
            return 1 / (-lambda) * ln(1-rng.nextDouble())
        }

        fun getRoundTripRoute(mode: Mode, distance: Double): Route  {
            val route = routeFallbackFromDistance(mode, distance)
            return addConstantTimeCost(mode, route)
        }

        private fun addConstantTimeCost(mode: Mode, route: Route) : Route {
            return when (mode) {
                Mode.CAR_DRIVER    -> addTimeAndCopy(5, route) // 5min for parking
                Mode.CAR_PASSENGER -> addTimeAndCopy(5, route) // 5min for parking
                Mode.BICYCLE       -> addTimeAndCopy(1, route) // 1min locking/unlocking and parking
                else               -> route
            }
        }

        private fun addTimeAndCopy(time: Int, route: Route) : Route {
            return Route(route.distance, route.time + time, route.lats, route.lons)
        }
    }
}