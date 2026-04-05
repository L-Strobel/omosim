package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import com.gurobi.gurobi.GRBLinExpr
import de.uniwuerzburg.omosim.calibration.differentiablemodel.TermBuilder
import org.tensorflow.Operand
import org.tensorflow.op.Ops
import org.tensorflow.types.TFloat32

class TfTermBuilder(
    val tf: Ops
) : TermBuilder<TfAccumulatingTerm, Operand<TFloat32>> {
    override fun addVar(term: TfAccumulatingTerm, v: Operand<TFloat32>, coefficient: Double) {
        val mult = tf.math.mul(v, tf.constant(coefficient.toFloat()))
        val addition = tf.math.add(term.value, mult)
        term.value = addition
    }

    override fun addConstant(term: TfAccumulatingTerm, constant: Double) {
        val addition = tf.math.add(term.value, tf.constant(constant.toFloat()))
        term.value = addition
    }

    override fun addTerm(term: TfAccumulatingTerm, other: TfAccumulatingTerm, coefficient: Double) {
        val mult = tf.math.mul(other.value, tf.constant(coefficient.toFloat()))
        val addition = tf.math.add(term.value, mult)
        term.value = addition
    }

    override fun new(nVars: Int): TfAccumulatingTerm {
        return TfAccumulatingTerm(tf.constant(0f))
    }
}