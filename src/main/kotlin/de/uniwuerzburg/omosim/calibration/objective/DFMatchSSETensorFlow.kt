package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.types.TFloat32
import smile.stat.Hypothesis.F


class DFMatchSSETensorFlow (
    val activity: ActivityType,
    private val mean: Double,
    private val m2: Double,
    val context: DistanceFunctionMatchContext
) : SGGravityObjectiveTF {
    override fun build(
        model: TfModel,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ): TfModel {
        val tf = model.tf
        val omosim = context.omosim
        val n = context.omosim.grid.size

        val arrDistance = Array(n) { FloatArray(n) }
        val arrDistanceSquared = Array(n) { FloatArray(n) }
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)
            for (d in 0 until n) {
                arrDistance[o][d] = (distances[d] / 1000f)
                arrDistanceSquared[o][d] = (distances[d] / 1000f) * (distances[d] / 1000f)
            }
        }
        val oDistance        = model.addMatrix( arrDistance )
        val oDistanceSquared = model.addMatrix( arrDistanceSquared )

        val totalPopulation = tf.constant(context.totalPopulation.toFloat())
        val expectedTripsScaled = tf.math.mul(expectedTrips[activity]!!, totalPopulation)
        val expectedDistance = tf.math.mul(expectedTripsScaled, oDistance)
        val expectedDistanceSquared = tf.math.mul(expectedTripsScaled, oDistanceSquared)

        val expectedTotalTrips = tf.reduceSum(
            expectedTripsScaled,
            tf.constant(intArrayOf(0, 1))
        )

        val expectedTotalDistance = tf.reduceSum(
            expectedDistance,
            tf.constant(intArrayOf(0, 1))
        )

        val expectedSumOfSquare = tf.reduceSum(
            expectedDistanceSquared,
            tf.constant(intArrayOf(0, 1))
        )

        val expectedMean = tf.math.div(expectedTotalDistance, expectedTotalTrips)
        val moment2      = tf.math.div(expectedSumOfSquare, expectedTotalTrips)

        val diff1 = tf.math.sub(expectedMean, tf.constant( mean.toFloat() ))
        val t1 = tf.math.mul( tf.math.square( diff1 ), tf.constant(10f) )

        val diff2 = tf.math.sub(moment2, tf.constant( m2.toFloat() ))
        val t2 = tf.math.sqrt( tf.math.square( diff2 ) )

        val obj = tf.math.add(t1, t2)

        model.finalize(obj)
        return model
    }
}