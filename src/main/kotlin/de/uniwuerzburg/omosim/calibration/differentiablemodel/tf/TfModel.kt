package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelUV
import org.tensorflow.Graph
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.ndarray.Shape
import org.tensorflow.ndarray.buffer.DataBuffers
import org.tensorflow.op.Ops
import org.tensorflow.op.core.Constant
import org.tensorflow.op.core.Gradients
import org.tensorflow.types.TFloat32


class TfModel(nVars: Int): DifferentiableModelUV(nVars) {
    val graph = Graph()
    val tf = Ops.create(graph)
    val x = tf.variable(Shape.of(nVars.toLong()), TFloat32::class.java)
    private var root: Operand<TFloat32>? = null
    var dx: Gradients? = null

    fun getVariable(i: Int) : Operand<TFloat32> {
        return tf.gather(x, tf.constant(i), tf.constant(0))
    }

    fun createConstant(value: Float) : Constant<TFloat32> {
        return tf.constant(value)
    }

    fun createLinearTerm() : Operand<TFloat32> {
        return tf.constant(0f)
    }

    fun createLinearTerm(x: Operand<TFloat32>, y: Operand<TFloat32>) : Operand<TFloat32> {
        return tf.math.add(x, y)
    }

    fun createLinearTerm(terms: List<Operand<TFloat32>>) : Operand<TFloat32> {
        return tf.math.addN(terms)
    }

    fun createMultiplication(x: Operand<TFloat32>, y: Operand<TFloat32>) : Operand<TFloat32> {
        return tf.math.mul(x, y)
    }

    fun createDivision(dividend: Operand<TFloat32>, divisor: Operand<TFloat32>) : Operand<TFloat32> {
        return tf.math.div(dividend, divisor)
    }

    fun createExponentiation(exponent: Operand<TFloat32>) : Operand<TFloat32> {
        return tf.math.exp(exponent)
    }

    fun createPower(base: Operand<TFloat32>, power: Float) : Operand<TFloat32> {
        return tf.math.pow(base, tf.constant(power))
    }

    fun setRoot(root: Operand<TFloat32>) {
        this.root = root
        this.dx = tf.gradients(root, listOf(x))
    }



    override fun gradient(vals: DoubleArray, gradient: DoubleArray) {
        val outArray = FloatArray(gradient.size)
        val outFBuffer = DataBuffers.ofFloats(vals.size.toLong())
        val floatVals = vals.map { it.toFloat() }.toFloatArray()
        val xValue = TFloat32.vectorOf(*floatVals)
        val dxOperand: Operand<TFloat32> = dx!!.dy(0)

        Session(graph).use { session ->
            val result = session.runner()
                .feed(x, xValue)
                .fetch(dxOperand)
                .run()
            val gradientOutput = result[0] as TFloat32
            gradientOutput.copyTo(outFBuffer)
            gradientOutput.close()
        }
        outFBuffer.read(outArray)
        for (i in outArray.indices) {
            gradient[i] = outArray[i].toDouble()
        }
    }

    override fun evaluate(vals: DoubleArray): Double {
        TODO("Not yet implemented")
    }

    override fun f(p0: DoubleArray?): Double {
        TODO("Not yet implemented")
    }
    override fun g(x: DoubleArray?, gradient: DoubleArray?): Double {
        TODO("Not yet implemented")
    }
}