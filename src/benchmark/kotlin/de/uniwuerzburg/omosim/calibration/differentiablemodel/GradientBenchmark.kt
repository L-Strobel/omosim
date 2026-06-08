package de.uniwuerzburg.omosim.calibration.differentiablemodel


import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.*
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import org.openjdk.jmh.annotations.*
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.AverageTime)
@Fork(value = 2)
@Warmup(iterations = 1)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class GradientBenchmark {
    var model: NativeModelUV? = null
    var modelTF: TfModelUV? = null
    var vars: DoubleArray? = null

    companion object {
        @Suppress("SameParameterValue")
        fun buildLargeTestModelTF(nVars: Int) : TfModelUV {
            val model = TfModelCore(nVars)
            val tf = model.tf
            val terms = mutableListOf<Operand<TFloat32>>()
            for (i in 0 until nVars) {
                val c = tf.constant(1.3f)
                val m = tf.math.mul(model.getVariable(i), tf.constant(3.3f))
                terms.add(c)
                terms.add(m)
            }
            val lTerm1 = tf.math.addN(terms)

            val c1 = tf.constant(2.2f)
            val m1 = tf.math.mul(model.getVariable(0), tf.constant(1.1f))
            val p1 = tf.math.add(c1, m1)

            val c2 = tf.constant(2.2f)
            val m2 = tf.math.mul(model.getVariable(1), tf.constant(1.1f))
            val p2 = tf.math.add(c2, m2)

            val mult = tf.math.mul(p1, p2)
            val top  = tf.math.mul(mult, tf.constant(-1.1f))

            val dTerm = tf.math.div(lTerm1, top)
            return TfModelUV(model, dTerm)
        }

        fun buildLargeTestModel(nVars: Int) : NativeModelUV {
            val model = NativeModelUV(nVars)

            val lTerm1 = LinearTerm(nVars)
            for (i in 0 until nVars) {
                val lbTerm = LinearBaseTerm(nVars)
                lbTerm.addConstant(1.3)
                lbTerm.addTerm(i, 3.3)
                lTerm1.addTerm(lbTerm, 1.0)
            }

            val p1 = LinearBaseTerm(nVars)
            p1.addConstant(2.2)
            p1.addTerm(0, 1.1)

            val p2 = LinearBaseTerm(nVars)
            p2.addConstant(2.2)
            p2.addTerm(1, 1.1)

            val top = QuadraticTerm(nVars, p1, p2,-1.1)

            val dTerm = DivisionTerm(nVars, lTerm1, top)
            model.setRootTerm(dTerm)
            return  model
        }
    }

    @Setup
    fun setup() {
        modelTF = buildLargeTestModelTF(1000)
        model = buildLargeTestModel(1000)
        vars = DoubleArray(model!!.nVars) { 1.1 }
    }

    @TearDown
    fun teardown() {
        modelTF?.close()
    }

    @Benchmark
    fun forwardBench(bh: Blackhole) {
        val g = DoubleArray(model!!.nVars) {0.0}
        for (i in 0 until model!!.nVars) {
            g[i] = model!!.gradientForward(i, vars!!)
        }
        bh.consume(g)
    }

    @Benchmark
    fun reverseBench(bh: Blackhole) {
        val g = DoubleArray(model!!.nVars) {0.0}
        model!!.gradientReverse(vars!!, g, 1.0)
        bh.consume(g)
    }

    @Benchmark
    fun tfBench(bh: Blackhole) {
        val g = DoubleArray(model!!.nVars) { 0.0 }
        modelTF!!.gradient(vars!!, g)
        bh.consume(g)
    }
}
