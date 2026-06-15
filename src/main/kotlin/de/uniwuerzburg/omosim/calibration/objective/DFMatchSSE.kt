package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.*
import de.uniwuerzburg.omosim.core.models.ActivityType

class DFMatchSSE (
    val activity: ActivityType,
    private val mean: Double,
    private val m2: Double,
    val context: DistanceFunctionMatchContext
) : SMGravityObjectiveNative<NativeModelUV> {
    override fun build (
        nVars: Int,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : NativeModelUV {
        val omosim = context.omosim
        val n = context.omosim.grid.size

        val expectedTotalDistance = LinearTerm(nVars)
        val expectedTotalTrips = LinearTerm(nVars)
        val expectedODCount = mutableMapOf<Pair<Int, Int>, LinearTerm>()
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)

            for (d in 0 until n) {
                val expectedTripCount = LinearTerm(nVars)
                expectedTripCount.addTerm(
                    expectedTrips[activity]!![o][d],
                    context.totalPopulation
                )
                expectedODCount[Pair(o,d)] = expectedTripCount

                expectedTotalDistance.addTerm(
                    expectedTripCount,
                    distances[d] / 1000.0
                )
                expectedTotalTrips.addTerm(
                    expectedTripCount,
                    1.0
                )
            }
        }
        val expectedMean = DivisionTerm(nVars, expectedTotalDistance, expectedTotalTrips)

        val expectedSumOfSquare = LinearTerm(nVars)
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)

            for (d in 0 until n) {
                val expectedTripCount = expectedODCount[Pair(o,d)]!!

                expectedSumOfSquare.addTerm(
                    expectedTripCount,
                    (distances[d] / 1000.0) * (distances[d] / 1000.0)
                )
            }
        }
        val moment2 = DivisionTerm(nVars, expectedSumOfSquare, expectedTotalTrips)

        // (m - s)^2 = m^2 - 2ms + s^2
        val obj = LinearTerm(nVars)
        obj.addConstant(mean * mean * 10)
        obj.addTerm(expectedMean, -2 * mean * 10)
        val qTermMean = QuadraticTerm(nVars, expectedMean, expectedMean, 1.0)
        obj.addTerm(qTermMean, 1.0 * 10.0)

        val objMoment2 = LinearTerm(nVars)
        objMoment2.addConstant(m2 * m2)
        objMoment2.addTerm(moment2, -2 * m2)
        val qTermVar = QuadraticTerm(nVars, moment2, moment2, 1.0)
        objMoment2.addTerm(qTermVar, 1.0)

        obj.addTerm(
            PowerTerm(nVars, objMoment2, 0.5), 1.0
        )

        val model = NativeModelUV(nVars)
        model.setRootTerm(obj)

        return model
    }
}