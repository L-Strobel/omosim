package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

import kotlin.math.exp

class ExponentialTerm(
    nVars: Int,
    private val exponent: Term
): BranchTerm(nVars) {

    init {
        children.add(exponent)
    }

    override fun function(vals: DoubleArray): Double {
        return exp(exponent.evaluate(vals))
    }

    override fun gradient(variable: Int, vals: DoubleArray): Double {
       return exponent.gradientForward(variable, vals) * exp(exponent.evaluate(vals))
    }

    override fun backpropagate(vals: DoubleArray, partials: DoubleArray, adjoint: Double) {
        exponent.gradientReverse(vals, partials, adjoint * exp(exponent.evaluate(vals)))
    }
}