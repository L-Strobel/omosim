package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.DivisionTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.ExponentialTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.Term
import de.uniwuerzburg.omosim.core.DestinationFinderDefault

class DistanceFunctionVMatrixBuilder(
    val context: DistanceFunctionMatchContext
) : VMatrixBuilderNative {
    override fun build(
        mrep: SurrogateGravity.SGCompactMatrixRep,
    ): Pair<List<List<Term>>, Int> {
        val vMatrix = mutableListOf<List<Term>>()

        val omosim = context.omosim
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[mrep.vActivity]!!
        val (_, nVars) = dcFunction.deterrenceFunctionAsTerm(1.0)
        val n = context.omosim.grid.size

        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)
            val attractions = finder.getWeightsNoOrigin(omosim.grid, activityType = mrep.vActivity) // TODO fix for internal cell connection

            val weights = mutableListOf<Term>()
            val sum = LinearTerm(nVars)
            for (d in 0 until n) {
                // TODO refactor
                val distanceAdj = if (distances[d].toDouble() <= 0.0) {
                    0.01 // 10 Meters
                } else {
                    distances[d].toDouble() / 1000
                }

                val (exponent, _) = dcFunction.deterrenceFunctionAsTerm(distanceAdj)
                val deterrenceValue = ExponentialTerm(nVars, exponent)
                val weight = LinearTerm(nVars)
                weight.addTerm(deterrenceValue, attractions[d])

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