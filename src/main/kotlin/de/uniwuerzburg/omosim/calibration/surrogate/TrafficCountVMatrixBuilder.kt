package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.*
import org.jetbrains.kotlinx.multik.ndarray.data.get

/**
 * Matrix that defines how the transition matrix of vActivity changes with the attraction value scalers.
 * Native.
 */
class TrafficCountVMatrixBuilder(
    val context: TrafficCountCalibrationContext
) : VMatrixBuilderNative {
    override fun build(
        mrep: SurrogateGravity.SGCompactMatrixRep
    ): Pair<List<List<Term>>, Int> {
        val n = context.omosim.grid.size
        val nVars = context.omosim.grid.size - 1

        val vMatrix = mutableListOf<List<Term>>()
        for (o in 0 until n) {
            // Get weight terms
            val weights = mutableListOf<Term>()
            val sum = LinearTerm(nVars)
            for (d in 0 until n) {
                val weight = if ( d != (n-1) ) {
                    Variable(nVars, d, mrep.tMatrices[mrep.vActivity]!![o, d])
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
                t.add(DivisionTerm(nVars, weights[d], sum))
            }

            vMatrix.add(t)
        }
        return Pair(vMatrix, nVars)
    }
}
