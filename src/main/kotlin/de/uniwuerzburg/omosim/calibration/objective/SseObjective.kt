package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficSensor
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.QuadraticTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.Term

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
        for (t in 0 until CalibrationConstants.T) {
            // (m - s)^2 = m^2 - 2ms + s^2
            val s = simCount[sensor]!![t]
            val m = sensor.measurements[t]
            obj.addConstant(m * m)
            obj.addTerm(s, -2 * m)
            val qTerm = QuadraticTerm(nVars, s, s, 1.0)
            obj.addTerm(qTerm, 1.0)
        }
    }
    return obj
}