package de.uniwuerzburg.omosim.calibration.differentiablemodel

/**
 * Leaf Term. Linear with multiple variables.
 */
class LinearBaseTerm(
    override val nVars: Int
): Term {
    private val coefficients = DoubleArray(nVars) {0.0}
    private var intercept = 0.0
    private var evalCache = ThreadLocal<Double>()
    override var visited = ThreadLocal<Boolean>()

    fun addTerm(variable: Int, coefficient: Double) {
        coefficients[variable] += coefficient
    }

    fun addConstant(value: Double) {
        intercept += value
    }

    override fun gradientReverse(vals: DoubleArray, partials: DoubleArray, seed: Double) {
        for (i in partials.indices) {
            partials[i] += seed * coefficients[i]
        }
    }

    override fun gradientForward(variable: Int, vals: DoubleArray) : Double {
        return coefficients[variable]
    }

    override fun evaluate(vals: DoubleArray) : Double {
        if (evalCache.get() != null) {
            return evalCache.get()
        }
        var result = intercept
        for (i in coefficients.indices) {
            result += coefficients[i] * vals[i]
        }
        evalCache.set(result)
        return result
    }

    override fun clearEvalCache() {
        evalCache.set(null)
    }

    override fun clearGradientCache() { }

    override fun countReceivers() { }

    override fun clearReceivers() { }

    override fun clearSearchMarkers() { }

    override fun visit(visitor: (term: Term) -> Unit) {
        if ((visited.get() == null) || (visited.get() == false)) {
            visited.set(true)
            visitor(this)
        }
    }
}