package de.uniwuerzburg.omosim.calibration.algorithms

import java.util.*
import kotlin.math.pow
import kotlin.time.measureTime

object SPSA {
    private const val NAME = "SPSA"

    object Defaults {
        const val iterations = 2000
        const val lb = 1e-3
        const val ub = 1e3
        const val a0 = 10.0
        const val c0 = 10.0
        const val A = 0.0
        const val gamma = 0.1
        const val alpha = 0.6
    }

    fun run(
        x0: DoubleArray,
        objective: (DoubleArray) -> Double,
        rng: Random = Random(),
        parameters: Map<String, String>? = null,
    ) : DoubleArray {
        return run(
            x0 = x0,
            objective = objective,
            rng = rng,
            iterations = parameters?.get("iterations")?.toIntOrNull() ?: Defaults.iterations,
            lb = parameters?.get("lb")?.toDoubleOrNull() ?: Defaults.lb,
            ub = parameters?.get("ub")?.toDoubleOrNull() ?: Defaults.ub,
            a0 = parameters?.get("a0")?.toDoubleOrNull() ?: Defaults.a0,
            c0 = parameters?.get("c0")?.toDoubleOrNull() ?: Defaults.c0,
            A = parameters?.get("A")?.toDoubleOrNull() ?: Defaults.A,
            gamma = parameters?.get("gamma")?.toDoubleOrNull() ?: Defaults.gamma,
            alpha = parameters?.get("alpha")?.toDoubleOrNull() ?: Defaults.alpha,
        )
    }

    fun run(
        x0: DoubleArray,
        objective: (DoubleArray) -> Double,
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
        ProgressLogger.logParameters(this.NAME, "lb=$lb:ub$ub:ao=$a0:c0=$c0:A=$A:gamma=$gamma:alpha=$alpha")

        val x = x0.copyOf()
        var bestX = x0.copyOf()
        var bestLoss = objective(x0)
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
                val jPlus = objective(xPlus)

                // Minus
                val xMinus = DoubleArray(x0.size) { j -> x[j] - c * perturbation[j] }
                xMinus.project(lb, ub)
                val jMinus = objective(xMinus)

                // Gradient estimate
                val grad = DoubleArray(x0.size) { j -> (jPlus - jMinus) / (2 * c * perturbation[j])}

                // Step
                for (j in x.indices) {
                    x[j] -= a*grad[j]
                }

                x.project(lb, ub) // Bound Projection
            }
            val loss = objective(x)

            if (loss < bestLoss) {
                bestX = x.copyOf()
                bestLoss = loss
            }

            ProgressLogger.logProgress(this.NAME, i, time, bestLoss, loss)
        }
        ProgressLogger.logFinalLoss(this.NAME, bestLoss)
        return bestX
    }
}