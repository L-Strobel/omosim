package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

/**
 * Sum of squares error between measured and simulated traffic count.
 * TensorFlow.
 *
 * loss = sum((m-s)^2)
 */
class TrafficCountSSETF(
    val context: TrafficCountCalibrationContext
) : SMGravityObjectiveTF<TfModelUV> {
    override fun build (
        core: TfModelCore,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : TfModelUV {
        val simCount = getSimCountsFromDemandTF(context, core, expectedTrips, tripStartDistr)
        val obj = sseObjectiveTF(core, context.sensors, simCount)
        return TfModelUV(core, obj)
    }
}