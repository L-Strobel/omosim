package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.NativeModelMV
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.Term
import de.uniwuerzburg.omosim.core.models.ActivityType

class TrafficCountSeparate(
    val context: TrafficCountCalibrationContext
) : SGGravityObjectiveNative<NativeModelMV> {
    override fun build (
        nVars: Int,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : NativeModelMV {
        // Simulated traffic counts
        val simCount = getSimCountsFromDemand(nVars, context, expectedTrips, tripStartDistr)

        // Create DifferentiableModelMultiOut from simulated counts
        val countsFlat = mutableListOf<Term>()
        for (sensor in context.sensors) {
            for (t in 0 until CalibrationConstants.T) {
                countsFlat.add( simCount[sensor]!![t] )
            }
        }
        val model = NativeModelMV(countsFlat.first().nVars)
        model.setRootTerms(countsFlat)

        return model
    }
}