package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.CalibrationContext
import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.*
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import org.jetbrains.kotlinx.multik.ndarray.data.get

interface VMatrixBuilder<T: CalibrationContext, V> {
    fun build(context: T, mrep: SGGravity.SGCompactMatrixRep) : Pair<List<List<V>>, Int>
}

object TrafficCountVMatrixBuilder : VMatrixBuilder<TrafficCountCalibrationContext, Term> {
    override fun build(
        context: TrafficCountCalibrationContext, mrep: SGGravity.SGCompactMatrixRep,
    ) : Pair<List<List<Term>>, Int> {
        val n = context.omosim.grid.size
        val nVars = context.omosim.grid.size - 1

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
        return Pair(vMatrix, nVars)
    }
}

object DistanceFunctionVMatrixBuilder : VMatrixBuilder<DistanceFunctionMatchContext, Term> {
    override fun build(
        context: DistanceFunctionMatchContext, mrep: SGGravity.SGCompactMatrixRep,
    ) : Pair<List<List<Term>>, Int> {
        val vMatrix = mutableListOf<List<Term>>()

        val omosim = context.omosim
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[mrep.vActivity]!!
        val (_, nVars) = dcFunction.deterrenceFunctionAsTerm(1.0)
        val n = context.omosim.grid.size

        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)
            val attractions = finder.getWeightsNoOrigin(omosim.grid, activityType=mrep.vActivity)

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
                t.add( DivisionTerm(nVars, weights[d], sum) )
            }

            vMatrix.add(t)
        }
        return Pair(vMatrix, nVars)
    }
}