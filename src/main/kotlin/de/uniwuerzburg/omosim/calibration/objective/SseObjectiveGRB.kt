package de.uniwuerzburg.omosim.calibration.objective

import com.gurobi.gurobi.*
import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficSensor

/**
 * Objective: sum(m-s)^2
 *
 * m: measurement
 * s: simulated value
 *
 * For Gurobi.
 */
fun sseObjectiveGRB(
    model: GRBModel, sensors: List<TrafficSensor>, simCount: Map<TrafficSensor, List<GRBLinExpr>>
) : GRBExpr {
    // Create Gurobi variable for simCount. Necessary for GRBQuadExpr
    val vSimCount = List(CalibrationConstants.T) {
        model.addVars(
            DoubleArray(sensors.size) { 0.0 },
            null,
            DoubleArray(sensors.size) { 0.0 },
            CharArray(sensors.size) { GRB.CONTINUOUS },
            Array(sensors.size) { "" }
        )
    }
    for ((i, sensor) in sensors.withIndex()) {
        for (t in 0 until CalibrationConstants.T) {
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
        for (t in 0 until CalibrationConstants.T) {
            // (Sm - Ss)^2 = Sm^2 - 2SmSs + Ss^2
            obj.addConstant(sensor.measurements[t] * sensor.measurements[t])
            obj.addTerm(-2 * sensor.measurements[t], vSimCount[t][i])
            obj.addTerm(1.0, vSimCount[t][i], vSimCount[t][i])
        }
    }
    return obj
}