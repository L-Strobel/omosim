package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

/**
 * Leaf Term. Variable value.
 */
class Variable(
    override val nVars: Int,
    val id: Int,
    private val coefficient: Double
) : Term {
    override var visited = ThreadLocal<Boolean>()

    override fun gradientReverse(vals: DoubleArray, partials: DoubleArray, seed: Double) {
        partials[id] += seed * coefficient
    }

    override fun gradientForward(variable: Int, vals: DoubleArray) : Double {
        return coefficient
    }

    override fun evaluate(vals: DoubleArray) : Double {
        return coefficient * vals[id]
    }

    override fun clearEvalCache() { }

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