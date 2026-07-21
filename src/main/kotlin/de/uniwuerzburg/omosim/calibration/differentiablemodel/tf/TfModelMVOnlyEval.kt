package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelMV
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.ndarray.StdArrays
import org.tensorflow.types.TFloat32

/**
 * Multivariate TensorFlow model without the option to compute gradients.
 *
 * @param core Graph builder
 * @param root Should be a 1d-Vector
 */
class TfModelMVOnlyEval(
    val core: TfModelCore,
    val root: Operand<TFloat32>
) : DifferentiableModelMV {
    val nOutput = root.shape().get(0).toInt()
    val session = Session(core.graph, core.config)

    override fun evaluate(vals: DoubleArray): DoubleArray {
        core.fillInputTensor(vals) // Load input

        val outArray = FloatArray(this.nOutput)

        // Compute
        session.runner()
            .feed(core.x, core.inputTensorContainer.get().tensor)
            .fetch(root)
            .run().use { result ->
                val output = result[0] as TFloat32
                StdArrays.copyFrom(output, outArray)
            }
        return outArray.map { it.toDouble() }.toDoubleArray()
    }

    override fun jacobian(vals: DoubleArray, nWorker: Int?): Array<DoubleArray> {
        throw NotImplementedError("Jacobian can not be computed for an OnlyEvaluate model!")
    }

    override fun getSize(): Int {
        return core.getSize()
    }

    fun close() {
        this.session.close()
        core.close()
    }
}