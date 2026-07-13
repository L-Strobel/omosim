package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelUV
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.ndarray.StdArrays
import org.tensorflow.types.TFloat32

/**
 * Univariate TensorFlow model.
 */
class TfModelUV(
    val core: TfModelCore,
    val root: Operand<TFloat32>
): DifferentiableModelUV {
    val dx: Operand<TFloat32> = core.tf.gradients(root, listOf(core.x)).dy(0)
    val session: Session = Session(core.graph, core.config)

    override fun gradient(vals: DoubleArray, gradient: DoubleArray) : Double {
        core.fillInputTensor(vals) // Load input

        // Compute
        val y = session.runner()
            .feed(core.x, core.inputTensorContainer.get().tensor)
            .fetch(root)
            .fetch(dx)
            .run().use { result ->
                val lossOutput = result[0] as TFloat32
                val lossValue =  lossOutput.getFloat().toDouble()
                val gradientOutput = result[1] as TFloat32

                // Store in Java
                val outArray = FloatArray(gradient.size)
                StdArrays.copyFrom(gradientOutput, outArray)
                for (i in outArray.indices) {
                    gradient[i] = outArray[i].toDouble()
                }

                lossValue
            }
        return y
    }

    override fun evaluate(vals: DoubleArray): Double {
        core.fillInputTensor(vals) // Load input

        // Compute
        val y = session.runner()
            .feed(core.x, core.inputTensorContainer.get().tensor)
            .fetch(root)
            .run().use { result ->
                val lossOutput = result[0] as TFloat32
                lossOutput.getFloat().toDouble()
            }
        return y
    }

    override fun numberOfVariables(): Int {
        return core.nVars
    }

    override fun f(p0: DoubleArray?): Double {
        return evaluate(p0!!)
    }

    override fun g(x: DoubleArray?, gradient: DoubleArray?): Double {
        return gradient(x!!, gradient!!)
    }

    override fun getSize(): Int {
        return core.getSize()
    }

    fun close() {
        this.session.close()
        core.close()
    }
}