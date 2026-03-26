package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.differentiablemodel.*
import org.jetbrains.kotlinx.multik.ndarray.data.get

interface VMatrixBuilder {
    fun build(nVars: Int, n: Int, mrep: SGGravity.SGCompactMatrixRep) : List<List<Term>>
}

object TrafficCountVMatrixBuilder : VMatrixBuilder {
    override fun build(nVars: Int, n: Int, mrep: SGGravity.SGCompactMatrixRep) : List<List<Term>> {
        val vMatrix = mutableListOf<List<Term>>()
        for (o in 0 until n) {
            // Get weight terms
            val weights = mutableListOf<Term>()
            val sum = LinearTerm(nVars)
            for (d in 0 until n) {
                val weight = if ( d != (n-1) ) {
                    Variable(nVars, d,  mrep.tMatrices[mrep.vActivity]!![o, d])
                } else {
                    // Last destination is chosen as the pivot element
                    Constant(nVars, mrep.tMatrices[mrep.vActivity]!![o, d])
                }
                sum.addTerm(weight, 1.0)
                weights.add(weight)
            }

            // Normalize
            val t = mutableListOf<Term>()
            for (d in 0 until n) {
                t.add( DivisionTerm(nVars, weights[d], sum) )
            }

            vMatrix.add(t)
        }
        return vMatrix
    }
}