package de.uniwuerzburg.omosim.calibration.differentiablemodel

import kotlin.math.pow

class PowerTerm(
    nVars: Int,
    private val base: Term,
    private val power: Double
): BranchTerm(nVars) {
    init {
        children.add(base)
    }

    override fun function(vals: DoubleArray): Double {
        return base.evaluate(vals).pow(power)
    }

    override fun gradient(variable: Int, vals: DoubleArray): Double {
        return power * base.evaluate(vals).pow(power-1) * base.gradientForward(variable, vals)
    }

    override fun backpropagate(vals: DoubleArray, partials: DoubleArray, adjoint: Double) {
        base.gradientReverse(vals, partials, adjoint * power * base.evaluate(vals).pow(power-1))
    }
}