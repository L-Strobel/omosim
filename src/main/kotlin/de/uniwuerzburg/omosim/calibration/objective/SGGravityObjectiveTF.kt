package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

interface SGGravityObjectiveTF {
    fun build (
        model: TfModel,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : TfModel
}