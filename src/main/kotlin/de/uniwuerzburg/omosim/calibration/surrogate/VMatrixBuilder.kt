package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import org.tensorflow.Operand
import org.tensorflow.ndarray.StdArrays
import org.tensorflow.types.TFloat32
import kotlin.math.ln


class DistanceFunctionVMatrixBuilderTFTensor(
    val context: DistanceFunctionMatchContext
): VMatrixBuilderTF {
    override fun build(
        mrep: SGGravity.SGCompactMatrixRep,
    ): Pair<Operand<TFloat32>, TfModel> {
        val omosim = context.omosim
        val n = omosim.grid.size
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[mrep.vActivity]!!
        val (_, nVars) = dcFunction.deterrenceFunctionAsTerm(1.0)

        val model = TfModel(nVars)
        val tf = model.tf

        val arrDistance = Array<FloatArray>(n) { FloatArray(n) }
        val arrAttraction = Array<FloatArray>(n) { FloatArray(n) }
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)
            val attractions = finder.getWeightsNoOrigin(omosim.grid, activityType = mrep.vActivity)

            for (d in 0 until n) {
                val distanceAdj = if (distances[d].toDouble() <= 0.0) {
                    0.01 // 10 Meters
                } else {
                    distances[d].toDouble() / 1000
                }
                arrDistance[o][d] = distanceAdj.toFloat()
                arrAttraction[o][d] = ln(attractions[d]).toFloat() // TODO could be more compact
            }
        }
        val mAttraction = TFloat32.tensorOf(StdArrays.ndCopyOf(arrAttraction))
        model.addTensor(mAttraction)
        val oAttraction = tf.constant(mAttraction)
        val oDeterrence = dcFunction.applyDeterrenceToTensor(arrDistance, model)
        val weightExponent = tf.math.add(oAttraction, oDeterrence)
        val normalized = tf.nn.softmax(weightExponent)
        return Pair(normalized, model)
    }
}