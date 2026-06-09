package de.uniwuerzburg.omosim.calibration.differentiablemodel

import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.*
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelMV
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import org.junit.jupiter.api.Test
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import kotlin.math.abs

class TfMVTest {
    companion object {
        @Suppress("SameParameterValue")
        fun buildLargeTestModel(nVars: Int) : NativeModelMV {
            val model = NativeModelMV(nVars)

            val lTerm1 = LinearTerm(nVars)
            for (i in 0 until nVars) {
                val lbTerm = LinearBaseTerm(nVars)
                lbTerm.addConstant(1.3)
                lbTerm.addTerm(i, 3.3)
                lTerm1.addTerm(lbTerm, 1.0)
            }

            val p1 = LinearBaseTerm(nVars)
            p1.addConstant(2.2)
            p1.addTerm(0, 1.1)

            val p2 = LinearBaseTerm(nVars)
            p2.addConstant(2.2)
            p2.addTerm(1, 2.1)

            val top = QuadraticTerm(nVars, p1, p2,-1.1)

            val dTerm = DivisionTerm(nVars, lTerm1, top)
            val pTerm = QuadraticTerm(nVars, lTerm1, top,1.0)

            model.setRootTerms(listOf(dTerm, pTerm))
            return  model
        }

        @Suppress("SameParameterValue")
        fun buildLargeTestModelTF(nVars: Int) : TfModelMV {
            val model = TfModelCore(nVars)
            val tf = model.tf
            val terms = mutableListOf<Operand<TFloat32>>()
            for (i in 0 until nVars) {
                val c = tf.constant(1.3f)
                val m = tf.math.mul(model.getVariable(i), tf.constant(3.3f))
                terms.add(c)
                terms.add(m)
            }
            val lTerm1 = tf.math.addN(terms)

            val c1 = tf.constant(2.2f)
            val m1 = tf.math.mul(model.getVariable(0), tf.constant(1.1f))
            val p1 = tf.math.add(c1, m1)

            val c2 = tf.constant(2.2f)
            val m2 = tf.math.mul(model.getVariable(1), tf.constant(2.1f))
            val p2 = tf.math.add(c2, m2)

            val mult = tf.math.mul(p1, p2)
            val top  = tf.math.mul(mult, tf.constant(-1.1f))

            val dTerm = tf.math.div(lTerm1, top)
            val pTerm = tf.math.mul(lTerm1, top)
            return TfModelMV( model, listOf(dTerm, pTerm))
        }
    }

    @Test
    fun largerModelGradient() {
        val nVars = 3
        val model = buildLargeTestModel(nVars)
        val modelTF = buildLargeTestModelTF(nVars)
        val vars = DoubleArray(nVars) { 1.1 }

        // Base Model
        val jacBase = model.jacobian(vars, 1).flatMap { it.toList() }

        // TensorFlow
        val jacTF = modelTF.jacobian(vars, 1).flatMap { it.toList() }

        assert(jacTF.zip(jacBase).all { abs(it.first - it.second) <= 1e-3 })
    }

    @Test
    fun largerModelEval() {
        val nVars = 10
        val model = buildLargeTestModel(nVars)
        val modelTF = buildLargeTestModelTF(nVars)
        val vars = DoubleArray(nVars) { 1.1 }

        // Base Model
        val yBase = model.evaluate(vars)

        // TensorFlow
        val yTF = modelTF.evaluate(vars)

        assert(yTF.zip(yBase).all { abs(it.first - it.second) <= 1e-3 })
    }
}