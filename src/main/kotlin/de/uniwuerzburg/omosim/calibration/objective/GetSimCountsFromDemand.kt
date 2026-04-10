package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.TrafficSensor
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.core.models.ActivityType

fun getSimCountsFromDemand(
    nVars: Int,
    context: TrafficCountCalibrationContext,
    expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
    tripStartDistr: Map<ActivityType, DoubleArray>
) : Map<TrafficSensor, List<LinearTerm>>{
    // Simulated traffic counts
    val simCount = mutableMapOf<TrafficSensor, List<LinearTerm>>()
    for (sensor in context.sensors) {
        simCount[sensor] = List(CalibrationConstants.T) { LinearTerm(nVars) }
    }
    for ((o, origin) in context.omosim.grid.withIndex()) {
        for ((d, destination) in context.omosim.grid.withIndex()) {
            val od = Pair(origin, destination)
            if (od in context.affectedSensors) {
                val affected = context.affectedSensors[od]!!
                for (sensor in affected) {
                    for (t in 0 until CalibrationConstants.T) {
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