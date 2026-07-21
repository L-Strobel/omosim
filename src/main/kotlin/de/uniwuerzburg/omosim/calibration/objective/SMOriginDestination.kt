package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelMV
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelMVOnlyEval
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.op.core.Constant
import org.tensorflow.types.TFloat32
import org.tensorflow.types.TInt32


/**
 * Return the expected origin-destination matrix of the gravity surrogate
 */
class SMOriginDestination(
    val context: TrafficCountCalibrationContext
) : SMGravityObjectiveTF<TfModelMVOnlyEval>  {
    override fun build (
        core: TfModelCore,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : TfModelMVOnlyEval {
        val tf = core.tf
        val totalPopulation = tf.constant(context.totalPopulation.toFloat())

        // Combined total expected od matrix
        val flatMatrices: MutableList<Operand<TFloat32>> = mutableListOf()
        val flatShape: Constant<TInt32?> = tf.constant(intArrayOf(-1))
        for (t in 0 until CalibrationConstants.T) {
            val e = mutableListOf<Operand<TFloat32>>()
            for (activity in ActivityType.entries) {
                val expectedTripsPopScaled = tf.math.mul(expectedTrips[activity]!!, totalPopulation)
                val timeShare = tf.constant(tripStartDistr[activity]!![t].toFloat())
                val expectedTripsTScaled = tf.math.mul(expectedTripsPopScaled, timeShare)
                e.add(expectedTripsTScaled)
            }
            val eMatrixT = tf.math.addN(e)
            val flat = tf.reshape(eMatrixT, flatShape) // Flatten
            flatMatrices.add(flat)
        }
        val eTotalFlat: Operand<TFloat32> = tf.concat(flatMatrices, tf.constant(0)) // Stack

        return TfModelMVOnlyEval(core, eTotalFlat)
    }
}
