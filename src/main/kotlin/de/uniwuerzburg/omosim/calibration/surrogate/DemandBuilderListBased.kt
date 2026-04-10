package de.uniwuerzburg.omosim.calibration.surrogate

import org.jetbrains.kotlinx.multik.api.identity
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.max
import kotlin.math.abs

abstract class DemandBuilderListBased<V, ACC>: DemandBuilder<List<List<V>>, List<List<ACC>>> {

    abstract fun addVar(term: ACC, v: V, coefficient: Double)
    abstract fun addConstant(term: ACC, constant: Double)
    abstract fun addTerm(term: ACC, other: ACC, coefficient: Double)
    abstract fun new(): ACC

    override fun add(
        accMatrix: List<List<ACC>>,
        other: NDArray<Double, D2>,
        relevantRCs: Set<Pair<Int, Int>>?
    ): List<List<ACC>> {
        for (o in 0 until accMatrix.size) {
            for (d in 0 until accMatrix[o].size) {
                if ((relevantRCs != null) && (Pair(o, d) !in relevantRCs)) {
                    continue
                }
                addConstant(accMatrix[o][d], other[o, d])
            }
        }
        return accMatrix
    }

    override fun add(
        accMatrix: List<List<ACC>>,
        other: List<List<ACC>>,
        mCoeff: NDArray<Double, D2>,
        relevantRCs: Set<Pair<Int, Int>>?
    ): List<List<ACC>> {
        for (o in 0 until accMatrix.size) {
            for (d in 0 until accMatrix[o].size) {
                if ((relevantRCs != null) && (Pair(o, d) !in relevantRCs)) {
                    continue
                }
                addTerm(accMatrix[o][d], other[o][d], mCoeff[o, d])
            }
        }
        return accMatrix
    }

    override fun diagMultAdd(
        accMatrix: List<List<ACC>>,
        v: List<List<ACC>>,
        mCoeff: NDArray<Double, D2>,
        relevantRCs: Set<Pair<Int, Int>>?
    ): List<List<ACC>> {
        for (o in 0 until accMatrix.size) {
            for (d in 0 until accMatrix[o].size) {
                if ((relevantRCs != null) && (Pair(o, d) !in relevantRCs)) {
                    continue
                }
                addTerm(accMatrix[o][d], v[0][o], mCoeff[o, d])
            }
        }
        return accMatrix
    }

    override fun matrixMult(
        left: D2Array<Double>,
        vMatrix: List<List<V>>,
        transpose: Boolean,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ): List<List<ACC>> {
        val right = mk.identity<Double>(vMatrix.size)
        return matrixMult(left, vMatrix, right, transpose, relevantRCs, cTol)
    }

    override fun matrixMult(
        vMatrix: List<List<V>>,
        right: D2Array<Double>,
        transpose: Boolean,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ): List<List<ACC>> {
        val left = mk.identity<Double>(vMatrix.size)
        return matrixMult(left, vMatrix, right, transpose, relevantRCs, cTol)
    }

    /**
     * Creates the terms that compute the following matrix multiplication:
     * L * X * R
     *
     * Where
     * L: left constant matrix
     * X: matrix filled with variable terms
     * R: right constant matrix
     *
     * @param x matrix filled with variable terms
     * @param left L
     * @param right R
     * @param transpose if true computes: L * X^T * R useful for the LIVE=EVIL rule.
     * @param relevantRCs if not null specifies which rows and columns of the result are relevant.
     * Not included columns are ignored.
     * @param cTol All terms with coefficients below this value will be ignored and not added to the result.
     */
    override fun matrixMult(
        left: D2Array<Double>,
        x: List<List<V>>,
        right: D2Array<Double>,
        transpose: Boolean,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ) : List<List<ACC>> {
        // Dimensions
        val lRow = left.shape[0]
        val lCol = left.shape[1]
        val rRow = right.shape[0]
        val rCol = right.shape[1]

        val leftMax = left.max()!! // For computation shortcut

        // Build empty result
        val result = List(lRow) {
            List(rCol) {
                this.new()
            }
        }

        // Build terms of matrix multiplication
        for (row in 0 until lRow) {
            for (col in 0 until rCol) {
                // Ignored entry?
                if (relevantRCs != null) {
                    if (Pair(row, col) !in relevantRCs) {
                        continue
                    }
                }

                val activeEntry = result[row][col]
                for (i in 0 until rRow) {
                    if (right[i, col] * leftMax <= cTol) {
                        continue
                    }
                    for (j in 0 until lCol) {
                        val coeff = left[row, j] * right[i, col]

                        // Coefficient relevant?
                        if (abs(coeff) <= cTol) {
                            continue
                        }

                        // Add to result
                        if (transpose) {
                            this.addVar(activeEntry, x[i][j], coeff)
                        } else {
                            this.addVar(activeEntry, x[j][i], coeff)
                        }
                    }
                }
            }
        }
        return result
    }

    override fun shape(m: List<List<ACC>>): Pair<Int, Int> {
        val nrows = m.size
        val ncols = if (nrows == 0) 0 else m[0].size
        return nrows to ncols
    }
}