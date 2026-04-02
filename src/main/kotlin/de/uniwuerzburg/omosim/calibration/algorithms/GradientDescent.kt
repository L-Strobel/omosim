package de.uniwuerzburg.omosim.calibration.algorithms

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelSingleOut
import org.jetbrains.kotlinx.multik.api.stat.abs
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object GradientDescent {
    private const val NAME = "GradientDescent"

    object Defaults {
        const val iterations = 1000
        const val lr0 = 1.0e-8
        const val lb = 1e-3
        const val ub = 1e3
        val lTol: Double? = null
        val backTracking: Boolean = false
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
            lTol = parameters?.get("lTol")?.toDoubleOrNull() ?: Defaults.lTol,
            backTracking = parameters?.get("backTracking")?.toBoolean() ?: Defaults.backTracking,
        )
    }

    fun run(
        model: DifferentiableModelSingleOut,
        x0: DoubleArray,
        iterations: Int = Defaults.iterations,
        lr0: Double = Defaults.lr0,
        lb: Double = Defaults.lb,
        ub: Double = Defaults.ub,
        lTol: Double? = Defaults.lTol,
        backTracking: Boolean = Defaults.backTracking
    ) : DoubleArray {
        ProgressLogger.logParameters(this.NAME,"lr0=$lr0:lb=$lb:ub$ub")

        // Init
        var x = x0.copyOf()
        var bestX = x0.copyOf()
        var bestLoss = model.evaluate(x0)
        var lastLoss = bestLoss
        var nNoImprovement = 0
        var btMinStepSizeReached = false
        ProgressLogger.logInitialLoss(this.NAME, bestLoss)

        // Descent
        ProgressLogger.logProgressHeader()
        for (i in 0 until iterations) {
            val g = DoubleArray(x0.size) { 0.0 }
            val (loss, time) = measureTimedValue {
                // Compute Gradient
                model.gradientReverse(x, g, 1.0)

                // Evaluate
                var newX = updateX(x, g, lr0, lb, ub) // Gradient Step and Bound Handling
                var loss = model.evaluate(newX)

                // Backtracking
                if (backTracking) {
                    val tau = 0.1 // Learning rate shrinkage
                    val c = 1e-4  // Acceptable achieved fraction of linear improvement promised by the gradient
                    val minLr = 1e-10
                    val t = c * g.sumOf { it.pow(2) }

                    var lr = lr0
                    while(lastLoss - loss < lr*t) { // Armijo Condition
                        lr *= tau
                        newX = updateX(x, g, lr, lb, ub)
                        loss = model.evaluate(newX)

                        if (lr <= minLr) {
                            btMinStepSizeReached = true
                            break
                        }
                    }
                }

                x = newX
                loss
            }

            // Update best solution
            if (loss < bestLoss) {
                bestX = x.copyOf()
                bestLoss = loss
                nNoImprovement = 0
            } else {
                nNoImprovement += 1
            }
            ProgressLogger.logProgress(this.NAME, -1, time, loss) // TODO Remove. // TODO: Warn about jittering gradients
            ProgressLogger.logProgress(this.NAME, i, time, bestLoss)

            // Early Termination
            if (loss.isNaN()) {
                ProgressLogger.logEarlyTermination(
                    this.NAME, reason = "Loss is NaN"
                )
                break
            }
            if (loss.isInfinite()) {
                ProgressLogger.logEarlyTermination(
                    this.NAME, reason = "Loss is Infinite"
                )
                break
            }
            if (btMinStepSizeReached) {
                ProgressLogger.logEarlyTermination(
                    this.NAME, reason = "Reached minimum step size during backtracking."
                )
                break
            }
            if (nNoImprovement > 5) {
                ProgressLogger.logEarlyTermination(
                    this.NAME, reason = "No improvement in 5 iterations"
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

    fun updateX(x: DoubleArray, g: DoubleArray, lr: Double, lb: Double, ub: Double) : DoubleArray {
        val xCopy = x.copyOf()

        // Step
        for (j in x.indices) {
            xCopy[j] -= lr * g[j]
        }

        // Bound Projection
        xCopy.project(lb, ub)

        return xCopy
    }
}


