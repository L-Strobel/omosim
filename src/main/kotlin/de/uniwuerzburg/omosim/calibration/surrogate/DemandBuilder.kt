package de.uniwuerzburg.omosim.calibration.surrogate

import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray

/**
 * Builds demand terms that together comprise the expected origin-destination matrix.
 */
interface DemandBuilder<M_IN, M_OUT> {
    fun shape(m: M_OUT): Pair<Int, Int>

    fun add(
        accMatrix: M_OUT,
        other: NDArray<Double, D2>,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ) : M_OUT

    fun add(
        accMatrix: M_OUT,
        other: M_OUT,
        mCoeff: NDArray<Double, D2>,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ) : M_OUT

    fun diagMultAdd(
        accMatrix: M_OUT,
        v: M_OUT,
        mCoeff: NDArray<Double, D2>,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ) : M_OUT

    fun matrixMult(
        left: D2Array<Double>,
        vMatrix: M_IN,
        transpose: Boolean,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ) : M_OUT

    fun matrixMult(
        vMatrix: M_IN,
        right: D2Array<Double>,
        transpose: Boolean,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ) : M_OUT

    /**
     * Creates the terms that compute the following matrix multiplication:
     * L * X * R
     *
     * Where
     * L: left constant matrix
     * X: matrix filled with variable terms
     * R: right constant matrix
     *
     * @param left L
     * @param x matrix filled with variable terms
     * @param right R
     * @param transpose if true computes: L * X^T * R useful for the LIVE=EVIL rule.
     * @param relevantRCs if not null specifies which rows and columns of the result are relevant.
     * Not included columns are ignored.
     * @param cTol All terms with coefficients below this value will be ignored and not added to the result.
     */
    fun matrixMult(
        left: D2Array<Double>,
        x: M_IN,
        right: D2Array<Double>,
        transpose: Boolean,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ) : M_OUT
}