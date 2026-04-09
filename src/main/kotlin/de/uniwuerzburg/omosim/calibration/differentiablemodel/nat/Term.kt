package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

interface Term {
    val nVars: Int
    var visited: ThreadLocal<Boolean>

    fun gradientForward(variable: Int, vals: DoubleArray) : Double
    fun evaluate(vals: DoubleArray) : Double
    fun clearEvalCache()
    fun clearGradientCache()
    fun gradientReverse(vals: DoubleArray, partials: DoubleArray, seed: Double)
    fun countReceivers()
    fun clearReceivers()
    fun clearSearchMarkers()
    fun visit(visitor: (term: Term) -> Unit)
}