package de.uniwuerzburg.omosim.calibration.algorithms

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelMultiOut
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.measureTime

object WSPSA {
    private const val NAME = "WSPSA"

    object Defaults {
        const val iterations = 10000
        const val lb = 1e-3
        const val ub = 1e3
        const val a0 = 300.0
        const val c0 = 100.0
        const val A = 0.0
        const val gamma = 0.1
        const val alpha = 0.6
    }

    fun run(
        x0: DoubleArray,
        objective:  (DoubleArray) -> Pair<Double, DoubleArray>,
        measurements: List<Double>,
        model: DifferentiableModelMultiOut,
        rng: Random = Random(),
        parameters: Map<String, String>? = null,
    ) : DoubleArray {
        return run(
            x0 = x0,
            objective = objective,
            measurements = measurements,
            model = model,
            rng = rng,
            iterations = parameters?.get("iterations")?.toIntOrNull() ?: Defaults.iterations,
            lb = parameters?.get("lb")?.toDoubleOrNull() ?: Defaults.lb,
            ub = parameters?.get("ub")?.toDoubleOrNull() ?: Defaults.ub,
            a0 = parameters?.get("a0")?.toDoubleOrNull() ?: Defaults.a0,
            c0 = parameters?.get("c0")?.toDoubleOrNull() ?: Defaults.c0,
            A = parameters?.get("A")?.toDoubleOrNull() ?: Defaults.A,
            gamma = parameters?.get("gamma")?.toDoubleOrNull() ?: Defaults.gamma,
            alpha = parameters?.get("alpha")?.toDoubleOrNull() ?: Defaults.alpha
        )
    }

    fun run(
        x0: DoubleArray,
        objective: (DoubleArray) -> Pair<Double, DoubleArray>,
        measurements: List<Double>,
        model: DifferentiableModelMultiOut,
        rng: Random,
        iterations: Int = Defaults.iterations,
        lb: Double = Defaults.lb,
        ub: Double = Defaults.ub,
        a0: Double = Defaults.a0,
        c0: Double = Defaults.c0,
        A: Double = Defaults.A,
        gamma: Double = Defaults.gamma,
        alpha: Double = Defaults.alpha
    ) : DoubleArray {
        ProgressLogger.logParameters(this.NAME,"lb=$lb:ub$ub:ao=$a0:c0=$c0:A=$A:gamma=$gamma:alpha=$alpha")

        val m = measurements.toDoubleArray()
        val x = x0.copyOf()
        var bestX = x0.copyOf()
        var (bestLoss, _) = objective(x0)
        ProgressLogger.logInitialLoss(this.NAME, bestLoss)

        ProgressLogger.logProgressHeader()
        for (i in 0 until iterations) {
            val time = measureTime {
                val a = a0 / (A + i + 1).pow(alpha)
                val c = c0 / (i + 1).toDouble().pow(gamma)
                val perturbation = DoubleArray(x0.size) { (rng.nextInt(0, 2) * 2 - 1).toDouble() }

                // Plus
                val xPlus = DoubleArray(x0.size) { j -> x[j] + c * perturbation[j] }
                xPlus.project(lb, ub)
                val jPlus = squareErrors(xPlus, objective, m)

                // Minus
                val xMinus = DoubleArray(x0.size) { j -> x[j] - c * perturbation[j] }
                xMinus.project(lb, ub)
                val jMinus = squareErrors(xMinus, objective, m)

                // Jacobian
                val jac = model.jacobian(x)

                // Normalize
                for (j in measurements.indices) {
                    for (k in x0.indices) {
                        jac[j][k] = abs( jac[j][k] )
                    }
                }

                // Gradient estimate
                val grad = gradient(jPlus, jMinus, jac, c, perturbation)

                // Step
                for (j in x.indices) {
                    x[j] -= a*grad[j]
                }

                // Bound Projection
                x.project(lb, ub)
            }
            val (loss, _) = objective(x)

            if (loss < bestLoss) {
                bestX = x.copyOf()
                bestLoss = loss
            }

            ProgressLogger.logProgress(this.NAME, i, time, loss)
        }
        ProgressLogger.logFinalLoss(this.NAME, bestLoss)
        return bestX
    }

    private fun squareErrors(
        x: DoubleArray, objective: (DoubleArray) -> Pair<Double, DoubleArray>, m: DoubleArray
    ) : DoubleArray {
        val (_, s) = objective(x)
        val se = DoubleArray(s.size) { 0.0 }
        for (i in s.indices) {
            se[i] = (m[i] - s[i]).pow(2)
        }
        return se
    }

    private fun gradient(
        jPlus: DoubleArray,
        jMinus: DoubleArray,
        wMatrix: Array<DoubleArray>,
        c: Double,
        perturbation: DoubleArray,
    ) : DoubleArray {
        val nVars = wMatrix.first().size
        val nMeasures = wMatrix.size
        val g = DoubleArray ( nVars ) { 0.0 }

        val jDiff = DoubleArray ( nMeasures ) { 0.0 }
        for (j in 0 until nMeasures) {
            jDiff[j] = (jPlus[j] - jMinus[j])
        }

        for (i in 0 until nVars) {
            for (j in 0 until nMeasures) {
                g[i] += wMatrix[j][i] * jDiff[j] / (2 * c * perturbation[i])
            }
        }
        return g
    }
}