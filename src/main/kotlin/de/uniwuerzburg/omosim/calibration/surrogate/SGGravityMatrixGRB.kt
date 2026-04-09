package de.uniwuerzburg.omosim.calibration.surrogate

import com.gurobi.gurobi.*
import de.uniwuerzburg.omosim.calibration.*

/**
 * Optimize the transition matrix of one activity directly with the gurobi solver
 * while ignoring the structure of the gravity model.
 *
 * Requirement: Gurobi must be installed and Gurobi_HOME must be added to the path.
 *
 * @param activityType Activity for which the transition matrix is optimized.
 * @param iThresh Performance parameter.
 * All terms with coefficients below this value will be ignored and not added to the result.
 * Higher values -> Computes faster but is a rougher approximation of the markov chain representation.
 * @return Optimal transition matrix
 */
/*fun SGGravity<DifferentiableModelUVBase, GRBVar, GRBLinExpr, Matrix<GRBVar>>.optimizeTMatrix( // TODO
    activityType: ActivityType,
    iThresh: Double = 1e-4
) : D2Array<Double>? {
    context as TrafficCountCalibrationContext

    logger.info(
        "[Experimental] Calibrating transition matrix for activity $activityType directly." +
                "Number of variables: ${context.omosim.grid.size}"
    )
    val m3rep = generateMarkovChainRep(activityType)

    val n = context.omosim.grid.size
    val relevantODs = context.getRelevantODs()

    try {
        // Setup
        val env = GRBEnv()
        val model = GRBModel(env)

        // Create gurobi expression of the expected trips matrix: E(o, d | Car)
        val expectedTrips = ActivityType.entries.associateWith {
            Matrix(
                List(n) {
                    List(n) {
                        GRBLinExpr()
                    }
                }
            )
        }

        // Transition matrix
        val vMatrix = Matrix(
            List<List<GRBVar>>(n) { o ->
                model.addVars(
                    DoubleArray(n) { 0.0 },
                    DoubleArray(n) { 1.0 },
                    DoubleArray(n) { 0.0 },
                    CharArray(n) { GRB.CONTINUOUS },
                    Array(n) { d -> "W_${o}_$d" }
                ).toList()
            }
        )

        // Ensure that each row of vMatrix is a proper probability distribution
        for (o in 0 until n) {
            val rowSum = GRBLinExpr()
            for (d in 0 until n) {
                rowSum.addTerm(1.0, vMatrix.get(o, d))
            }
            model.addConstr(rowSum, GRB.EQUAL, 1.0, "PCondition")
        }

        // Temporal trip distribution
        val tripStartDistr = monteCarloTripStartDistribution(MC_SAMPLES)

        // Add expected trips for each destination activity
        for (activity in ActivityType.entries) {
            addE(
                GRBLinExprBuilder,
                n,
                m3rep,
                expectedTrips[activity]!!,
                vMatrix,
                relevantODs,
                iThresh,
                activity
            )
        }

        // Simulated Traffic Counts
        val simCount = mutableMapOf<TrafficSensor, List<GRBLinExpr>>()
        for (sensor in context.sensors) {
            simCount[sensor] = List(T) { GRBLinExpr() }
        }
        for ((o, origin) in context.omosim.grid.withIndex()) {
            for ((d, destination) in context.omosim.grid.withIndex()) {
                // Temporary variables to avoid GRBLinExpr().multAdd(). multAdd() leads to explosion in memory usage.
                val eODA = mutableMapOf<ActivityType, GRBVar> ()
                for (activity in ActivityType.entries) {
                    val v = model.addVar( 0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "demand")
                    model.addConstr( expectedTrips[activity]!!.get(o, d), GRB.EQUAL, v,"demandEq")
                    eODA[activity] = v
                }

                // Add expected trips to simulated traffic counts
                val od = Pair(origin, destination)
                if (od in context.affectedSensors) {
                    val affected = context.affectedSensors[od]!!
                    for (sensor in affected) {
                        for (t in 0 until T) {
                            for (activity in ActivityType.entries) {
                                simCount[sensor]!![t]
                                    .addTerm(context.totalPopulation * tripStartDistr[activity]!![t], eODA[activity]!!)
                            }
                        }
                    }
                }
            }
        }

        // Set Objective
        val obj = grbSseObjective(model, context.sensors, simCount)
        model.setObjective(obj, GRB.MINIMIZE)

        // Solve
        model.optimize()

        val success = handleGrbStatus(model)
        if (success) {
            val oval = model[GRB.DoubleAttr.ObjVal]
            logger.info("Optimization (gurobi) finished with optimal objective: $oval")

            // Ideal transition matrix
            val result = mk.ones<Double>(context.omosim.grid.size, context.omosim.grid.size)
            for(o in context.omosim.grid.indices) {
                for (d in context.omosim.grid.indices) {
                    result[o, d] = vMatrix.get(o, d).get(GRB.DoubleAttr.X)
                }
            }
            model.dispose()
            env.dispose()
            return result
        }

        model.dispose()
        env.dispose()
    }  catch (e: GRBException) {
        logger.error("Gurobi Error! Error code: ${e.errorCode}. ${e.message}")
    }
    return null
}

/**
 * Term builder for Gurobi.
 * Used to generate Gurobi Terms from matrix multiplications of the form: AXB,
 * where X is a matrix filled with variable terms.
 */
object GRBLinExprBuilder: TermBuilder<GRBLinExpr, GRBVar> {
    override fun addVar(term: GRBLinExpr, v: GRBVar, coefficient: Double) {
        term.addTerm(coefficient, v)
    }

    override fun addConstant(term: GRBLinExpr, constant: Double) {
        term.addConstant(constant)
    }

    override fun addTerm(term: GRBLinExpr, other: GRBLinExpr, coefficient: Double) {
        term.multAdd(coefficient, other)
    }

    override fun new(nVars: Int): GRBLinExpr {
        return GRBLinExpr()
    }
}*/