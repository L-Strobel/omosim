package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.CalibrationConstants.MC_SAMPLES
import de.uniwuerzburg.omosim.calibration.CalibrationContext
import de.uniwuerzburg.omosim.calibration.SGGravityObjectiveNative
import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModel
import de.uniwuerzburg.omosim.calibration.differentiablemodel.LinearTermBuilder
import de.uniwuerzburg.omosim.calibration.differentiablemodel.TermBuilder
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
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.times

class SGGravityNative<M: DifferentiableModel> (
    val context: CalibrationContext,
    val objective: SGGravityObjectiveNative<M>,
    val vMatrixBuilder: VMatrixBuilderNative,
    val termBuilder: LinearTermBuilder,
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
        vActivity: ActivityType,
        iThresh: Double = 1e-4
    ) : M {
        logger.info("Building surrogate for activity $vActivity")

        // Core work
        val n = context.omosim.grid.size
        val mrep = core.generateMarkovChainRep(vActivity) // Compact matrix representation
        val relevantODs = context.getRelevantODs() // Relevant origin-destination pairs for measurements
        val tripStartDistr = core.monteCarloTripStartDistribution( MC_SAMPLES ) // Temporal trip distribution

        // Transition matrix containing variable terms
        val (vMatrix, nVars) = vMatrixBuilder.build(mrep)

        logger.info("Number of variables: $nVars")

        // Create graph of the expected trips matrix: E(o, d | Car)
        val expectedTrips = ActivityType.entries.associateWith {
            List(n) {
                List(n) {
                    termBuilder.new(nVars)
                }
            }
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


    /**
     * Determine expected trips matrix with the given activity at the destination.
     *
     * @param builder Term builder of the desired model
     * @param nVars Number of variables in the problem
     * @param mrep Compact markov chain representation of original model.
     * @param expectedTrips Expected trips matrix model to which the new terms are added
     * @param vMatrix Transition matrix containing variable terms
     * @param relevantODs ActivityType for which the gravity model will be variable
     * @param iThresh Performance parameter. @see de.uniwuerzburg.omod.calibration.surrogate.SGGravity.buildDiffModel
     * @param activity Activity at the destination of the trip matrix
     */
    fun <T, K> addE(
        builder: TermBuilder<T, K>,
        nVars: Int,
        mrep: SGCompactMatrixRep,
        expectedTrips: List<List<T>>,
        vMatrix: List<List<K>>,
        relevantODs: Set<Pair<Int, Int>>,
        iThresh: Double,
        activity: ActivityType
    ) {
        val tActivity = if (activity == ActivityType.BUSINESS) {
            ActivityType.OTHER // Edge case: Use other type transition matrix for business activity
        } else {
            activity
        }

        val n = core.omosim.grid.size
        val pCar = mrep.pCar[tActivity]!!
        val mPriorCnst  = mrep.mPriorCnst[activity]!!
        val mPriorVar   = mrep.mPriorVar[activity]!!
        val mPriorVarT  = mPriorVar.transpose()

        // In the case that the segment starts at vActivity
        val vStart = List(n) { builder.new(nVars) }

        // Transition matrix
        val tMatrix = if (activity == ActivityType.HOME) {
            mk.identity<Double>(n)
        } else {
            mrep.tMatrices[tActivity]!!
        }

        // Expected trip contribution unaffected by vActivity
        val mFix = when(activity) {
            in core.fixActivities -> mPriorCnst.transpose().dot(tMatrix) // F = (K^T)A
            mrep.vActivity -> mk.zeros<Double>(n, n) // Not used
            else -> {
                // CASE: vActivity is flexible but not the destination
                // F = diag(iK) A
                val ones = mk.ones<Double>(1, n)
                val left = ones.dot(mPriorCnst).diagonal()
                left.dot(tMatrix)
            }
        }

        // Expected trip contribution affected by vActivity
        val mVar = when(activity) {
            in core.fixActivities -> {
                when (mrep.vActivity) {
                    activity -> {
                        // For segments that started at vActivity: V = (vK)^T
                        // Here we only build v (vStart)
                        val h = mrep.h.flatten()
                        for (col in 0 until n) {
                            for (row in 0 until n) {
                                builder.addVar(vStart[col], vMatrix[row][col], h[row])
                            }
                        }
                        // For other segments: V = (K^T)X
                        val left  = mPriorCnst.transpose()
                        val right = mk.identity<Double>(n)
                        builder.fromMatrixMult(
                            nVars, vMatrix, left, right, transpose=false, relevantRCs=relevantODs, cTol=iThresh
                        )
                        builder.fromMatrixMult(nVars, vMatrix, mk.zeros<Double>(n, n), mk.zeros<Double>(n, n), relevantRCs=relevantODs, cTol=iThresh) // TODO
                    }
                    in core.fixActivitiesNotHome -> {
                        // V = ( ( diag(h)XK )^T ) A
                        val left  = mPriorVarT
                        val right = mrep.h.diagonal().transpose().dot(tMatrix)
                        builder.fromMatrixMult(nVars, vMatrix, left, right, relevantRCs=relevantODs, cTol=iThresh)
                        builder.fromMatrixMult(nVars, vMatrix, mk.zeros<Double>(n, n), mk.zeros<Double>(n, n), relevantRCs=relevantODs, cTol=iThresh) // TODO
                    }
                    ActivityType.HOME -> {
                        throw NotImplementedError("Surrogate model dependent on home coefficients is not implemented!")
                    }
                    else -> {
                        // V = ( ( KX )^T ) A
                        val left  = mk.identity<Double>(n)
                        val right = mPriorVarT.dot(tMatrix)
                        builder.fromMatrixMult(nVars, vMatrix, left, right, relevantRCs=relevantODs, cTol=iThresh)
                        builder.fromMatrixMult(nVars, vMatrix, mk.zeros<Double>(n, n), mk.zeros<Double>(n, n), relevantRCs=relevantODs, cTol=iThresh) // TODO
                    }
                }
            }
            in core.flexActivities -> {
                when (mrep.vActivity) {
                    activity -> {
                        // V = diag(iK)X
                        val ones = mk.ones<Double>(1, n)
                        val left = ones.dot(mPriorCnst).diagonal()
                        val right = mk.identity<Double>(n)
                        builder.fromMatrixMult(
                            nVars, vMatrix, left, right, transpose=false, relevantRCs=relevantODs, cTol=iThresh
                        )
                        builder.fromMatrixMult(nVars, vMatrix, mk.zeros<Double>(n, n), mk.zeros<Double>(n, n), relevantRCs=relevantODs, cTol=iThresh) // TODO
                    }
                    in core.fixActivitiesNotHome -> {
                        // V = diag(hXK)A
                        val left = mrep.h
                        val right = mPriorVar
                        builder.fromMatrixMult(
                            nVars, vMatrix, left, right, transpose=false, relevantRCs=relevantODs, cTol=iThresh
                        )
                        builder.fromMatrixMult(nVars, vMatrix, mk.zeros<Double>(n, n), mk.zeros<Double>(n, n), relevantRCs=relevantODs, cTol=iThresh) // TODO
                    }
                    ActivityType.HOME -> {
                        throw NotImplementedError("Surrogate model dependent on home coefficients is not implemented!")
                    }
                    else -> {
                        // V = diag(KX)A
                        val left = mk.ones<Double>(1, n).dot(mPriorVar)
                        val right = mk.identity<Double>(n)
                        builder.fromMatrixMult(
                            nVars, vMatrix, left, right, transpose=false, relevantRCs=relevantODs, cTol=iThresh
                        )
                        builder.fromMatrixMult(nVars, vMatrix, mk.zeros<Double>(n, n), mk.zeros<Double>(n, n), relevantRCs=relevantODs, cTol=iThresh) // TODO
                    }
                }
            }
            else -> { throw IllegalStateException("$activity neither fixed nor flexible") }
        }

        // E += ( F + V ) odot pCar
        val fix = mFix.times(pCar)
        val tMatrixCar = tMatrix.times(pCar)
        val mPriorVarTCar = mPriorVarT.times(pCar)
        for (o in 0 until n) {
            for (d in 0 until n) {
                if (Pair(o, d) !in relevantODs) { continue }
                // F
                //if (mrep.vActivity != activity) {
                //    builder.addConstant(expectedTrips.get(o, d), fix[o, d])
                //}

                // V
                if (mVar.shape().first == 1) {
                    builder.addTerm(expectedTrips[o][d], mVar.get(0, o), tMatrixCar[o,d])
                } else {
                    builder.addTerm(expectedTrips[o][d], mVar.get(o, d), pCar[o, d])
                }
                if ((mrep.vActivity in core.fixActivities) and (mrep.vActivity == activity)){
                    // For segments that started at vActivity: V = (diag(v)K)^T
                    builder.addTerm(expectedTrips[o][d], vStart[o], mPriorVarTCar[o, d])
                }
            }
        }
    }
}