package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.NativeModelUV
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.core.models.ActivityType

/**
 * Sum of squares error between measured and simulated traffic count.
 * Native.
 *
 * loss = sum((m-s)^2)
 */
class TrafficCountSSE(
    val context: TrafficCountCalibrationContext
) : SMGravityObjectiveNative<NativeModelUV> {
    override fun build (
        nVars: Int,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : NativeModelUV {
        // Simulated traffic counts
        val simCount = getSimCountsFromDemand(nVars, context, expectedTrips, tripStartDistr)

        // Objective
        val obj = sseObjective(nVars, context.sensors, simCount)

        // Create model
        val model = NativeModelUV(nVars)
        model.setRootTerm(obj)

        return model
    }
}