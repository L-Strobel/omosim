package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelMV
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.ndarray.StdArrays
import org.tensorflow.types.TFloat32

/**
 * Multivariate TensorFlow model.
 */
class TfModelMV(
    val core: TfModelCore,
    roots: List<Operand<TFloat32>>
) : DifferentiableModelMV {
    val nOutput = roots.size
    val root: Operand<TFloat32>
    val dx: Operand<TFloat32>
    val session: Session

    init {
        val jRows: List<Operand<TFloat32>> = roots.map { root ->
            core.tf.gradients(root, listOf(core.x)).dy(0)
        }
        this.root = core.tf.stack(roots)
        this.dx = core.tf.stack(jRows)
        this.session = Session(core.graph, core.config)
    }

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
        core.fillInputTensor(vals) // Load input

        val outArray = Array(nOutput) { FloatArray(core.nVars) }

        // Compute
        session.runner()
            .feed(core.x, core.inputTensorContainer.get().tensor)
            .fetch(dx)
            .run().use { result ->
                val jacobianOutput = result[0] as TFloat32
                StdArrays.copyFrom(jacobianOutput, outArray)
            }

        val doubleArray = outArray.map {
            row -> row.map {
                entry -> entry.toDouble()
            }.toDoubleArray()
        }.toTypedArray()

        return doubleArray
    }

    override fun getSize(): Int {
        return core.getSize()
    }

    fun close() {
        this.session.close()
        core.close()
    }
}