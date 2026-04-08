package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.CalibrationContext
import de.uniwuerzburg.omosim.calibration.DistanceFunctionMatchContext
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.*
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfAccumulatingTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfTermBuilder
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfTermBuilderDummy
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.io.json.OutputActivity
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.tensorflow.Graph
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.ndarray.StdArrays
import org.tensorflow.op.Ops
import org.tensorflow.op.core.ReduceSum
import org.tensorflow.types.TFloat32
import org.tensorflow.types.TInt32
import kotlin.math.ln


interface VMatrixBuilder<ACC, V> {
    fun build(builder: TermBuilder<ACC, V>, mrep: SGGravity.SGCompactMatrixRep) : Pair<Matrix<V>, Int>
}

class TrafficCountVMatrixBuilder(
    val context: TrafficCountCalibrationContext
) : VMatrixBuilder<LinearTerm, Term> {
    override fun build(
        builder: TermBuilder<LinearTerm, Term>,
        mrep: SGGravity.SGCompactMatrixRep
    ): Pair<Matrix<Term>, Int> {
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
        return Pair(Matrix(vMatrix), nVars)
    }
}

class DistanceFunctionVMatrixBuilder(
    val context: DistanceFunctionMatchContext
) : VMatrixBuilder<LinearTerm, Term> {
    override fun build(
        builder: TermBuilder<LinearTerm, Term>,
        mrep: SGGravity.SGCompactMatrixRep,
    ): Pair<Matrix<Term>, Int> {
        val vMatrix = mutableListOf<List<Term>>()

        val omosim = context.omosim
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[mrep.vActivity]!!
        val (_, nVars) = dcFunction.deterrenceFunctionAsTerm(1.0)
        val n = context.omosim.grid.size

        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)
            val attractions = finder.getWeightsNoOrigin(omosim.grid, activityType = mrep.vActivity)

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
        return Pair(Matrix(vMatrix), nVars)
    }
}

class DistanceFunctionVMatrixBuilderTF(
    val context: DistanceFunctionMatchContext
): VMatrixBuilder<TfAccumulatingTerm, Operand<TFloat32>> {
    override fun build(
        builder: TermBuilder<TfAccumulatingTerm, Operand<TFloat32>>,
        mrep: SGGravity.SGCompactMatrixRep,
    ): Pair<Matrix<Operand<TFloat32>>, Int> {
        builder as TfTermBuilder // TODO Refactor this

        val tf = builder.model.tf
        val vMatrix = mutableListOf<List<Operand<TFloat32>>>()

        val omosim = context.omosim
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[mrep.vActivity]!!
        val (_, nVars) = dcFunction.deterrenceFunctionAsTFTerm(1.0, builder)
        val n = context.omosim.grid.size

        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)
            val attractions = finder.getWeightsNoOrigin(omosim.grid, activityType = mrep.vActivity)

            val weights = mutableListOf<Operand<TFloat32>>()
            for (d in 0 until n) {
                // TODO refactor
                val distanceAdj = if (distances[d].toDouble() <= 0.0) {
                    0.01 // 10 Meters
                } else {
                    distances[d].toDouble() / 1000
                }

                val (exponent, _) = dcFunction.deterrenceFunctionAsTFTerm(distanceAdj, builder)
                val deterrenceValue = tf.math.exp(exponent)
                val attraction =  tf.constant(attractions[d].toFloat())
                val weight = tf.math.mul(deterrenceValue, attraction)
                weights.add(weight)
            }

            val sum = tf.math.addN(weights)

            // Normalize
            val t = mutableListOf<Operand<TFloat32>>()
            for (d in 0 until n) {
                t.add( tf.math.div(weights[d], sum) )
            }

            vMatrix.add(t)
        }
        return Pair(Matrix(vMatrix), nVars)
    }
}

class DistanceFunctionVMatrixBuilderTFTensor(
    val context: DistanceFunctionMatchContext
): VMatrixBuilder<Operand<TFloat32>, Operand<TFloat32>> {
    override fun build(
        builder: TermBuilder<Operand<TFloat32>, Operand<TFloat32>>,
        mrep: SGGravity.SGCompactMatrixRep,
    ): Pair<MatrixTF, Int> {
        builder as TfTermBuilderDummy // TODO Refactor this

        val omosim = context.omosim
        val n = omosim.grid.size
        val tf = builder.model.tf
        val shape: Operand<TInt32> = tf.constant(intArrayOf(n, n))
        val vMatrix = mutableListOf<List<Operand<TFloat32>>>()

        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[mrep.vActivity]!!
        val (_, nVars) = dcFunction.deterrenceFunctionAsTerm(1.0)

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
        builder.model.addTensor(mAttraction)
        val oAttraction = tf.constant(mAttraction)
        val oDeterrence = dcFunction.applyDeterrenceToTensor(arrDistance, builder)
        val weightExponent = tf.math.add(oAttraction, oDeterrence)
        val normalized = tf.nn.softmax(weightExponent)
        return Pair(MatrixTF(tf, normalized), nVars)
    }
}