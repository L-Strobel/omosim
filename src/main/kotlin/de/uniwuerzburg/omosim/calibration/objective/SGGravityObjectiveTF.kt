package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModel
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

interface SGGravityObjectiveTF<M: DifferentiableModel> {
    fun build (
        core: TfModelCore,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : M
}