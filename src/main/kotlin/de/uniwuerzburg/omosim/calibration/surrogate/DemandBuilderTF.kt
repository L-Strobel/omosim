package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.utils.diagonal
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.operations.toArray
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

class DemandBuilderTF(
    val model: TfModel
) : DemandBuilder<Operand<TFloat32>, Operand<TFloat32>> {
    val tf = model.tf

    // Flip rows and columns. I.e., normal transpose
    val perm2dTranspose = tf.constant(
        intArrayOf(1, 0)
    )

    override fun add(
        accMatrix: Operand<TFloat32>,
        other: NDArray<Double, D2>,
        relevantRCs: Set<Pair<Int, Int>>?
    ): Operand<TFloat32> {
        val otherTF = model.addMatrix( other.toArray() )
        return tf.math.add(accMatrix, otherTF)
    }

    override fun add(
        accMatrix: Operand<TFloat32>,
        other: Operand<TFloat32>,
        mCoeff: NDArray<Double, D2>,
        relevantRCs: Set<Pair<Int, Int>>?
    ): Operand<TFloat32> {
        val mCoeffTF = model.addMatrix( mCoeff.toArray() )
        val otherMult = tf.math.mul(other, mCoeffTF)
        return tf.math.add(accMatrix, otherMult)
    }

    override fun diagMultAdd(
        accMatrix: Operand<TFloat32>,
        v: Operand<TFloat32>,
        mCoeff: NDArray<Double, D2>,
        relevantRCs: Set<Pair<Int, Int>>?
    ): Operand<TFloat32> {
        val mCoeffTF = model.addMatrix( mCoeff.toArray() )
        val vDiag = tf.linalg.tensorDiag( tf.squeeze( v ) )
        val diagMult = tf.linalg.matMul(vDiag, mCoeffTF)
        return tf.math.add(accMatrix, diagMult)
    }

    override fun matrixMult(
        left: D2Array<Double>,
        vMatrix: Operand<TFloat32>,
        transpose: Boolean,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ): Operand<TFloat32> {
        val x = if (transpose) {
            tf.linalg.transpose(vMatrix, perm2dTranspose)
        } else {
            vMatrix
        }
        val leftTF = model.addMatrix( left.toArray() )

        return tf.linalg.matMul(leftTF, x)
    }

    override fun matrixMult(
        vMatrix: Operand<TFloat32>,
        right: D2Array<Double>,
        transpose: Boolean,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ): Operand<TFloat32> {
        val x = if (transpose) {
            tf.linalg.transpose(vMatrix, perm2dTranspose)
        } else {
            vMatrix
        }
        val rightTF = model.addMatrix( right.toArray() )

        return tf.linalg.matMul(x, rightTF)
    }

    override fun matrixMult(
        left: D2Array<Double>,
        x: Operand<TFloat32>,
        right: D2Array<Double>,
        transpose: Boolean,
        relevantRCs: Set<Pair<Int, Int>>?,
        cTol: Double
    ): Operand<TFloat32> {
        val xT = if (transpose) {
            tf.linalg.transpose(x, perm2dTranspose)
        } else {
            x
        }
        val leftTF  = model.addMatrix( left.toArray() )
        val rightTF = model.addMatrix( right.toArray() )

        val lm = tf.linalg.matMul(leftTF, xT)
        return tf.linalg.matMul(lm, rightTF)
    }

    override fun shape(m: Operand<TFloat32>): Pair<Int, Int> {
        val shape = m.asOutput().shape()
        val dims = shape.asArray()
        val nrows = dims[0].toInt()
        val ncols = dims[1].toInt()
        return nrows to ncols
    }
}