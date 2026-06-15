package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

class TrafficCountSSETF(
    val context: TrafficCountCalibrationContext
) : SMGravityObjectiveTF<TfModelUV> {
    override fun build (
        core: TfModelCore,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : TfModelUV {
        val tf = core.tf
        val simCount = getSimCountsFromDemandTF(context, core, expectedTrips, tripStartDistr)

        // Objective
        val s = mutableListOf<Operand<TFloat32>>()
        val m = mutableListOf<Float>()
        for (sensor in context.sensors) {
            for (t in 0 until CalibrationConstants.T) {
                s.add(simCount[sensor]!![t])
                m.add(sensor.measurements[t].toFloat())
            }
        }
        val vSim = tf.stack(s)
        val vMeasured = tf.constant(m.toFloatArray())
        val diff = tf.math.sub(vSim, vMeasured)
        val sqrDiff = tf.math.square(diff)
        val obj = tf.reduceSum(sqrDiff, tf.constant(0))

        return TfModelUV(core, obj)
    }
}