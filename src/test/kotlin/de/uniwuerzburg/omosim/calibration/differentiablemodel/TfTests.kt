package de.uniwuerzburg.omosim.calibration.differentiablemodel

import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import org.junit.jupiter.api.Test
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import kotlin.math.abs

class TfTests {
    companion object {
        @Suppress("SameParameterValue")
        fun buildLargeTestModelTF(nVars: Int) : TfModelUV {
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
            val m2 = tf.math.mul(model.getVariable(1), tf.constant(1.1f))
            val p2 = tf.math.add(c2, m2)

            val mult = tf.math.mul(p1, p2)
            val top  = tf.math.mul(mult, tf.constant(-1.1f))

            val dTerm = tf.math.div(lTerm1, top)
            return TfModelUV(model, dTerm)
        }
    }

    @Test
    fun largerModelGradient() {
        val nVars = 1000
        val model = NativeModelUVTest.buildLargeTestModel(nVars)
        val modelTF = buildLargeTestModelTF(nVars)
        val vars = DoubleArray(nVars) { 1.1 }

        // Base Model
        val gradientB = DoubleArray(nVars) { 0.0 }
        model.gradientReverse(vars, gradientB, 1.0)

        // TensorFlow
        val gradientTF = DoubleArray(nVars) { 0.0 }
        modelTF.gradient(vars, gradientTF)

        assert(gradientTF.zip(gradientB).all { abs(it.first - it.second) <= 1e-3 })
    }

    @Test
    fun largerModelEval() {
        val nVars = 10
        val model = NativeModelUVTest.buildLargeTestModel(nVars)
        val modelTF = buildLargeTestModelTF(nVars)
        val vars = DoubleArray(nVars) { 1.1 }

        // Base Model
        val yBase = model.evaluate(vars)

        // TensorFlow
        val yTF = modelTF.evaluate(vars)

        assert( abs(yBase - yTF) <= 1e-3 )
    }
}