package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

/**
 * Leaf Term. Constant Value.
 */
class Constant(
    override val nVars: Int,
    private val value: Double = 0.0
) : Term {
    override var visited = ThreadLocal<Boolean>()

    override fun gradientReverse(vals: DoubleArray, partials: DoubleArray, seed: Double) { }

    override fun gradientForward(variable: Int, vals: DoubleArray) : Double {
        return 0.0
    }

    override fun evaluate(vals: DoubleArray) : Double {
        return value
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