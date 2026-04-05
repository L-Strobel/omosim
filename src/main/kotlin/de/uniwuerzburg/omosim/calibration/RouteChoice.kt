package de.uniwuerzburg.omosim.calibration

import com.gurobi.gurobi.*
import de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
import de.uniwuerzburg.omosim.calibration.differentiablemodel.*
import de.uniwuerzburg.omosim.core.models.*
import java.time.LocalTime

/**
 * Origin-destination pair at a given time t.
 * Used to define the route choice probability distributions for different Trips possibilities.
 *
 * @param origin Start location
 * @param destination End location
 * @param t Time slice
 */
data class ODTTriple(
    val origin: LocationOption,
    val destination: LocationOption,
    val t: Int
)

/**
 * Calibrate OMoSim output by adjusting route choice.
 * Each agent will choose a route between A and B from the paths found by GraphHopper when ALT_ROUTE is set.
 * The result are multiple probability vector p - one for each o-d pair - that give the probability that each path
 * is selected by an agent.
 * By default, the agents will always choose the first result p = (1.0, 0.0, 0.0, ...).
 *
 * @param context Calibration context to use. Includes a Simulator (OMoSim) and the traffic count data.
 */
class RouteChoice(
    private val context: TrafficCountCalibrationContext
) {
    /**
     * Calibrate route choice.
     *
     * @param gurobi Use Gurobi solver. Must be installed on the system and findable by the gurobi java API.
     */
    fun calibrate(algorithm: CalibrationAlgorithm?, parameters: Map<String, String>, gurobi: Boolean = false) {
        context.omosim.mainRng.setSeed(0) // Seed impact low with 100% of agents

        // Run Simulation
        val agents = context.omosim.run(0.1, verbose = false)
        context.omosim.doModeChoice(agents, ModeChoiceOption.FAST, withPath = false, verbose = false)

        val odtCounts = getODTCounts(agents)

        context.omosim.altPercentages = if (gurobi) {
           optimize(odtCounts)
        } else {
            val model = buildModel(odtCounts) // Create route choice model
            val x0 = buildX0(odtCounts)

            // Set bounds to [0, 1] independent of user specification
            val parametersCorrected = parameters.toMutableMap()
            val lb = parameters["lb"]?.toDoubleOrNull()
            if ((parameters["lb"] != null) and (lb != 0.0)) {
                logger.warn("RouteChoice: Lower bound must be 0.0 and was ${parameters["lb"]}. Setting lb it to 0.0")
            }
            parametersCorrected["lb"] = "0.0"

            val ub = parameters["ub"]?.toDoubleOrNull()
            if ((parameters["ub"] != null) and (ub != 1.0)) {
                logger.warn("RouteChoice: Upper bound must be 1.0 and was ${parameters["ub"]}. Setting ub it to 1.0")
            }
            parametersCorrected["ub"] = "1.0"

            // Optimize
            val x = model.optimizeWith(algorithm, parametersCorrected, x0, nWorker = context.omosim.nWorker)
            unpackX(x, odtCounts)
        }
    }

    /**
     * Find the optimal p with Gurobi.
     *
     * @param odtCounts occurrence count of specific origin-destination-time triples
     * @return Optimal p for each trip possibility
     */
    private fun optimize(
        odtCounts: Map<ODTTriple, Double>,
    ) : Map<ODTTriple, List<Double>> {
        logger.info("Starting route choice calibration with gurobi.")

        try {
            // Setup
            val env = GRBEnv()
            val model = GRBModel(env)

            // Initialize simulated traffic counts
            val simCount = mutableMapOf<TrafficSensor, List<GRBLinExpr>>()
            for (sensor in context.sensors) {
                simCount[sensor] = List(T) { GRBLinExpr() }
            }

            // Add contribution of each odt to simulated counts
            val result = mutableMapOf<ODTTriple, MutableList<GRBVar>>()
            for ((od, alternatives) in context.affectedAltSensors.entries) {
                for (t in 0 until T) {
                    val odt = ODTTriple(od.first, od.second, t)
                    if (odt in odtCounts) {
                        val count = odtCounts[odt]!!
                        val pSum = GRBLinExpr()
                        for (alternative in alternatives) {
                            // Probability of choosing that alternative
                            val pA = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "P")

                            if (odt in result) {
                                result[odt]!!.add(pA)
                            } else {
                                result[odt] = mutableListOf(pA)
                            }

                            // Add to simulated count
                            for (sensor in alternative) {
                                simCount[sensor]!![t].addTerm(count, pA)
                            }

                            // For PCondition
                            pSum.addTerm(1.0, pA)
                        }
                        // Ensure that p is a proper probability distribution
                        model.addConstr(pSum, GRB.EQUAL, 1.0, "PCondition")
                    }
                }
            }

            // Objective
            val obj = grbSseObjective(model, context.sensors, simCount)
            model.setObjective(obj, GRB.MINIMIZE)

            // Solve
            model.optimize()

            val success = handleGrbStatus(model)
            if (success) {
                val oval = model[GRB.DoubleAttr.ObjVal]
                logger.info("Optimization (gurobi) finished with optimal objective: $oval")
                val rVal = result.mapValues { (_, v) -> v.map { it.get(GRB.DoubleAttr.X) } }
                model.dispose()
                env.dispose()
                return rVal
            }
            model.dispose()
            env.dispose()
        } catch (e: GRBException) {
            logger.error("Gurobi Error! Error code: ${e.errorCode}. ${e.message}")
        }
        return mapOf()
    }

    /**
     * Build a differentiable model that computes the sum-of-square loss for a given p.
     *
     * @param odtCounts occurrence count of specific origin-destination-time triples
     * @return Differentiable Model
     */
    private fun buildModel(
        odtCounts: Map<ODTTriple, Double>
    ) : DifferentiableModelUV {
        // Setup. Initialize differentiable model
        var nVar = 0
        for ((od, alternatives) in context.affectedAltSensors.entries) {
            for (t in 0 until T) {
                val odt = ODTTriple(od.first, od.second, t)
                if (odt in odtCounts) {
                    nVar += alternatives.size
                }
            }
        }
        val model = DifferentiableModelUVBase(nVar)

        // Initialize simulated traffic counts
        val simCount = mutableMapOf<TrafficSensor, List<LinearTerm>>()
        for (sensor in context.sensors) {
            simCount[sensor] = List(T) { LinearTerm(model.nVars) }
        }

        // Add contribution of each odt to simulated counts
        var iVar = 0 // Keep track of which variable represents the current route choice decision.
        for ((od, alternatives) in context.affectedAltSensors.entries) {
            for (t in 0 until T) {
                val odt = ODTTriple(od.first, od.second, t)
                if (odt in odtCounts) {
                    val count = odtCounts[odt]!!
                    val pAs = mutableListOf<Term>()
                    for (alternative in alternatives) {
                        // Probability of choosing that alternative
                        val exTripsAlternative = Variable(model.nVars, iVar, 1.0)
                        iVar += 1
                        pAs.add(exTripsAlternative)
                    }

                    val pSum = LinearTerm(model.nVars)
                    for (pA in pAs) {
                        pSum.addTerm(pA, 1.0)
                    }

                    for ((i, alternative) in alternatives.withIndex()) {
                        // Ensure that p is a proper probability distribution
                        val pTerm = DivisionTerm(model.nVars, pAs[i], pSum)

                        // Add to simulated count
                        for (sensor in alternative) {
                            simCount[sensor]!![t].addTerm(pTerm, count)
                        }
                    }
                }
            }
        }

        // Objective
        val obj = sseObjective(model.nVars, context.sensors, simCount)
        model.setRootTerm(obj)
        return model
    }

    /**
     * Create x0 for route choice.
     *
     * @param odtCounts occurrence count of specific origin-destination-time triples
     * @return x0
     */
    private fun buildX0(odtCounts: Map<ODTTriple, Double>) : DoubleArray {
        val x0 = mutableListOf<Double>()
        for ((od, alternatives) in context.affectedAltSensors.entries) {
            for (t in 0 until T) {
                val odt = ODTTriple(od.first, od.second, t)
                if (odt in odtCounts) {
                    val p0 = MutableList(alternatives.size) { 0.0 }
                    if (p0.size > 0) {
                        p0[0] = 1.0
                    }
                    x0.addAll(p0)
                }
            }
        }
        return x0.toDoubleArray()
    }

    /**
     * Unpack result of optimization to fit route choice format.
     *
     * @param odtCounts occurrence count of specific origin-destination-time triples
     * @return Unpacked route choice parameters
     */
    private fun unpackX(x: DoubleArray, odtCounts: Map<ODTTriple, Double>) : Map<ODTTriple, List<Double>> {
        val unpacked = mutableMapOf<ODTTriple, List<Double>>()
        var i = 0
        for ((od, alternatives) in context.affectedAltSensors.entries) {
            for (t in 0 until T) {
                val odt = ODTTriple(od.first, od.second, t)
                if (odt in odtCounts) {
                    val p = mutableListOf<Double>()
                    for (j in alternatives.indices) {
                        p.add(x[i])
                        i += 1
                    }
                    unpacked[odt] = p
                }
            }
        }
        return unpacked
    }

    /**
     * Determine how often a specific origin-destination-time triple occurs.
     * @param agents OMoSim run result
     * @return Occurrence
     */
    private fun getODTCounts(
        agents: List<MobiAgent>
    ) : Map<ODTTriple, Double> {
        val n = mutableMapOf<ODTTriple, Double>()
        for (agent in agents) {
            val demand = agent.mobilityDemand.first() // Get demand for first day

            // Get trip start times
            val startTimes = mutableListOf<LocalTime>()
            val origins = mutableListOf<Activity>()
            val destinations = mutableListOf<Activity>()
            val visitor: TripVisitor = { _: Trip, origin: Activity, destination: Activity,
                                         departureTime: LocalTime, _: Weekday, _: Boolean ->
                startTimes.add(departureTime)
                origins.add(origin)
                destinations.add(destination)
            }
            demand.visitTrips(visitor)

            // Count car trips
            for (i in 0 until demand.trips.size) {
                val trip = demand.trips[i]
                val startTime = startTimes[i]
                val origin = origins[i].location.getAggLoc()!!
                val destination = destinations[i].location.getAggLoc()!!

                if (trip.mode != Mode.CAR_DRIVER) {
                    continue
                }

                val t =  startTime.determineTimeSlice()
                val odt = ODTTriple(origin, destination, t)
                n[odt] = (n[odt] ?: 0.0) + context.totalPopulation / agents.size
            }
        }
        return n
    }
}

