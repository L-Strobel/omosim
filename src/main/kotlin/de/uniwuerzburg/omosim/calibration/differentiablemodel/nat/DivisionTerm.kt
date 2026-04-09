package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

import kotlin.math.pow

class DivisionTerm(
    nVars: Int,
    private val dividend: Term,
    private val divisor: Term
): BranchTerm(nVars) {

    init {
        children.add(divisor)
        children.add(dividend)
    }

    override fun function(vals: DoubleArray): Double {
       return dividend.evaluate(vals) / divisor.evaluate(vals)
    }

    override fun gradient(variable: Int, vals: DoubleArray): Double {
        val divisorEval = divisor.evaluate(vals)
        return (dividend.gradientForward(variable, vals) * divisorEval -
                dividend.evaluate(vals) * divisor.gradientForward(variable, vals)) / (divisorEval * divisorEval)
    }

    override fun backpropagate(vals: DoubleArray, partials: DoubleArray, adjoint: Double) {
        dividend.gradientReverse(vals, partials, adjoint / divisor.evaluate(vals))
        divisor.gradientReverse(vals, partials, adjoint * dividend.evaluate(vals) / - divisor.evaluate(vals).pow(2.0))
    }
}