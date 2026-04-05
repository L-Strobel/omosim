package de.uniwuerzburg.omosim.calibration.differentiablemodel

import de.uniwuerzburg.omosim.calibration.CalibrationAlgorithm
import de.uniwuerzburg.omosim.calibration.algorithms.BFGS
import de.uniwuerzburg.omosim.calibration.algorithms.GradientDescent
import de.uniwuerzburg.omosim.calibration.algorithms.PSO
import de.uniwuerzburg.omosim.calibration.algorithms.SPSA
import de.uniwuerzburg.omosim.calibration.logger
import smile.util.function.DifferentiableMultivariateFunction


/**
 * Warning: Don't use within coroutines!! ThreadLocal cache will be unstable. Use an ExecutorService instead.
 */
class DifferentiableModelUVBase (
    val nVars: Int
) : DifferentiableMultivariateFunction, DifferentiableModel {
    private var root: Term = LinearBaseTerm(nVars)
    private var visited = ThreadLocal<Boolean>()

    fun setRootTerm(term: Term) {
        root = term

        // Determine value receivers for Reverse mode
        clearReceivers()
        countReceivers()
    }

    fun gradientReverse(vals: DoubleArray, partials: DoubleArray, seed: Double) {
        root.gradientReverse(vals, partials, seed)
        clearGradientCache()
        clearEvalCache()
    }

    fun gradientForward(variable: Int, vals: DoubleArray): Double {
        val result = root.gradientForward(variable, vals)
        clearGradientCache()
        clearEvalCache()
        return result
    }

    fun evaluate(vals: DoubleArray): Double {
        val result = root.evaluate(vals)
        clearEvalCache() // Safer, but slows down reverse mode a bit.
        return result
    }

    fun clearEvalCache() {
        root.clearEvalCache()
        clearSearchMarkers()
    }

    fun clearGradientCache() {
        root.clearGradientCache()
        clearSearchMarkers()
    }

    fun countReceivers() {
        root.countReceivers()
        clearSearchMarkers()
    }

    fun clearReceivers() {
        root.clearReceivers()
        clearSearchMarkers()
    }

    // SMILE interface
    override fun f(p0: DoubleArray?): Double {
        return evaluate(p0!!)
    }

    override fun g(x: DoubleArray?, gradient: DoubleArray?): Double {
        val result = evaluate(x!!)
        gradientReverse(x, gradient!!, 1.0)
        return result
    }

    fun clearSearchMarkers() {
        visited.set(false)
        root.clearSearchMarkers()
    }

    override fun visit(visitor: (term: Term) -> Unit) {
        root.visit(visitor)
        clearSearchMarkers()
    }

    /**
     * Optimize the Differentiable Model.
     *
     * @param algorithm Options: SM_LBFGS, SM_GD, SM_PSO, PSO, SM_SPSA, SPSA
     * @param parameters Parameters for key:value options see de.uniwuerzburg.omosim.calibration.algorithms
     * @param x0 Starting value of variables
     * @param nWorker Number of parallel threads for optimization
     */
    fun optimizeWith(
        algorithm: CalibrationAlgorithm?,
        parameters: Map<String,String>,
        x0: DoubleArray? = null,
        nWorker: Int? = null
    ) : DoubleArray {
        val x = when (algorithm) {
            CalibrationAlgorithm.SM_LBFGS -> {
                val x0Fallback = getX0(parameters)
                if (x0 == null) {
                    logger.warn("x0 not supplied to LBFGS. Running with x0=${x0Fallback.toList()}")
                }
                BFGS.run(this, x0 ?: x0Fallback, parameters)
            }
            CalibrationAlgorithm.SM_GD -> {
                val x0Fallback = getX0(parameters)
                if (x0 == null) {
                    logger.warn("x0 not supplied to Gradient Descent. Running with x0=${x0Fallback.toList()}")
                }
                GradientDescent.run(this, x0 ?: x0Fallback, parameters)
            }
            CalibrationAlgorithm.SM_PSO, CalibrationAlgorithm.PSO -> {
                val objective = { x: DoubleArray -> this.evaluate(x) }
                PSO.run(this.nVars, objective, nWorker, parameters)
            }
            CalibrationAlgorithm.SM_SPSA, CalibrationAlgorithm.SPSA -> {
                val x0Fallback = getX0(parameters)
                if (x0 == null) {
                    logger.warn("x0 not supplied to SPSA. Running with x0=${x0Fallback.toList()}")
                }
                val objective = { x: DoubleArray -> this.evaluate(x) }
                SPSA.run(x0 ?: x0Fallback, objective, parameters = parameters)
            }

            else -> throw IllegalArgumentException(
                "Algorithm ${algorithm?.name} can not be used to solve a generic differentiable model."
            )
        }
        return x
    }

    /**
     * Get starting value for x based on the specified bounds.
     */
    private fun getX0(parameters: Map<String, String>) : DoubleArray {
        val lb = parameters["lb"]?.toDoubleOrNull()
        val ub = parameters["ub"]?.toDoubleOrNull()
        val x0Val = if (( lb != null ) and (ub != null)) {
            (ub!! - lb!!) / 2.0
        } else if (ub != null) {
            ub - 1.0
        } else if (lb != null) {
            lb + 1.0
        } else {
            1.0
        }
        return DoubleArray(this.nVars) { x0Val }
    }
}