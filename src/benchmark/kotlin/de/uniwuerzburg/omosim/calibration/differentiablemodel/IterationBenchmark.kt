package de.uniwuerzburg.omosim.calibration.differentiablemodel


import de.uniwuerzburg.omosim.calibration.algorithms.PSO
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.DifferentiableModelUV
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import kotlinx.benchmark.Blackhole
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.AverageTime)
@Fork(value = 2)
@Warmup(iterations = 5)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class IterationBenchmark {
    var model: DifferentiableModelUV? = null
    var modelTF: TfModel? = null
    var vars: DoubleArray? = null

    @Setup
    fun setup() {
        model = GradientBenchmark.buildLargeTestModel(10000)
        modelTF = GradientBenchmark.buildLargeTestModelTF(10000)
        vars = DoubleArray(model!!.nVars) { 1.1 }
    }

    @Benchmark
    fun psoBench(bh: Blackhole) {
        val objective = { x: DoubleArray -> model!!.evaluate(x) }
        val x = PSO.run(model!!.nVars, objective, iterations = 10)
        bh.consume(x)
    }

    @Benchmark
    fun psoBenchTF(bh: Blackhole) {
        val objective = { x: DoubleArray -> modelTF!!.evaluate(x) }
        val x = PSO.run(modelTF!!.nVars, objective, iterations = 10)
        bh.consume(x)
    }
}
