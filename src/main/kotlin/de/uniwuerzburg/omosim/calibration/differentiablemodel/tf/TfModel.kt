package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelUV
import org.tensorflow.Graph
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.ndarray.Shape
import org.tensorflow.ndarray.buffer.DataBuffers
import org.tensorflow.ndarray.buffer.FloatDataBuffer
import org.tensorflow.op.Ops
import org.tensorflow.op.core.Constant
import org.tensorflow.op.core.Variable
import org.tensorflow.proto.ConfigProto
import org.tensorflow.proto.GPUOptions
import org.tensorflow.proto.GraphOptions
import org.tensorflow.proto.OptimizerOptions
import org.tensorflow.types.TFloat32


class TfModel(nVars: Int): DifferentiableModelUV(nVars) {
    val graph = Graph()
    val tf: Ops = Ops.create(graph)
    val x: Variable<TFloat32> = tf.variable(Shape.of(nVars.toLong()), TFloat32::class.java)
    val inputTensor: TFloat32 = TFloat32.tensorOf(Shape.of(nVars.toLong()))
    private val ioBuffer: FloatDataBuffer = DataBuffers.ofFloats(nVars.toLong())
    private lateinit var root: Operand<TFloat32>
    private lateinit var dx: Operand<TFloat32>
    lateinit var session: Session
    private val tensors = mutableListOf<TFloat32>()

    // Configs
    private var graphOptions: GraphOptions = GraphOptions.newBuilder()
        .setOptimizerOptions(
            OptimizerOptions.newBuilder()
                .setGlobalJitLevel(OptimizerOptions.GlobalJitLevel.ON_1)
                .setOptLevel(OptimizerOptions.Level.L1)
                .build()
        )
        .build()
    private var gpuOptions: GPUOptions = GPUOptions.newBuilder()
        .setAllowGrowth(true)
        .setPerProcessGpuMemoryFraction(0.5)
        .build()
    var config: ConfigProto = ConfigProto.newBuilder()
        .setAllowSoftPlacement(true)
        .setGraphOptions(graphOptions)
        .setGpuOptions(gpuOptions)
        .setIntraOpParallelismThreads(Runtime.getRuntime().availableProcessors())
        .setInterOpParallelismThreads(2)
        .build()

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

    fun addTensor(tensor: TFloat32) {
        tensors.add(tensor)
    }

    fun finalize(root: Operand<TFloat32>) {
        this.root = root
        this.dx = tf.gradients(root, listOf(x)).dy(0)
        this.session = Session(graph, config)
    }

    fun close() {
        this.session.close()
        this.inputTensor.close()
        this.tensors.forEach { it.close() }
    }

    fun fillInputTensor(data: DoubleArray) {
        for (i in data.indices) {
            ioBuffer.setFloat(data[i].toFloat(), i.toLong())
        }
        inputTensor.copyFrom(ioBuffer)
    }

    override fun gradient(vals: DoubleArray, gradient: DoubleArray) : Double {
        fillInputTensor(vals) // Load input

        // Compute
        var y: Double? = null
        session.runner()
            .feed(x, inputTensor)
            .fetch(root)
            .fetch(dx)
            .run().use { result ->
                val lossOutput = result[0] as TFloat32
                y = lossOutput.getFloat().toDouble()
                val gradientOutput = result[1] as TFloat32
                gradientOutput.copyTo(ioBuffer)
                gradientOutput.close()
            }

        // Store in Java
        val outArray = FloatArray(gradient.size)
        ioBuffer.read(outArray)
        for (i in outArray.indices) {
            gradient[i] = outArray[i].toDouble()
        }

        return y!!
    }

    override fun evaluate(vals: DoubleArray): Double {
        fillInputTensor(vals) // Load input

        // Compute
        var y: Double? = null
        session.runner()
            .feed(x, inputTensor)
            .fetch(root)
            .run().use { result ->
                val lossOutput = result[0] as TFloat32
                y = lossOutput.getFloat().toDouble()
            }
        return y!!
    }

    override fun f(p0: DoubleArray?): Double {
        return evaluate(p0!!)
    }

    override fun g(x: DoubleArray?, gradient: DoubleArray?): Double {
        return gradient(x!!, gradient!!)
    }

    override fun getSize(): Int {
        return graph.operations().asSequence().count()
    }
}