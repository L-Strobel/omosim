package de.uniwuerzburg.omosim.utils

import kotlin.math.abs

/**
 * Set values abs(x) < eps to zero
 */
fun cleanMatrix(matrix: Array<DoubleArray>, eps: Double = 1e-13) {
    for (i in matrix.indices) {
        val row = matrix[i]
        for (j in row.indices) {
            if (abs(matrix[i][j]) < eps) {
                matrix[i][j] = 0.0
            }
        }
    }
}