package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import org.tensorflow.Graph
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.ndarray.Shape
import org.tensorflow.ndarray.StdArrays
import org.tensorflow.op.Ops
import org.tensorflow.op.core.Variable
import org.tensorflow.proto.ConfigProto
import org.tensorflow.proto.GPUOptions
import org.tensorflow.proto.GraphOptions
import org.tensorflow.proto.OptimizerOptions
import org.tensorflow.types.TFloat32

class TfModelCore(
    val nVars: Int
) {
    val graph = Graph()
    val tf: Ops = Ops.create(graph)
    val x: Variable<TFloat32> = tf.variable(Shape.of(nVars.toLong()), TFloat32::class.java)
    val inputTensorContainer: ThreadLocal<ThreadLocalTensorContainer> = ThreadLocal.withInitial { // TODO factor out?
        ThreadLocalTensorContainer(nVars.toLong())
    }
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

    fun addMatrix(data: Array<DoubleArray>) : Operand<TFloat32> {
        val fData = data.map { dArray ->
            dArray.map { it.toFloat() }.toFloatArray()
        }.toTypedArray()
        val tensor = TFloat32.tensorOf( StdArrays.ndCopyOf( fData ) )
        this.addTensor(tensor)
        return tf.constant(tensor)
    }

    fun addMatrix(data: Array<FloatArray>) : Operand<TFloat32> {
        val tensor = TFloat32.tensorOf( StdArrays.ndCopyOf( data ) )
        this.addTensor(tensor)
        return tf.constant(tensor)
    }

    private fun addTensor(tensor: TFloat32) {
        tensors.add(tensor)
    }

    fun close() {
        this.inputTensorContainer.remove()
        this.tensors.forEach { it.close() }
    }

    fun fillInputTensor(data: DoubleArray) {
        val floatData = FloatArray(data.size) { i -> data[i].toFloat() }
        StdArrays.copyTo(floatData, inputTensorContainer.get().tensor)
    }

    fun getSize(): Int {
        return graph.operations().asSequence().count()
    }
}