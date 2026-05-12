package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.*
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.sum
import org.jetbrains.kotlinx.multik.ndarray.operations.toArray
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.types.TFloat32

class TrafficCountVMatrixBuilderTF(
    val context: TrafficCountCalibrationContext
) : VMatrixBuilderTF {
    override fun build(
        mrep: SGGravity.SGCompactMatrixRep
    ): Pair<Operand<TFloat32>, TfModel>  {
        val nVars = context.omosim.grid.size - 1

        val model = TfModel(nVars)
        val tf = model.tf

        val tst = mrep.tMatrices[mrep.vActivity]!![0].toDoubleArray()
        tst[2] *= 100.0
        val value = tst[2] / tst.sum()
        println(value)
        println(tst[2])
        println(tst.sum())

        val tMatrix = mrep.tMatrices[mrep.vActivity]!!.toArray()
        val oTMatrix = model.addMatrix(tMatrix)

        println(model.x.shape())
        val vScaler = tf.concat(listOf(model.x, tf.constant(floatArrayOf(1f))), tf.constant(0))
        println(vScaler.shape())
        val mScaled = tf.math.mul(oTMatrix, vScaler)

        // Normalize
        println(mScaled.shape())
        val rowSum = tf.reduceSum(mScaled, tf.constant(intArrayOf(1)))
        val rowSumReshaped = tf.reshape(rowSum, tf.constant(longArrayOf(-1, 1)))
        val normalized = tf.math.div(mScaled, rowSumReshaped)

        // TEST
        model.session = Session(model.graph, model.config)
        val x = DoubleArray(model.nVars) {1.0}
        x[2] = 100.0
        model.fillInputTensor(x)

        model.session.runner()
            .feed(model.x, model.inputTensor)
            .fetch(normalized)
            .run().use { result ->
                val output = result[0] as TFloat32
                println( output.getFloat(0,2) )
            }
        // TEST

        return Pair(normalized, model)
    }
}

class TrafficCountVMatrixBuilderTFTest(
    val context: DistanceFunctionMatchContext
) : VMatrixBuilderTF {
    override fun build(
        mrep: SGGravity.SGCompactMatrixRep
    ): Pair<Operand<TFloat32>, TfModel>  {
        val nVars = context.omosim.grid.size - 1

        val model = TfModel(nVars)
        val tf = model.tf

        val tMatrix = mrep.tMatrices[mrep.vActivity]!!.toArray()
        val oTMatrix = model.addMatrix(tMatrix)

        val vScaler = tf.concat(listOf(model.x, tf.constant(floatArrayOf(1f))), tf.constant(0))
        val mScaled = tf.math.mul(oTMatrix, vScaler)

        // Normalize
        val rowSum = tf.reduceSum(mScaled, tf.constant(1))
        val normalized = tf.math.div(mScaled, rowSum)

        return Pair(normalized, model)
    }
}