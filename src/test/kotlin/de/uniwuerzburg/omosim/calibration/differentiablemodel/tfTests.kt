package de.uniwuerzburg.omosim.calibration.differentiablemodel

import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import org.junit.jupiter.api.Test
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.op.Ops
import org.tensorflow.types.TFloat32
import kotlin.math.abs

class tfTests {

    @Suppress("SameParameterValue")
    fun buildLargeTestModel(nVars: Int) : DifferentiableModelUVBase {
        val model = DifferentiableModelUVBase(nVars)

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
        p2.addTerm(1, 1.1)

        val top = QuadraticTerm(nVars, p1, p2,-1.1)

        val dTerm = DivisionTerm(nVars, lTerm1, top)
        model.setRootTerm(dTerm)
        return  model
    }


    @Suppress("SameParameterValue")
    fun buildLargeTestModelTF(nVars: Int) : DifferentiableModelUV {
        val model = TfModel(nVars)
        var lTerm1 = model.createLinearTerm()
        for (i in 0 until nVars) {
            val c = model.createConstant(1.3f)
            val m = model.createMultiplication(model.getVariable(i), model.createConstant(3.3f))
            val lbTerm = model.createLinearTerm(c, m)
            lTerm1 = model.createLinearTerm(lTerm1, lbTerm)
        }

        val c1 = model.createConstant(2.2f)
        val m1 = model.createMultiplication(model.getVariable(0), model.createConstant(1.1f))
        val p1 = model.createLinearTerm(c1, m1)

        val c2 = model.createConstant(2.2f)
        val m2 = model.createMultiplication(model.getVariable(1), model.createConstant(1.1f))
        val p2 = model.createLinearTerm(c2, m2)

        val mult = model.createMultiplication(p1, p2)
        val top = model.createMultiplication(mult, model.createConstant(-1.1f))

        val dTerm = model.createDivision(lTerm1, top)
        model.setRoot(dTerm)
        return model
    }

    @Test
    fun largerModel() {
        val nVars = 1000
        val model = buildLargeTestModel(nVars)

        val vars = DoubleArray(nVars) { 1.1 }

        // Backward
        val gradientB = DoubleArray(nVars) { 0.0 }
        model.gradientReverse(vars, gradientB, 1.0)

        val modelTF = buildLargeTestModelTF(nVars)
        val gradientTF = DoubleArray(nVars) { 0.0 }
        modelTF.gradient(vars, gradientTF)

        println(gradientTF.toList())
        println(gradientB.toList())
        assert(gradientTF.zip(gradientB).all { abs(it.first - it.second) <= 1e-3 })
    }

    @Test
    fun playground() {
        val model = TfModel(2)
        val x = model.getVariable(0)
        val y = model.getVariable(1)
        val c1 = model.createConstant(3f)
        val c2 = model.createConstant(4f)
        val root = model.createLinearTerm(
            model.createMultiplication(x,c1),
            model.createMultiplication(y,c2)
        )
        model.setRoot(root)
        val g =  doubleArrayOf(0.0, 0.0)
        model.gradient(doubleArrayOf(1.0, 1.0), g)
        println(g.toList())
    }
}