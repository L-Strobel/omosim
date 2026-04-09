package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

class QuadraticTerm(
    nVars: Int,
    private val termA: Term,
    private val termB: Term,
    private val coefficient: Double,
): BranchTerm(nVars) {
    init {
        children.add(termA)
        children.add(termB)
    }

    override fun function(vals: DoubleArray): Double {
        return  termA.evaluate(vals) * termB.evaluate(vals) * coefficient
    }

    override fun gradient(variable: Int, vals: DoubleArray): Double {
        return termA.evaluate(vals) * termB.gradientForward(variable, vals) * coefficient +
               termB.evaluate(vals) * termA.gradientForward(variable, vals) * coefficient
    }

    override fun backpropagate(vals: DoubleArray, partials: DoubleArray, adjoint: Double) {
        termA.gradientReverse(vals, partials, adjoint * termB.evaluate(vals) * coefficient)
        termB.gradientReverse(vals, partials, adjoint * termA.evaluate(vals) * coefficient)
    }
}