package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

abstract class BranchTerm(
    override val nVars: Int
) : Term {
    val children = mutableListOf<Term>()
    private var evalCache = ThreadLocal<Double>()
    private var gradientCache = ThreadLocal<Double>()
    private var nReceivers = ThreadLocal<Int>()
    private var received = ThreadLocal<Int>()
    private var adjoint = ThreadLocal<Double>()
    override var visited = ThreadLocal<Boolean>()

    abstract fun function(vals: DoubleArray): Double
    abstract fun backpropagate(vals: DoubleArray, partials: DoubleArray, adjoint: Double)
    abstract fun gradient(variable: Int, vals: DoubleArray) : Double

    override fun gradientReverse(vals: DoubleArray, partials: DoubleArray, seed: Double) {
        var adjoint = adjoint.get() ?: 0.0
        var received = received.get() ?: 0

        // Accumulate adjoint variable
        if (received != nReceivers.get()) {
            adjoint += seed
            received += 1

            this.adjoint.set( adjoint)
            this.received.set( received)
        }

        // If finalized continue
        if (received == nReceivers.get()) {
            backpropagate(vals, partials, adjoint)
        }
    }

    override fun gradientForward(variable: Int, vals: DoubleArray) : Double {
        if (gradientCache.get() != null) {
            return gradientCache.get()
        }
        val g = gradient(variable, vals)
        gradientCache.set(g)
        return g
    }

    override fun evaluate(vals: DoubleArray) : Double {
        if (evalCache.get() != null) {
            return evalCache.get()
        }
        val y = function(vals)
        evalCache.set(y)
        return y
    }

    override fun clearEvalCache() {
        if ((visited.get() == null) || (visited.get() == false)) {
            visited.set(true)
            evalCache.set(null)
            for (term in children) {
                term.clearEvalCache()
            }
        }
    }

    override fun clearGradientCache() {
        if ((visited.get() == null) || (visited.get() == false)) {
            visited.set(true)
            received.set(0)
            adjoint.set(0.0)
            gradientCache.set(null)
            for (term in children) {
                term.clearGradientCache()
            }
        }
    }

    override fun countReceivers() {
        nReceivers.set( (nReceivers.get() ?: 0) + 1)
        if ((visited.get() == null) || (visited.get() == false)) {
            visited.set(true)
            for (term in children) {
                term.countReceivers()
            }
        }
    }

    override fun clearReceivers() {
        if ((visited.get() == null) || (visited.get() == false)) {
            visited.set(true)
            nReceivers.set(0)
            for (term in children) {
                term.clearReceivers()
            }
        }
    }

    override fun clearSearchMarkers() {
        if ((visited.get() == null) || (visited.get())) {
            visited.set(false)
            for (term in children) {
                term.clearSearchMarkers()
            }
        }
    }

    override fun visit(visitor: (term: Term) -> Unit) {
        if ((visited.get() == null) || (visited.get() == false)) {
            visited.set(true)
            visitor(this)
            for (term in children) {
                term.visit(visitor)
            }
        }
    }
}