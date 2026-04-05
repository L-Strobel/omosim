package de.uniwuerzburg.omosim.calibration.differentiablemodel


import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import org.openjdk.jmh.annotations.*
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import smile.stat.Hypothesis.F
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.AverageTime)
@Fork(value = 2)
@Warmup(iterations = 1)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class GradientBenchmark {
    var model: DifferentiableModelUVBase? = null
    var modelTF: TfModel? = null
    var vars: DoubleArray? = null
    var varsF: TFloat32? = null

    @Suppress("SameParameterValue")
    fun buildLargeTestModelTF(nVars: Int) : TfModel {
        val model = TfModel(nVars)

        val terms = mutableListOf<Operand<TFloat32>>()
        for (i in 0 until nVars) {
            val c = model.createConstant(1.3f)
            val m = model.createMultiplication(model.getVariable(i), model.createConstant(3.3f))
            terms.add(c)
            terms.add(m)
        }
        val lTerm1 = model.createLinearTerm(terms)

        val c1 = model.createConstant(2.2f)
        val m1 = model.createMultiplication(model.getVariable(0), model.createConstant(1.1f))
        val p1 = model.createLinearTerm(c1, m1)

        val c2 = model.createConstant(2.2f)
        val m2 = model.createMultiplication(model.getVariable(1), model.createConstant(1.1f))
        val p2 = model.createLinearTerm(c2, m2)

        val mult = model.createMultiplication(p1, p2)
        val top = model.createMultiplication(mult, model.createConstant(-1.1f))

        val dTerm = model.createDivision(lTerm1, top)
        model.setRoot(dTerm)
        return model
    }

    fun buildLargeTestModel(nVars: Int) : DifferentiableModelUVBase {
        val model = DifferentiableModelUVBase(nVars)

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

    @Setup
    fun setup() {
        modelTF = buildLargeTestModelTF(5000)
        model = buildLargeTestModel(5000)
        vars = DoubleArray(model!!.nVars) { 1.1 }
        varsF =  TFloat32.vectorOf(*FloatArray(model!!.nVars) { 1.1f })
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
        val g = FloatArray(model!!.nVars) { 0.0f }
        modelTF!!.gradientF(varsF!!, g)
        bh.consume(g)
    }
}
