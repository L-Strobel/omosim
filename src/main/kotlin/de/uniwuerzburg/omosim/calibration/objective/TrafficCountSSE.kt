package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.DifferentiableModelUVBase
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.core.models.ActivityType

class TrafficCountSSE(
    val context: TrafficCountCalibrationContext
) : SGGravityObjectiveNative<DifferentiableModelUVBase> {
    override fun build (
        nVars: Int,
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