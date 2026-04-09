package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.CalibrationConstants.MC_SAMPLES
import de.uniwuerzburg.omosim.calibration.CalibrationContext
import de.uniwuerzburg.omosim.calibration.SGGravityObjectiveTF
import de.uniwuerzburg.omosim.calibration.differentiablemodel.MatrixTF
import de.uniwuerzburg.omosim.calibration.differentiablemodel.TermBuilder
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfTermBuilderDummy
import de.uniwuerzburg.omosim.calibration.logger
import de.uniwuerzburg.omosim.calibration.surrogate.SGGravityCore.SGCompactMatrixRep
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.utils.diagonal
import org.jetbrains.kotlinx.multik.api.identity
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ones
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import org.jetbrains.kotlinx.multik.ndarray.operations.toArray
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

class SGGravityTF(
    val context: CalibrationContext,
    val objective: SGGravityObjectiveTF,
    val vMatrixBuilder: VMatrixBuilderTF,
    val termBuilder: TfTermBuilderDummy,
    val mode: Mode? = Mode.CAR_DRIVER
) {
    val core = SGGravityCore(context, mode)

    /**
     * Build surrogate for a gravity model with Sum-of-Squared-Errors objective.
     * Variables = Gravity Model attraction scalers for one activity type.
     *
     * @param vActivity ActivityType for which the gravity model will be variable
     * @param iThresh Performance parameter.
     * All terms with coefficients below this value will be ignored and not added to the result.
     * Higher values -> Computes faster but is a rougher approximation of the markov chain representation.
     *
     * @return surrogate model
     */
    fun build(
        model: TfModel,
        vActivity: ActivityType,
        iThresh: Double = 1e-4
    ) : TfModel {
        logger.info("Building surrogate for activity $vActivity")

        // Core work
        val n = context.omosim.grid.size
        val mrep = core.generateMarkovChainRep(vActivity) // Compact matrix representation
        val relevantODs = context.getRelevantODs() // Relevant origin-destination pairs for measurements
        val tripStartDistr = core.monteCarloTripStartDistribution( MC_SAMPLES ) // Temporal trip distribution

        // Transition matrix containing variable terms
        val (vMatrix, nVars) = vMatrixBuilder.build(model, mrep)

        logger.info("Number of variables: $nVars")

        // Create graph of the expected trips matrix: E(o, d | Car)
        val expectedTrips = ActivityType.entries.associateWith {
            val zeros = model.tf.zeros(model.tf.constant(intArrayOf(n, n)), TFloat32::class.java);
            MatrixTF(model.tf, zeros)
        }

        // Add expected trips for each destination activity
        for (activity in ActivityType.entries) {
            addE(
                termBuilder,
                nVars,
                mrep,
                expectedTrips[activity]!!,
                vMatrix,
                relevantODs,
                iThresh,
                activity
            )
        }

        // Objective
        val model = objective.build(nVars, expectedTrips, tripStartDistr)

        // Logging
        val terms = model.getSize()
        logger.info("Building surrogate complete. Number of terms: $terms")

        return model
    }


    fun <T, K> addE(
        builder: TermBuilder<T, K>,
        nVars: Int,
        mrep: SGCompactMatrixRep,
        expectedTrips: MatrixTF,
        vMatrix : MatrixTF,
        relevantODs: Set<Pair<Int, Int>>,
        iThresh: Double,
        activity: ActivityType
    ) {
        val builder = builder as TfTermBuilderDummy
        val tf = builder.model.tf
        val tActivity = if (activity == ActivityType.BUSINESS) {
            ActivityType.OTHER // Edge case: Use other type transition matrix for business activity
        } else {
            activity
        }

        val n = core.omosim.grid.size
        val pCar = mrep.pCar[tActivity]!!
        val mPriorCnst = mrep.mPriorCnst[activity]!!
        val mPriorVar = mrep.mPriorVar[activity]!!
        val mPriorVarT = mPriorVar.transpose()

        // In the case that the segment starts at vActivity
        var vStart: Operand<TFloat32>? = null

        // Transition matrix
        val tMatrix = if (activity == ActivityType.HOME) {
            mk.identity<Double>(n)
        } else {
            mrep.tMatrices[tActivity]!!
        }

        // Expected trip contribution unaffected by vActivity
        val mFix = when (activity) {
            in  core.fixActivities -> mPriorCnst.transpose().dot(tMatrix) // F = (K^T)A
            mrep.vActivity -> mk.zeros<Double>(n, n) // Not used
            else -> {
                // CASE: vActivity is flexible but not the destination
                // F = diag(iK) A
                val ones = mk.ones<Double>(1, n)
                val left = ones.dot(mPriorCnst).diagonal()
                left.dot(tMatrix)
            }
        }

        val h = builder.model.addMatrix(mrep.h.toArray()) // TODO h should be row

        val perm2dTranspose = tf.constant(
            intArrayOf(1, 0)
        )

        // Expected trip contribution affected by vActivity
        val mVar: Operand<TFloat32> = when (activity) {
            in core.fixActivities -> {
                when (mrep.vActivity) {
                    activity -> {
                        // For segments that started at vActivity: V = (vK)^T
                        // Here we only build v (vStart)
                        vStart = tf.linalg.matMul(h, vMatrix.matrixT)

                        // For other segments: V = (K^T)X
                        val left = builder.model.addMatrix(mPriorCnst.transpose().toArray())
                        tf.linalg.matMul(left, vMatrix.matrixT)
                        tf.zeros( tf.array(n, n), TFloat32::class.java) // TODO
                    }

                    in core.fixActivitiesNotHome -> {
                        // V = ( ( diag(h)XK )^T ) A
                        val left = builder.model.addMatrix(mPriorVarT.toArray())
                        val right = builder.model.addMatrix(
                            mrep.h.diagonal().transpose().dot(tMatrix).toArray()
                        )
                        val vT = tf.linalg.transpose( vMatrix.matrixT, perm2dTranspose)
                        val lm = tf.linalg.matMul(left, vT)
                        tf.linalg.matMul(lm, right)
                        tf.zeros( tf.array(n, n), TFloat32::class.java) // TODO
                    }

                    ActivityType.HOME -> {
                        throw NotImplementedError("Surrogate model dependent on home coefficients is not implemented!")
                    }

                    else -> {
                        // V = ( ( KX )^T ) A
                        val vT = tf.linalg.transpose( vMatrix.matrixT, perm2dTranspose)
                        val right = builder.model.addMatrix(mPriorVarT.dot(tMatrix).toArray())
                        tf.linalg.matMul(vT, right)
                        tf.zeros( tf.array(n, n), TFloat32::class.java) // TODO
                    }
                }
            }

            in core.flexActivities -> {
                when (mrep.vActivity) {
                    activity -> {
                        // V = diag(iK)X
                        val ones = mk.ones<Double>(1, n)
                        val left = builder.model.addMatrix(ones.dot(mPriorCnst).diagonal().toArray())
                        tf.linalg.matMul(left, vMatrix.matrixT)
                        tf.zeros( tf.array(n, n), TFloat32::class.java) // TODO
                    }

                    in core.fixActivitiesNotHome -> {
                        // V = diag(hXK)A
                        val left = h
                        val right = builder.model.addMatrix(mPriorVar.toArray())
                        val lm = tf.linalg.matMul(left, vMatrix.matrixT)
                        tf.linalg.matMul(lm, right)
                        tf.zeros( tf.array(n, n), TFloat32::class.java) // TODO
                    }

                    ActivityType.HOME -> {
                        throw NotImplementedError("Surrogate model dependent on home coefficients is not implemented!")
                    }

                    else -> {
                        // V = diag(KX)A
                        val left = builder.model.addMatrix(mk.ones<Double>(1, n).dot(mPriorVar).toArray())
                        tf.linalg.matMul(left, vMatrix.matrixT)
                        //tf.zeros( tf.array(n, n), TFloat32::class.java) // TODO
                    }
                }
            }

            else -> {
                throw IllegalStateException("$activity neither fixed nor flexible")
            }
        }

        // E += ( F + V ) odot pCar
        val fix = builder.model.addMatrix(mFix.times(pCar).toArray())
        val tMatrixCar = builder.model.addMatrix(tMatrix.times(pCar).toArray())
        val mPriorVarTCar = builder.model.addMatrix(mPriorVarT.times(pCar).toArray())
        val oCar = builder.model.addMatrix(pCar.toArray())

        // F
        //if (mrep.vActivity != activity) {
        //    expectedTrips.add(fix)
        //}

        // V
        val shape = mVar.asOutput().shape()
        val dims = shape.asArray()
        val nrows = dims[0].toInt()

        if (nrows == 1) {
            val dVar = tf.linalg.tensorDiag( tf.squeeze( mVar ) )
            expectedTrips.add(tf.linalg.matMul(dVar, tMatrixCar))
        } else {
            expectedTrips.add(tf.math.mul(mVar, oCar))
        }
        if ((mrep.vActivity in  core.fixActivities) and (mrep.vActivity == activity)) {
            // For segments that started at vActivity: V = (diag(v)K)^T
            val dStart = tf.linalg.tensorDiag( tf.squeeze( vStart ) )
            expectedTrips.add(tf.linalg.matMul(dStart, mPriorVarTCar))
        }
    }
}