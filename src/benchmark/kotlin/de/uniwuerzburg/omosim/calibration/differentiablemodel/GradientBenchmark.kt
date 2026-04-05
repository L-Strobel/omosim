package de.uniwuerzburg.omosim.calibration.differentiablemodel


import kotlinx.benchmark.Blackhole
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.AverageTime)
@Fork(value = 2)
@Warmup(iterations = 1)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class GradientBenchmark {
    var model: DifferentiableModelUVBase? = null
    var vars: DoubleArray? = null

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
        model = buildLargeTestModel(1000)
        vars = DoubleArray(model!!.nVars) { 1.1 }
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
}
