package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelMV
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

/**
 * Returns simulated traffic counts as a vector. Used for W-SPSA.
 * TensorFlow.
 */
class TrafficCountSeparateTF(
    val context: TrafficCountCalibrationContext
) : SMGravityObjectiveTF<TfModelMV>  {
    override fun build (
        core: TfModelCore,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : TfModelMV {
        val simCount = getSimCountsFromDemandTF(context, core, expectedTrips, tripStartDistr)

        val s = mutableListOf<Operand<TFloat32>>()
        for (sensor in context.sensors) {
            for (t in 0 until CalibrationConstants.T) {
                s.add(simCount[sensor]!![t])
            }
        }

        return TfModelMV(core, s)
    }
}
