package de.uniwuerzburg.omosim.calibration

import com.gurobi.gurobi.GRB
import com.gurobi.gurobi.GRBExpr
import com.gurobi.gurobi.GRBLinExpr
import com.gurobi.gurobi.GRBModel
import com.gurobi.gurobi.GRBQuadExpr
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

object TrafficCountSSE : SGGravityObjective<TrafficCountCalibrationContext, DifferentiableModelSingleOut> {
    override fun build (
        nVars: Int,
        context: TrafficCountCalibrationContext,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : DifferentiableModelSingleOut {
        // Simulated traffic counts
        val simCount = getSimCountsFromDemand(nVars, context, expectedTrips, tripStartDistr)

        // Objective
        val obj = sseObjective(nVars, context.sensors, simCount)

        // Create model
        val model = DifferentiableModelSingleOut(nVars)
        model.setRootTerm(obj)

        return model
    }
}

object TrafficCountSeparate : SGGravityObjective<TrafficCountCalibrationContext, DifferentiableModelMultiOut> {
    override fun build (
        nVars: Int,
        context: TrafficCountCalibrationContext,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : DifferentiableModelMultiOut {
        // Simulated traffic counts
        val simCount = getSimCountsFromDemand(nVars, context, expectedTrips, tripStartDistr)

        // Create DifferentiableModelMultiOut from simulated counts
        val countsFlat = mutableListOf<Term>()
        for (sensor in context.sensors) {
            for (t in 0 until T) {
                countsFlat.add( simCount[sensor]!![t] )
            }
        }
        val model = DifferentiableModelMultiOut(countsFlat.first().nVars)
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
