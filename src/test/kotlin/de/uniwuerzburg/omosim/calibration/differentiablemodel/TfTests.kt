package de.uniwuerzburg.omosim.calibration.differentiablemodel

import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import org.junit.jupiter.api.Test
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import kotlin.math.abs

class TfTests {
    companion object {
        @Suppress("SameParameterValue")
        fun buildLargeTestModelTF(nVars: Int) : TfModel {
            val model = TfModel(nVars)

            val terms = mutableListOf<Operand<TFloat32>>()
            for (i in 0 until nVars) {
                val c = model.createConstant(1.3f)
                val m = model.createMultiplication(model.getVariable(i), model.createConstant(3.3f))
                terms.add(c)
                terms.add(m)
            }
            val lTerm1 = model.createLinearTerm(terms)

            val c1 = model.createConstant(2.2f)
            val m1 = model.createMultiplication(model.getVariable(0), model.createConstant(1.1f))
            val p1 = model.createLinearTerm(c1, m1)

            val c2 = model.createConstant(2.2f)
            val m2 = model.createMultiplication(model.getVariable(1), model.createConstant(1.1f))
            val p2 = model.createLinearTerm(c2, m2)

            val mult = model.createMultiplication(p1, p2)
            val top = model.createMultiplication(mult, model.createConstant(-1.1f))

            val dTerm = model.createDivision(lTerm1, top)
            model.finalize(dTerm)
            return model
        }
    }

    @Test
    fun largerModelGradient() {
        val nVars = 1000
        val model = DifferentiableModelUVBaseTest.buildLargeTestModel(nVars)
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
        val model = DifferentiableModelUVBaseTest.buildLargeTestModel(nVars)
        val modelTF = buildLargeTestModelTF(nVars)
        val vars = DoubleArray(nVars) { 1.1 }

        // Base Model
        val yBase = model.evaluate(vars)

        // TensorFlow
        val yTF = modelTF.evaluate(vars)

        assert( abs(yBase - yTF) <= 1e-3 )
    }
}