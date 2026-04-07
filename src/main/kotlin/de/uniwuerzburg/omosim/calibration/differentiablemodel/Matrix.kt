package de.uniwuerzburg.omosim.calibration.differentiablemodel

import org.tensorflow.Operand
import org.tensorflow.op.Ops
import org.tensorflow.types.TFloat32


open class Matrix<T>(
    val matrix: List<List<T>>
) {
    open fun get(r: Int, c: Int): T {
        return matrix[r][c]
    }

    open fun shape() : Pair<Int, Int> {
        var nrows = matrix.size
        var ncols = if (nrows == 0) 0 else matrix[0].size
        return Pair(nrows, ncols)
    }
}

class MatrixTF(
    val tf: Ops,
    val matrixT: Operand<TFloat32>
) : Matrix<Operand<TFloat32>>(listOf()) {
    override fun get(r: Int, c: Int): Operand<TFloat32> {
        val indices = tf.constant(arrayOf(longArrayOf(r.toLong(), c.toLong())))
        val entry = tf.gatherNd(matrixT, indices)
        return tf.squeeze(entry)
    }

    override fun shape() : Pair<Int, Int> {
        val shape = matrixT.asOutput().shape()
        val dims = shape.asArray()
        val nrows = dims[0].toInt()
        val ncols = dims[1].toInt()
        return Pair(nrows, ncols)
    }
}

