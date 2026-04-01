package de.uniwuerzburg.omosim.calibration.algorithms

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelSingleOut
import org.jetbrains.kotlinx.multik.api.stat.abs
import kotlin.math.abs
import kotlin.time.measureTime

object GradientDescent {
    private const val NAME = "GradientDescent"

    object Defaults {
        const val iterations = 1000
        const val lr0 = 1.0e-8
        const val lb = 1e-3
        const val ub = 1e3
        val lTol: Double? = null
    }

    fun run(
        model: DifferentiableModelSingleOut,
        x0: DoubleArray,
        parameters: Map<String, String>? = null
    ) : DoubleArray {
        return run(
            model,
            x0,
            iterations = parameters?.get("iterations")?.toIntOrNull() ?: Defaults.iterations,
            lr0 = parameters?.get("lr0")?.toDoubleOrNull() ?: Defaults.lr0,
            lb = parameters?.get("lb")?.toDoubleOrNull() ?: Defaults.lb,
            ub = parameters?.get("ub")?.toDoubleOrNull() ?: Defaults.ub,
            lTol = parameters?.get("lTol")?.toDoubleOrNull() ?: Defaults.lTol
        )
    }

    fun run(
        model: DifferentiableModelSingleOut,
        x0: DoubleArray,
        iterations: Int = Defaults.iterations,
        lr0: Double = Defaults.lr0,
        lb: Double = Defaults.lb,
        ub: Double = Defaults.ub,
        lTol: Double? = Defaults.lTol
    ) : DoubleArray {
        ProgressLogger.logParameters(this.NAME,"lr0=$lr0:lb=$lb:ub$ub")

        // Init
        val lr = lr0
        val x = x0.copyOf()
        var bestX = x0.copyOf()
        var bestLoss = model.evaluate(x0)
        var lastLoss = bestLoss
        ProgressLogger.logInitialLoss(this.NAME, bestLoss)

        // Descent
        ProgressLogger.logProgressHeader()
        for (i in 0 until iterations) {
            val g = DoubleArray(x0.size) { 0.0 }
            val time = measureTime {
                // Compute Gradient
                model.gradientReverse(x, g, 1.0)

                // Step
                for (j in x.indices) {
                    x[j] -= lr * g[j]
                }

                // Bound Projection
                x.project(lb, ub)
            }
            // TODO Check for NaNs

            // Evaluate
            val loss = model.evaluate(x)
            if (loss < bestLoss) {
                bestX = x.copyOf()
                bestLoss = loss
            }
            ProgressLogger.logProgress(this.NAME, i, time, bestLoss)

            // Early Termination
            if (loss.isNaN()) {
                ProgressLogger.logEarlyTermination(
                    this.NAME, reason = "Loss is NaN"
                )
                break
            }
            if (lTol != null) {
                if (abs(loss - lastLoss) < lTol) {
                    ProgressLogger.logEarlyTermination(
                        this.NAME,reason = "Loss change below lTol (${lTol})"
                    )
                    break
                }
            }
            lastLoss = loss
        }
        ProgressLogger.logFinalLoss(this.NAME, bestLoss)
        return bestX
    }
}


