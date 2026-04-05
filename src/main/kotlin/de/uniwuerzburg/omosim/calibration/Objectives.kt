package de.uniwuerzburg.omosim.calibration

import com.gurobi.gurobi.*
import de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
import de.uniwuerzburg.omosim.calibration.differentiablemodel.*
import de.uniwuerzburg.omosim.core.models.ActivityType

/**
 * Objective: sum(m-s)^2
 *
 * m: measurement
 * s: simulated value
 *
 * For differentiable model.  @see de.uniwuerzburg.omod.calibration.differentiablemodel
 */
fun sseObjective(nVars: Int, sensors: List<TrafficSensor>, simCount: Map<TrafficSensor, List<Term>>) : LinearTerm {
    val obj = LinearTerm(nVars)
    for (sensor in sensors) {
        for (t in 0 until T) {
            // (m - s)^2 = m^2 - 2ms + s^2
            val s = simCount[sensor]!![t]
            val m = sensor.measurements[t]
            obj.addConstant(m * m)
            obj.addTerm(s, -2 * m)
            val qTerm = QuadraticTerm(nVars, s, s,1.0)
            obj.addTerm(qTerm, 1.0)
        }
    }
    return obj
}

/**
 * Objective: sum(m-s)^2
 *
 * m: measurement
 * s: simulated value
 *
 * For Gurobi.
 */
fun grbSseObjective(
    model: GRBModel, sensors: List<TrafficSensor>, simCount: Map<TrafficSensor, List<GRBLinExpr>>
) : GRBExpr {
    // Create Gurobi variable for simCount. Necessary for GRBQuadExpr
    val vSimCount = List(T) {
        model.addVars(
            DoubleArray(sensors.size) { 0.0 },
            null,
            DoubleArray(sensors.size) { 0.0 },
            CharArray(sensors.size) { GRB.CONTINUOUS },
            Array(sensors.size) { "" }
        )
    }
    for ((i, sensor) in sensors.withIndex()) {
        for (t in 0 until T) {
            model.addConstr(
                simCount[sensor]!![t],
                GRB.EQUAL,
                vSimCount[t][i]!!,
                "cnteq"
            )
        }
    }

    // Objective
    val obj = GRBQuadExpr()
    for ((i, sensor) in sensors.withIndex()) {
        for (t in 0 until T) {
            // (Sm - Ss)^2 = Sm^2 - 2SmSs + Ss^2
            obj.addConstant(sensor.measurements[t] * sensor.measurements[t])
            obj.addTerm(-2 * sensor.measurements[t], vSimCount[t][i])
            obj.addTerm(1.0, vSimCount[t][i], vSimCount[t][i])
        }
    }
    return obj
}

interface SGGravityObjective <T: CalibrationContext, M: DifferentiableModel> {
    fun build (
        nVars: Int,
        context: T,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : M
}

object TrafficCountSSE : SGGravityObjective<TrafficCountCalibrationContext, DifferentiableModelUVBase> {
    override fun build (
        nVars: Int,
        context: TrafficCountCalibrationContext,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : DifferentiableModelUVBase {
        // Simulated traffic counts
        val simCount = getSimCountsFromDemand(nVars, context, expectedTrips, tripStartDistr)

        // Objective
        val obj = sseObjective(nVars, context.sensors, simCount)

        // Create model
        val model = DifferentiableModelUVBase(nVars)
        model.setRootTerm(obj)

        return model
    }
}

object TrafficCountSeparate : SGGravityObjective<TrafficCountCalibrationContext, DifferentiableModelMV> {
    override fun build (
        nVars: Int,
        context: TrafficCountCalibrationContext,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : DifferentiableModelMV {
        // Simulated traffic counts
        val simCount = getSimCountsFromDemand(nVars, context, expectedTrips, tripStartDistr)

        // Create DifferentiableModelMultiOut from simulated counts
        val countsFlat = mutableListOf<Term>()
        for (sensor in context.sensors) {
            for (t in 0 until T) {
                countsFlat.add( simCount[sensor]!![t] )
            }
        }
        val model = DifferentiableModelMV(countsFlat.first().nVars)
        model.setRootTerms(countsFlat)

        return model
    }
}

private fun getSimCountsFromDemand(
    nVars: Int,
    context: TrafficCountCalibrationContext,
    expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
    tripStartDistr: Map<ActivityType, DoubleArray>
) : Map<TrafficSensor, List<LinearTerm>>{
    // Simulated traffic counts
    val simCount = mutableMapOf<TrafficSensor, List<LinearTerm>>()
    for (sensor in context.sensors) {
        simCount[sensor] = List(T) { LinearTerm(nVars) }
    }
    for ((o, origin) in context.omosim.grid.withIndex()) {
        for ((d, destination) in context.omosim.grid.withIndex()) {
            val od = Pair(origin, destination)
            if (od in context.affectedSensors) {
                val affected = context.affectedSensors[od]!!
                for (sensor in affected) {
                    for (t in 0 until T) {
                        for (activity in ActivityType.entries) {
                            simCount[sensor]!![t].addTerm(
                                expectedTrips[activity]!![o][d],
                                coefficient = context.totalPopulation * tripStartDistr[activity]!![t]
                            )
                        }
                    }
                }
            }
        }
    }
    return simCount
}


class DFMatchSSE (
    val activity: ActivityType,
    val mean: Double,
    val m2: Double
) : SGGravityObjective<DistanceFunctionMatchContext, DifferentiableModelUVBase> {
    override fun build (
        nVars: Int,
        context: DistanceFunctionMatchContext,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : DifferentiableModelUVBase {
        val omosim = context.omosim
        val n = context.omosim.grid.size

        val expectedTotalDistance = LinearTerm(nVars)
        val expectedTotalTrips = LinearTerm(nVars)
        val expectedODCount = mutableMapOf<Pair<Int, Int>, LinearTerm>()
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)

            for (d in 0 until n) {
                val expectedTripCount = LinearTerm(nVars)
                expectedTripCount.addTerm(
                    expectedTrips[activity]!![o][d],
                    context.totalPopulation
                )
                expectedODCount[Pair(o,d)] = expectedTripCount

                expectedTotalDistance.addTerm(
                    expectedTripCount,
                    distances[d] / 1000.0
                )
                expectedTotalTrips.addTerm(
                    expectedTripCount,
                    1.0
                )
            }
        }
        val expectedMean = DivisionTerm(nVars, expectedTotalDistance, expectedTotalTrips)

        val expectedSumOfSquare = LinearTerm(nVars)
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)

            for (d in 0 until n) {
                val expectedTripCount = expectedODCount[Pair(o,d)]!!

                expectedSumOfSquare.addTerm(
                    expectedTripCount,
                    (distances[d] / 1000.0) * (distances[d] / 1000.0)
                )
            }
        }
        val moment2 = DivisionTerm(nVars, expectedSumOfSquare, expectedTotalTrips)

        // (m - s)^2 = m^2 - 2ms + s^2
        val obj = LinearTerm(nVars)
        obj.addConstant(mean * mean * 10)
        obj.addTerm(expectedMean, -2 * mean * 10)
        val qTermMean = QuadraticTerm(nVars, expectedMean, expectedMean,1.0)
        obj.addTerm(qTermMean, 1.0 * 10.0)

        val objMoment2 = LinearTerm(nVars)
        objMoment2.addConstant(m2 * m2)
        objMoment2.addTerm(moment2, -2 * m2)
        val qTermVar = QuadraticTerm(nVars, moment2, moment2,1.0)
        objMoment2.addTerm(qTermVar, 1.0)

        obj.addTerm(
            //objMoment2, 1.0
            PowerTerm(nVars, objMoment2, 0.5), 1.0
        )

        val model = DifferentiableModelUVBase(nVars)
        model.setRootTerm(obj)

        return model
    }
}