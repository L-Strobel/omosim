package de.uniwuerzburg.omosim.calibration.algorithms

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelUVBase
import de.uniwuerzburg.omosim.calibration.differentiablemodel.LinearBaseTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.LinearTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.QuadraticTerm

object TestObjectives {
    fun diffModel() : Pair<(DoubleArray) -> Double, DifferentiableModelUVBase> {
        val model = DifferentiableModelUVBase(1)
        val base = LinearBaseTerm(1)
        base.addTerm(0, 1.0)
        val quad = QuadraticTerm(1, base, base, 1.0)
        model.setRootTerm(quad)

        val objective: (DoubleArray) -> Double = { x: DoubleArray ->
            model.evaluate(x)
        }
        return Pair(objective, model)
    }

    fun sphere(nDimensions: Int, shift: Double) : Pair<(DoubleArray) -> Double, DifferentiableModelUVBase> {
        val objective =  { x: DoubleArray ->
            var oval = 0.0
            for (i in 0 until nDimensions) {
                oval += (x[i] - shift) * (x[i] - shift)
            }
            oval
        }
        val model = DifferentiableModelUVBase(nDimensions)
        val sum = LinearTerm(nDimensions)
        for (i in 0 until nDimensions) {
            val base = LinearBaseTerm(nDimensions)
            base.addTerm(i, 1.0)
            base.addConstant(-shift)
            val quad = QuadraticTerm(1, base, base, 1.0)
            sum.addTerm(quad, 1.0)
        }
        model.setRootTerm(sum)

        return Pair(objective, model)
    }
}