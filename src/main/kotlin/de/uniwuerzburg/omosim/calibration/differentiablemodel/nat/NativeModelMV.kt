package de.uniwuerzburg.omosim.calibration.differentiablemodel.nat

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelMV
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Warning: Don't use within coroutines!! ThreadLocal cache will be unstable. Use an ExecutorService instead.
 */
class NativeModelMV (
    val nVars: Int
) : DifferentiableModelMV {
    private var roots: List<Term> = listOf()

    fun setRootTerms(terms: List<Term>) {
        roots = terms
    }

    override fun jacobian(vals: DoubleArray, nWorker: Int?) : Array<DoubleArray> {
        val executor = if (nWorker == null)
            Executors.newWorkStealingPool()
        else {
            Executors.newWorkStealingPool(nWorker)
        }

        val jac = Array(roots.size) { DoubleArray(nVars) { 0.0 } }
        for ((i, root) in roots.withIndex()) {
            executor.submit {
                root.clearReceivers()
                root.clearSearchMarkers()
                root.countReceivers()
                root.clearSearchMarkers()
                root.gradientReverse(vals, jac[i], 1.0)
                clearGradientCache()
                clearEvalCache()
            }
        }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.HOURS) // Wait as long as necessary.
        return jac
    }

    override fun evaluate(vals: DoubleArray): DoubleArray {
        val result = DoubleArray(roots.size) { 0.0 }
        for ((i, root) in roots.withIndex()) {
            result[i] = root.evaluate(vals)
        }
        clearEvalCache()
        return result
    }

    private fun clearEvalCache() {
        for (root in roots) {
            root.clearEvalCache()
        }
        clearSearchMarkers()
    }

    private fun clearGradientCache() {
        for (root in roots) {
            root.clearGradientCache()
        }
        clearSearchMarkers()
    }

    private fun clearSearchMarkers() {
        for (root in roots) {
            root.clearSearchMarkers()
        }
    }

    private fun visit(visitor: (term: Term) -> Unit) {
        for (root in roots) {
            root.visit(visitor)
        }
        clearSearchMarkers()
    }

    override fun getSize(): Int {
        var terms = 0
        this.visit { terms += 1 }
        return terms
    }
}