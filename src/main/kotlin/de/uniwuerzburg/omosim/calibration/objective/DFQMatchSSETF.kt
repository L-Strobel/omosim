package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32


class DFQMatchSSETF (
    val activity: ActivityType,
    private val cdfVals: List<Pair<Double, Double>>,
    val context: DistanceFunctionMatchContext
) : SMGravityObjectiveTF<TfModelUV> {
    override fun build(
        core: TfModelCore,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ): TfModelUV {
        val tf = core.tf
        val omosim = context.omosim
        val n = context.omosim.grid.size

        val totalPopulation = tf.constant(context.totalPopulation.toFloat())
        val expectedTripsScaled = tf.math.mul(expectedTrips[activity]!!, totalPopulation)
        val expectedTotalTrips = tf.reduceSum(
            expectedTripsScaled,
            tf.constant(intArrayOf(0, 1))
        )

        // Expected distances
        val fArrayDistance = FloatArray(n*n) { 0f }
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)
            for (d in 0 until n) {
                fArrayDistance[o * n + d] = (distances[d] / 1000f)
            }
        }

        // Sorting
        val indices = fArrayDistance.indices.sortedBy { fArrayDistance[it] }
        val fArrayDistanceSorted = indices.map { fArrayDistance[it] }.toFloatArray()

        val indicesTF = tf.constant(indices.toIntArray())
        val flatten = tf.constant(intArrayOf(-1))
        val flatETrips = tf.reshape(expectedTripsScaled, flatten)
        val sortedETrips = tf.gather(flatETrips, indicesTF, tf.constant(0))

        // Objective
        val oTerms = mutableListOf<Operand<TFloat32>>()
        for ((distance, cProb) in cdfVals) {
            val binIndices = mutableListOf<Int>()
            for ((i, entryDistance) in fArrayDistanceSorted.withIndex()) {
                if (distance < entryDistance) { break }
                binIndices.add(i)
            }

            val binIndicesTF = tf.constant( binIndices.toIntArray() )
            val binTrips = tf.gather(sortedETrips, binIndicesTF, tf.constant(0))
            val binCount = tf.reduceSum( binTrips, tf.constant(0) )
            val cProbModel = tf.math.div(binCount, expectedTotalTrips)

            val diff = tf.math.sub(cProbModel, tf.constant(cProb.toFloat()))
            val sqrDiff = tf.math.square(diff)
            oTerms.add( sqrDiff )
        }
        val obj = tf.math.addN(oTerms)

        return TfModelUV(core, obj)
    }
}