package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import com.gurobi.gurobi.GRBLinExpr
import de.uniwuerzburg.omosim.calibration.differentiablemodel.TermBuilder
import org.tensorflow.Operand
import org.tensorflow.op.Ops
import org.tensorflow.types.TFloat32

class TfTermBuilder(
    val model: TfModel
) : TermBuilder<TfAccumulatingTerm, Operand<TFloat32>> {
    override fun addVar(term: TfAccumulatingTerm, v: Operand<TFloat32>, coefficient: Double) {
        val mult = model.tf.math.mul(v, model.tf.constant(coefficient.toFloat()))
        val addition = model.tf.math.add(term.value, mult)
        term.value = addition
    }

    override fun addConstant(term: TfAccumulatingTerm, constant: Double) {
        val addition = model.tf.math.add(term.value, model.tf.constant(constant.toFloat()))
        term.value = addition
    }

    override fun addTerm(term: TfAccumulatingTerm, other: TfAccumulatingTerm, coefficient: Double) {
        val mult = model.tf.math.mul(other.value, model.tf.constant(coefficient.toFloat()))
        val addition = model.tf.math.add(term.value, mult)
        term.value = addition
    }

    override fun new(nVars: Int): TfAccumulatingTerm {
        return TfAccumulatingTerm(model.tf.constant(0f))
    }
}


class TfTermBuilderDummy(
    val model: TfModel
) : TermBuilder<Operand<TFloat32>, Operand<TFloat32>> {
    override fun addVar(term: Operand<TFloat32>, v: Operand<TFloat32>, coefficient: Double) {

    }

    override fun addConstant(term: Operand<TFloat32>, constant: Double) {

    }

    override fun addTerm(term: Operand<TFloat32>, other: Operand<TFloat32>, coefficient: Double) {

    }

    override fun new(nVars: Int): Operand<TFloat32> {
        throw NotImplementedError()
    }
}