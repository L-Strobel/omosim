package de.uniwuerzburg.omosim.calibration

import com.gurobi.gurobi.*
import de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelUV
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.*
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import de.uniwuerzburg.omosim.calibration.objective.SMOriginDestination
import de.uniwuerzburg.omosim.calibration.objective.sseObjective
import de.uniwuerzburg.omosim.calibration.objective.sseObjectiveGRB
import de.uniwuerzburg.omosim.calibration.objective.sseObjectiveTF
import de.uniwuerzburg.omosim.calibration.surrogate.SurrogateGravity
import de.uniwuerzburg.omosim.calibration.surrogate.TrafficCountVMatrixBuilderTF
import de.uniwuerzburg.omosim.core.models.*
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import java.time.LocalTime
import java.util.*

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
    fun calibrate(
        algorithm: CalibrationAlgorithm?,
        parameters: Map<String, String>,
        gurobi: Boolean = false,
        surrogate: Boolean = true
    ) : Map<ODTTriple, List<Double>> {
        // Compute expected origin-destination matrix
        val odtCounts = if (surrogate) {
            val rawCounts = getODTCountsSM()
            trimCounts(rawCounts, 0.1)
        } else {
            getODTCountsSimulation()
        }

        // Optimize routes
        return if (gurobi) {
           optimize(odtCounts)
        } else {
            val model = buildModelTF(odtCounts) // Create route choice model
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
            val obj = sseObjectiveGRB(model, context.sensors, simCount)
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
        val model = NativeModelUV(nVar)

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

    private fun buildModelTF(
        odtCounts: Map<ODTTriple, Double>
    ) : DifferentiableModelUV {
        // Determine indices
        val affectedIndices = mutableMapOf<TrafficSensor, List<MutableList<Int>>>()
        for (sensor in context.sensors) {
            affectedIndices[sensor] = List(T) { mutableListOf() }
        }
        var iVar = 0
        var nVar = 0
        var chunkID = 0
        val chunkIDs = mutableListOf<Int>()
        val counts = mutableListOf<Float>()
        for ((od, alternatives) in context.affectedAltSensors.entries) {
            for (t in 0 until T) {
                val odt = ODTTriple(od.first, od.second, t)
                if (odt in odtCounts) {
                    // Total variable count -> Number of routes
                    nVar += alternatives.size

                    // Determine chunks (routes that share the same odt)
                    chunkIDs.addAll(List(alternatives.size) {chunkID} )
                    chunkID += 1

                    // Expected odt count
                    val fCount = odtCounts[odt]!!.toFloat()
                    counts.addAll(List(alternatives.size) { fCount } )

                    // Which route affects which sensor?
                    for (alternative in alternatives) {
                        for (sensor in alternative) {
                            affectedIndices[sensor]!![t].add(iVar)
                        }
                        iVar += 1
                    }
                }
            }
        }

        // Initialize differentiable model
        val core = TfModelCore(nVar)
        val tf = core.tf

        // Route choice probabilities
        val chunkIDsTF = tf.constant(chunkIDs.toIntArray())
        val countsTF = tf.constant(counts.toFloatArray())
        val chunkSums = tf.math.unsortedSegmentSum(
            core.x,
            chunkIDsTF,
            tf.constant(chunkID + 1)
        )
        val expandedSum = tf.gather(chunkSums, chunkIDsTF, tf.constant(0))
        val p = tf.math.div(core.x, expandedSum)

        // Expected route choice count
        val e = tf.math.mul(p, countsTF)

        // Determine which routes affect which sensor
        val simCounts = mutableMapOf<TrafficSensor, MutableList<Operand<TFloat32>>>()
        for (sensor in context.sensors) {
            simCounts[sensor] = mutableListOf()
            for (t in 0 until T) {
                val indices = tf.constant( affectedIndices[sensor]!![t].toIntArray() )
                val sensorEs = tf.gather(e, indices,tf.constant(0) )
                val simCount = tf.reduceSum(sensorEs, tf.constant(0))
                simCounts[sensor]!!.add(simCount)
            }
        }

        // Objective
        val obj = sseObjectiveTF(core, context.sensors, simCounts)

        return TfModelUV(core, obj)
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
                    if (p0.isNotEmpty()) {
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

    private fun getODTCountsSM() : Map<ODTTriple, Double> {
        val n = context.omosim.grid.size
        val activity = ActivityType.WORK // Shouldn't matter

        // Build surrogate
        val model = SurrogateGravity(context).buildTF(
            activity,
            SMOriginDestination(context),
            TrafficCountVMatrixBuilderTF(context)
        )

        // Compute expected origin-destination matrix
        val x0 = Gravity.getX0(activity, context)
        val e = model.evaluate(x0)

        // Unpack matrix
        val odtCounts = mutableMapOf<ODTTriple, Double>()
        for ((i, v) in e.withIndex()) {
            val r = i % n
            val c = i / n % n
            val t = i / n / n

            val o = context.omosim.grid[r]
            val d = context.omosim.grid[c]

            val odt = ODTTriple(o, d, t)
            odtCounts[odt] = v
        }
        return odtCounts
    }

    /**
     * Determine how often a specific origin-destination-time triple occurs with a simulaiton
     * @param sharePop Share of population to used to estimate the expected origin-destination Matrix.
     * Must be quite high because of the many different possibilities for origin-destination-timestep combinations.
     * @return Occurrence
     */
    private fun getODTCountsSimulation(
        sharePop: Double = 1.0
    ) : Map<ODTTriple, Double> {
        // Should be different from the one used for other batch runs to avoid overfitting
        context.omosim.mainRng.setSeed(11)

        // Run Simulation
        val agents = context.omosim.run(sharePop, start_wd = context.weekday, verbose = false)
        context.omosim.doModeChoice(agents, ModeChoiceOption.FAST, withPath = false, verbose = false)

        // Get counts
        val odtCount = mutableMapOf<ODTTriple, Double>()
        for (agent in agents) {
            val demand = agent.mobilityDemand.first() // Get demand for first day

            // Get trip start times
            val startTimes = mutableListOf<LocalTime>()
            val origins = mutableListOf<Activity>()
            val destinations = mutableListOf<Activity>()
            val visitor: TripVisitor = { _: Trip, origin: Activity, destination: Activity,
                                         departureTime: LocalTime, _: Weekday, _: Boolean, _: Random? ->
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

                val t = startTime.determineTimeSlice()
                val odt = ODTTriple(origin, destination, t)
                odtCount[odt] = (odtCount[odt] ?: 0.0) + context.totalPopulation / agents.size
            }
        }
        return odtCount
    }

    /**
     * Remove counts below a certain threshhold
     *
     * @param odtCounts Counts to trim
     * @param limit Threshold
     */
    private fun trimCounts(odtCounts: Map<ODTTriple, Double>, limit: Double): Map<ODTTriple, Double> {
        return odtCounts.filter {(k, v) -> v > limit}
    }
}

