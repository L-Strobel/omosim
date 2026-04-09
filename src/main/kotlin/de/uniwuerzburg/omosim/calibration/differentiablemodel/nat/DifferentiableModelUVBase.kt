package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

import smile.util.function.DifferentiableMultivariateFunction


/**
 * Warning: Don't use within coroutines!! ThreadLocal cache will be unstable. Use an ExecutorService instead.
 */
class DifferentiableModelUVBase (
    nVars: Int
) : DifferentiableMultivariateFunction, DifferentiableModelUV(nVars), DifferentiableModel {
    private var root: Term = LinearBaseTerm(nVars)
    private var visited = ThreadLocal<Boolean>()

    fun setRootTerm(term: Term) {
        root = term

        // Determine value receivers for Reverse mode
        clearReceivers()
        countReceivers()
    }

    override fun gradient(vals: DoubleArray, gradient: DoubleArray) : Double {
        return gradientReverse(vals, gradient, 1.0)
    }

    fun gradientReverse(vals: DoubleArray, partials: DoubleArray, seed: Double) : Double {
        root.gradientReverse(vals, partials, seed)
        val y = root.evaluate(vals)
        clearGradientCache()
        clearEvalCache()
        return y
    }

    fun gradientForward(variable: Int, vals: DoubleArray): Double {
        val result = root.gradientForward(variable, vals)
        clearGradientCache()
        clearEvalCache()
        return result
    }

    override fun evaluate(vals: DoubleArray): Double {
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

    private fun visit(visitor: (term: Term) -> Unit) {
        root.visit(visitor)
        clearSearchMarkers()
    }

    override fun getSize(): Int {
        var terms = 0
        this.visit { terms += 1 }
        return terms
    }
}