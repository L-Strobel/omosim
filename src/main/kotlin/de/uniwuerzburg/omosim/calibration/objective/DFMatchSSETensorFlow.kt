package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import kotlin.math.pow


class DFMatchSSETensorFlow (
    val activity: ActivityType,
    private val moments: List<Double>,
    val context: DistanceFunctionMatchContext
) : SGGravityObjectiveTF {
    override fun build(
        model: TfModelUV,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ): TfModelUV {
        val tf = model.tf
        val omosim = context.omosim
        val n = context.omosim.grid.size

        val totalPopulation = tf.constant(context.totalPopulation.toFloat())
        val expectedTripsScaled = tf.math.mul(expectedTrips[activity]!!, totalPopulation)
        val expectedTotalTrips = tf.reduceSum(
            expectedTripsScaled,
            tf.constant(intArrayOf(0, 1))
        )

        val oTerms = mutableListOf<Operand<TFloat32>>()
        for ((i, m) in moments.withIndex()) {
            // Expected Moment
            val fArrayDistance = Array(n) { FloatArray(n) }
            for ((o, origin) in omosim.grid.withIndex()) {
                val distances = omosim.routingCache.getDistances(origin, omosim.grid)
                for (d in 0 until n) {
                    fArrayDistance[o][d] = (distances[d] / 1000f).pow(i+1)
                }
            }
            val oDistance = model.addMatrix( fArrayDistance )
            val odMomentValues = tf.math.mul(expectedTripsScaled, oDistance)
            val expectedMomentSum = tf.reduceSum(odMomentValues, tf.constant(intArrayOf(0, 1)))
            val expectedMoment = tf.math.div(expectedMomentSum, expectedTotalTrips)

            // Objective Term
            val diff = tf.math.sub(expectedMoment, tf.constant(m.toFloat()))
            val sqrDiff = tf.math.square(diff)
            val r = 1/(i+1).toFloat()
            val oTerm = if (r != 1f) {
                tf.math.pow(sqrDiff, tf.constant(r))
            } else {
                tf.math.mul(sqrDiff, tf.constant(10f))
            }
            oTerms.add(oTerm)
        }

        val obj = tf.math.addN(oTerms)

        model.finalize(obj)
        return model
    }
}