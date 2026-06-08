package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import kotlin.math.ln


class DistanceFunctionVMatrixBuilderTF(
    val context: DistanceFunctionMatchContext
): VMatrixBuilderTF {
    override fun build(
        mrep: SGGravity.SGCompactMatrixRep,
    ): Pair<Operand<TFloat32>, TfModelUV> {
        val omosim = context.omosim
        val n = omosim.grid.size
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[mrep.vActivity]!!
        val (_, nVars) = dcFunction.deterrenceFunctionAsTerm(1.0)

        val model = TfModelUV(nVars)
        val tf = model.tf

        val arrDistance = Array(n) { FloatArray(n) }
        val arrLnAttraction = Array(n) { FloatArray(n) }
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)
            val attractions = finder.getWeightsNoOrigin(omosim.grid, activityType = mrep.vActivity) // TODO fix for internal cell connection

            for (d in 0 until n) {
                val distanceAdj = if (distances[d].toDouble() <= 100.0) {
                    0.1f
                } else {
                    distances[d] / 1000f
                }
                arrDistance[o][d] = distanceAdj
                arrLnAttraction[o][d] = ln(attractions[d]).toFloat()
            }
        }
        val oAttraction = model.addMatrix( arrLnAttraction )
        val oDeterrence = dcFunction.applyDeterrenceToTensor(arrDistance, model)
        val weightExponent = tf.math.add(oAttraction, oDeterrence)
        val normalized = tf.nn.softmax(weightExponent)
        return Pair(normalized, model)
    }
}