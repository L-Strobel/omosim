package de.uniwuerzburg.omosim.utils.Distributions

import de.uniwuerzburg.omosim.utils.sampleNDGaussian
import de.uniwuerzburg.omosim.utils.sampleNDGaussianFast
import kotlinx.benchmark.Blackhole
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.CholeskyDecomposition
import org.apache.commons.math3.linear.RealMatrix
import org.jetbrains.kotlinx.multik.api.identity
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.set
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.toArray
import org.openjdk.jmh.annotations.*
import java.util.*
import java.util.concurrent.TimeUnit


class Gaussian (
    val means: DoubleArray,
    val covariances: Array<DoubleArray>,
    var l: RealMatrix
)

@BenchmarkMode(Mode.Throughput)
@Fork(value = 2)
@Warmup(iterations = 5)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class GaussianBenchmark {
    val gDimensions = 5
    val rng = Random()
    val gaussians = mutableListOf<Gaussian>()

    @Setup
    fun setup() {
        for (i in 0 until 100) {
            val means = DoubleArray(gDimensions) { rng.nextDouble() }

            // Create random covariance matrix
            val randomMatrix = mk.zeros<Double>(gDimensions, gDimensions) // Random seed
            for (j in 0 until gDimensions) {
                for (k in 0 until gDimensions) {
                    randomMatrix[j, k] = rng.nextDouble(-1.0, 1.0)
                }
            }
            var mkCovariances = randomMatrix.dot(randomMatrix.transpose()) // Ensure semi-definite
            mkCovariances += mk.identity<Double>(gDimensions) + 1e-4 // Ensure positive semi-definite
            val covariances = mkCovariances.toArray()

            val l = CholeskyDecomposition(Array2DRowRealMatrix(covariances), 0.1, 1.0E-10).l
            val gaussian = Gaussian(means, covariances, l)
            gaussians.add(gaussian)
        }
    }

    @Benchmark
    fun sampleNDGaussianBench(bh: Blackhole) {
        var acc = 0.0
        for (gaussian in gaussians) {
            for (i in 0 until 1000) {
                val sample = sampleNDGaussian(gaussian.means, gaussian.covariances, rng)
                for (j in sample.indices) {
                    acc += sample[j]
                }
            }
        }
        bh.consume(acc)
    }

    @Benchmark
    fun sampleNDGaussianBenchFast(bh: Blackhole) {
        var acc = 0.0
        for (gaussian in gaussians) {
            for (i in 0 until 1000) {
                val sample = sampleNDGaussianFast(gaussian.means, gaussian.l, rng)
                for (j in sample.indices) {
                    acc += sample[j]
                }
            }
        }
        bh.consume(acc)
    }
}
