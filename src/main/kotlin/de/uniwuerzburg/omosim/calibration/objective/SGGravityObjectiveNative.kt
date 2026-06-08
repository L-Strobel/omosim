package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModel
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.core.models.ActivityType

interface SGGravityObjectiveNative <M: DifferentiableModel> {
    fun build (
        nVars: Int,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : M
}