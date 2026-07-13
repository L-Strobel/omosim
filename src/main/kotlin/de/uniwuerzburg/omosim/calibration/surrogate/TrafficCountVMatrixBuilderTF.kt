package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import org.jetbrains.kotlinx.multik.ndarray.operations.toArray
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

/**
 * Matrix that defines how the transition matrix of vActivity changes with the attraction value scalers.
 * TensorFlow.
 */
class TrafficCountVMatrixBuilderTF(
    val context: TrafficCountCalibrationContext
) : VMatrixBuilderTF {
    override fun build(
        mrep: SurrogateGravity.SGCompactMatrixRep
    ): Pair<Operand<TFloat32>, TfModelCore>  {
        val nVars = context.omosim.grid.size - 1

        val model = TfModelCore(nVars)
        val tf = model.tf

        val tMatrix = mrep.tMatrices[mrep.vActivity]!!.toArray()
        val oTMatrix = model.addMatrix(tMatrix)

        // Add scalers
        val vScaler = tf.concat(listOf(model.x, tf.constant(floatArrayOf(1f))), tf.constant(0))
        val mScaled = tf.math.mul(oTMatrix, vScaler)

        // Normalize
        val rowSum = tf.reduceSum(mScaled, tf.constant(intArrayOf(1)))
        val rowSumReshaped = tf.reshape(rowSum, tf.constant(longArrayOf(-1, 1)))
        val normalized = tf.math.div(mScaled, rowSumReshaped)

        return Pair(normalized, model)
    }
}
