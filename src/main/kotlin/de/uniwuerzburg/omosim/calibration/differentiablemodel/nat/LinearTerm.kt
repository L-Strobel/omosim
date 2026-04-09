package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

class LinearTerm(
    nVars: Int
): BranchTerm(nVars) {
    private val coefficients = mutableListOf<Double>()
    private var intercept = 0.0

    fun addTerm(term: Term, coefficient: Double) {
        children.add(term)
        coefficients.add(coefficient)
    }

    fun addConstant(value: Double) {
        intercept += value
    }

    override fun function(vals: DoubleArray): Double {
        var y = intercept
        for (i in children.indices) {
            y += children[i].evaluate(vals) * coefficients[i]
        }
        return y
    }

    override fun gradient(variable: Int, vals: DoubleArray): Double {
        var g = 0.0
        for (i in children.indices) {
            g += children[i].gradientForward(variable, vals) * coefficients[i]
        }
        return g
    }

    override fun backpropagate(vals: DoubleArray, partials: DoubleArray, adjoint: Double) {
        for (i in children.indices) {
            children[i].gradientReverse(vals, partials, adjoint * coefficients[i])
        }
    }
}