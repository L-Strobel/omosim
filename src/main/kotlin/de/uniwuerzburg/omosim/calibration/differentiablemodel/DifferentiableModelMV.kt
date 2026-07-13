package de.uniwuerzburg.omosim.calibration.differentiablemodel

/**
 * Multivariate differentiable model.
 * Used for W-SPSA algorithm.
 *
 * Implementations:
 * - TensorFlow: TfModelMV
 * - Native: NativeModelMV
 */
interface DifferentiableModelMV : DifferentiableModel {
    fun jacobian(vals: DoubleArray, nWorker: Int? = null) : Array<DoubleArray>
    fun evaluate(vals: DoubleArray): DoubleArray
}