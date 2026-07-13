package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.*
import de.uniwuerzburg.omosim.calibration.CalibrationConstants.MC_SAMPLES
import de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModel
import de.uniwuerzburg.omosim.calibration.objective.SMGravityObjectiveNative
import de.uniwuerzburg.omosim.calibration.objective.SMGravityObjectiveTF
import de.uniwuerzburg.omosim.core.ActivityGeneratorDefault
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import de.uniwuerzburg.omosim.core.models.*
import de.uniwuerzburg.omosim.utils.diagonal
import de.uniwuerzburg.omosim.utils.normalize
import org.jetbrains.kotlinx.multik.api.*
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.asDNArray
import org.jetbrains.kotlinx.multik.ndarray.data.set
import org.jetbrains.kotlinx.multik.ndarray.operations.expandDims
import org.jetbrains.kotlinx.multik.ndarray.operations.plusAssign
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import org.locationtech.jts.geom.Coordinate
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

/**
 * Surrogate model builder for the gravity model surrogate.
 *
 * @param context Calibration context to use. Includes a Simulator (OMoSim) and the traffic count data.
 */
class SurrogateGravity (
    val context: CalibrationContext,
    val mode: Mode? = Mode.CAR_DRIVER
) {
    private val modeChoiceDummy = ModeChoiceDummyForCalibration(context.omosim.parameterReader)
    private val fixActivitiesNotHome = setOf(ActivityType.WORK, ActivityType.SCHOOL)
    private val fixActivities = setOf(ActivityType.HOME) + fixActivitiesNotHome
    private val flexActivities = setOf(ActivityType.OTHER, ActivityType.SHOPPING, ActivityType.BUSINESS)
    val omosim = context.omosim

    init {
        if (omosim.destinationFinder !is DestinationFinderDefault) {
            throw NotImplementedError(
                "Surrogate is not valid for the destination finder " +
                        omosim.destinationFinder.javaClass.simpleName
            )
        }
        if (omosim.activityGenerator !is ActivityGeneratorDefault) {
            throw NotImplementedError(
                "Surrogate is not valid for the activity generator " +
                        omosim.activityGenerator.javaClass.simpleName
            )
        }
    }

    /**
     * Generate compact markov chain representation of original model.
     *
     * Compact representation explanation:
     * - The expected trip matrix can be computed with the probability matrix mPrior (K in the paper)
     * that gives the probability of an agent being in location col given his home location is row.
     * - K depends on the sequence of prior activities. For example: HSSSO
     * - All these sequences that have the same last activity can be grouped:
     *      - O' = HSSSO + HO + HWO + ... etc.
     * - The last activity O will not be included in the groups, since it is always the same and because it might
     * be dependent on the variables. Therefore: O' = HSSS + H + HW + ...
     * - In this grouping we make a distinction between the sequences that contain the variable activity vActivity
     * and those that do not:
     *      - mPriorCnst -> Does not contain vActivity
     *      - mPriorVar  -> Contains vActivity
     *
     * @param vActivity activity type for the variable gravity model
     * @return compact markov chain representation.
     */
    fun generateMarkovChainRep(vActivity: ActivityType) : SGCompactMatrixRep {
        if(vActivity == ActivityType.HOME) {
            throw NotImplementedError("Surrogate model dependent on home coefficients is not implemented!")
        }

        // Transition matrices
        val tMatrices = computeTransitionMatrices()
        val h = tMatrices[ActivityType.HOME]!!
        val mHW = h.diagonal().dot(tMatrices[ActivityType.WORK]!!)    // Work | Home
        val mHS = h.diagonal().dot(tMatrices[ActivityType.SCHOOL]!!)  // School | Home
        val hDiag = h.diagonal()

        // Compact representation:
        val mPriorVar  = ActivityType.entries.associateWith { mk.zeros<Double>(omosim.grid.size, omosim.grid.size) }
        val mPriorCnst = ActivityType.entries.associateWith { mk.zeros<Double>(omosim.grid.size, omosim.grid.size) }

        // Go through each unique fixed-fixed segment
        for ((chain, chainP) in getUniqueChainSegments()) {
            if (chain.size <= 1) { continue }

            val startActivity = chain.first()
            var nextActivity = chain[1]

            // Setup
            var mPostFixed = mk.identity<Double>(omosim.grid.size) // Matrix product of matrices after vActivity that are not vActivity
            var mK: D2Array<Double>      // Location probability distributions given the home location
            var mKExVar: D2Array<Double> // The same but ignoring the var activity
            when(startActivity) {
                ActivityType.HOME -> {
                    mK = hDiag
                    mKExVar = hDiag
                    mPriorCnst[nextActivity]!!.plusAssign(hDiag * chainP)
                }
                ActivityType.WORK -> {
                    mK = mHW
                    mKExVar = mHW
                    if (vActivity == ActivityType.WORK) {
                        mPriorVar[nextActivity]!!.plusAssign(mPostFixed * chainP)
                    } else {
                        mPriorCnst[nextActivity]!!.plusAssign(mHW * chainP)
                    }
                }
                ActivityType.SCHOOL -> {
                    mK = mHS
                    mKExVar = mHS
                    if (vActivity == ActivityType.SCHOOL) {
                        mPriorVar[nextActivity]!!.plusAssign(mPostFixed * chainP)
                    } else {
                        mPriorCnst[nextActivity]!!.plusAssign(mHS * chainP)
                    }
                }
                else -> {
                    throw IllegalStateException("Last fixed activity can not be of type $startActivity !")
                }
            }

            // Run through chain cumulate mPriorVar, mPriorCnst, and mPostFixed
            var next = 2
            var occurred = (startActivity == vActivity) || (nextActivity == vActivity)
            for (activity in chain.drop(1).dropLast(1)) {
                nextActivity = chain[next]
                next += 1

                // Get transition matrix
                val mT = if (activity == ActivityType.BUSINESS) {
                    tMatrices[ActivityType.OTHER]!! // Edge case: Use other type transition matrix for business activity
                } else {
                    tMatrices[activity]!!
                }

                // Variable activity is in the past
                if ((vActivity in fixActivitiesNotHome) and (occurred)) {
                    // Cumulate all matrices after a fixed location activity has occurred
                    mPostFixed = mPostFixed.dot(mT)
                    mPriorVar[nextActivity]!!.plusAssign(mPostFixed * chainP)
                } else if ((vActivity !in fixActivitiesNotHome) and (occurred) and (nextActivity != vActivity)){
                    // Cumulate probability distributions after a flex location activity
                    // Ignores all subsequent vActivities
                    mPriorVar[nextActivity]!!.plusAssign(mKExVar * chainP)
                }
                // vActivity has not yet occured or is ignored
                else {
                    // Normal case cumulate matrices before next activity
                    mPriorCnst[nextActivity]!!.plusAssign(mK.dot(mT) * chainP)
                }

                // Update location probability distributions
                mK = mK.dot(mT)
                if ((vActivity !in fixActivitiesNotHome) and (activity != vActivity)) {
                    mKExVar = mKExVar.dot(mT)
                }

                // Check if vActivity occurs
                if (nextActivity == vActivity) {
                    occurred = true
                }
            }
        }

        return SGCompactMatrixRep(h, mPriorVar, mPriorCnst, tMatrices, getPMode(mode), vActivity)
    }

    /**
     * Compact markov chain representation of original model.
     *
     * @param h home probability for each location
     * @param mPriorVar @see de.uniwuerzburg.omod.calibration.surrogate.SGGravity.generateMarkovChainRep
     * @param mPriorCnst @see de.uniwuerzburg.omod.calibration.surrogate.SGGravity.generateMarkovChainRep
     * @param tMatrices Current transition matrices for each activity
     * @param pCar mode share of car for each transition
     * @param vActivity activity type for the variable gravity model
     */
    data class SGCompactMatrixRep (
        val h: D2Array<Double>,
        val mPriorVar: Map<ActivityType, D2Array<Double>>,
        val mPriorCnst: Map<ActivityType,  D2Array<Double>>,
        val tMatrices: Map<ActivityType,  D2Array<Double>>,
        val pCar: Map<ActivityType,  D2Array<Double>>,
        val vActivity: ActivityType
    )

    /**
     * Compute the current transition matrices based on the gravity models.
     *
     * @return Transition matrices. HOME: 1xn vector, Rest: nxn matrix
     */
    private fun computeTransitionMatrices() : Map<ActivityType,  D2Array<Double>> {
        val tMatrices = mutableMapOf<ActivityType,  D2Array<Double>>()
        val finder = omosim.destinationFinder as DestinationFinderDefault

        // HOME. A vector.
        val homeWeights = finder.getWeightsNoOrigin(omosim.grid, activityType=ActivityType.HOME).toMutableList()
        if (!omosim.populateBufferArea) { // Handle buffer area
            for ((i, cell) in omosim.grid.withIndex()) {
                if (!cell.inFocusArea) {
                    homeWeights[i] = 0.0
                }
            }
        }
        val h = mk.ndarray( homeWeights.normalize()!! )
            .expandDims(0).asDNArray().asD2Array()
        tMatrices[ActivityType.HOME] = h

        // Transition matrices
        for (activityType in ActivityType.entries) {
            if (activityType == ActivityType.HOME) { continue }

            val mT = mk.zeros<Double>(omosim.grid.size, omosim.grid.size)
            if (finder.forcedTransitionMatrix.containsKey(activityType)) { // Handle forced matrix
                val mWeights = finder.forcedTransitionMatrix[activityType]!!
                for ((o, cell) in omosim.grid.withIndex()) {
                    val weights = mWeights[cell]!!
                    mT[o] = mk.ndarray( weights.normalize()!! )
                }
            } else { // Normal case
                for (o in omosim.grid.indices) {
                    val weights = finder.getWeights(omosim.grid[o], omosim.grid, activityType=activityType)
                    mT[o] = mk.ndarray( weights.normalize()!! )
                }
            }
            tMatrices[activityType] = mT
        }
        return tMatrices
    }

    /**
     * Determine all unique fixed-fixed chain segments and their probabilities of occurrence.
     *
     * @return unique chain segments with probabilities
     */
    private fun getUniqueChainSegments() : List<ChainSegment> {
        val activityGenerator = omosim.activityGenerator as ActivityGeneratorDefault

        // Get all activity chains
        val allChains = mutableMapOf<List<ActivityType>, Double>()
        for (stratum in omosim.popStrata) {
            if (stratum.stratumShare == 0.0) { continue }

            for ((socioFeatureSet, pSFSet) in stratum.iterateOptions()) {
                if (pSFSet == 0.0) { continue }

                // Get chains for that stratum
                val ageGrp = AgeGrp.fromInt(socioFeatureSet.age)
                val chains = activityGenerator.getChain(
                    context.weekday, socioFeatureSet.hom, socioFeatureSet.mob, ageGrp, ActivityType.HOME
                )
                val chainProbs = chains.weights.normalize()!!.toTypedArray()

                // Cumulate probabilities
                for ((chain, chainP) in chains.chains.zip(chainProbs)) {
                    val p = stratum.stratumShare * pSFSet * chainP
                    allChains[chain] = allChains.getOrDefault(chain, 0.0) + p
                }
            }
        }

        // Determine fixed-fixed chain segments
        val segments = mutableListOf<ChainSegment>()
        for ((chain, chainP) in allChains) {
            val currentSegment = mutableListOf<ActivityType>()

            for ((i, activity) in chain.withIndex()) {
                currentSegment.add(activity)
                if (currentSegment.size > 1) {
                    // Reached fixed or last activity -> Segment finished
                    if ((activity in fixActivities) or (i == chain.size - 1)) {
                        segments.add(
                            ChainSegment(
                                currentSegment.toList(),
                                chainP
                            )
                        )
                        currentSegment.clear()
                        currentSegment.add(activity)  // New segment starts with end activity of this segment
                    }
                }
            }
        }

        // Get unique segments and cumulate probabilities
        val uniqueSegments = segments.groupBy { it.chain }.map { (chain, segments) ->
            ChainSegment(
                chain,
                segments.sumOf { it.probability }
            )
        }
        return uniqueSegments
    }

    /**
     * Fixed-fixed activity chain segment with probability of occurrence
     */
    private data class ChainSegment(
        val chain: List<ActivityType>,
        val probability: Double
    )

    private fun getPMode(mode: Mode?) : Map<ActivityType, D2Array<Double>> {
        return when(mode) {
            Mode.CAR_DRIVER -> getPCar(context.weekday)
            null -> ActivityType.entries.associateWith { // All Modes
                mk.ones<Double>(omosim.grid.size, omosim.grid.size)
            }
            else -> throw IllegalArgumentException("SGGravity: Mode $mode not supported!")
        }
    }
    /**
     * Get probability matrix for the car mode. Each entry gives the probability that the origin-destination trip
     * given by o=row and d=col is by car.
     *
     * @param weekday Weekday
     * @return Probability matrix for each activity type.
     */
    private fun getPCar(weekday: Weekday = Weekday.UNDEFINED) : Map<ActivityType, D2Array<Double>> {
        val finder = omosim.destinationFinder as DestinationFinderDefault

        val pCar = mutableMapOf<ActivityType, D2Array<Double>>()
        for (activity in ActivityType.entries) {
            pCar[activity] = mk.zeros<Double>(omosim.grid.size, omosim.grid.size)
        }

        // Dummy location for home, work, and school of dummy agent. Irrelevant for mode choice.
        val dummyCoord = Coordinate(0.0,0.0)
        val dummyLocation = DummyLocation(dummyCoord, dummyCoord, null, setOf())

        for (stratum in omosim.popStrata) {
            if (stratum.stratumShare == 0.0) { continue }

            for ((socioFeatureSet, pSFSet) in stratum.iterateOptions()) {
                if (pSFSet == 0.0) { continue }

                // Agent representing the population stratum
                val stratumAgent = MobiAgent(
                    -1,  socioFeatureSet.hom, socioFeatureSet.mob, socioFeatureSet.age,
                    dummyLocation, dummyLocation, dummyLocation, socioFeatureSet.sex
                )
                stratumAgent.carAccess = true // Car ownership probability is considered later

                // Car ownership probability
                val carOwnershipP = omosim.carOwnership.probability(stratumAgent, stratum)
                if (carOwnershipP == 0.0) { continue }

                for (activity in ActivityType.entries) {
                    val pCarActivity = mk.zeros<Double>(omosim.grid.size, omosim.grid.size)
                    for (o in omosim.grid.indices) {
                        val distances = finder.routingCache.getDistances(omosim.grid[o], omosim.grid)
                        for (d in omosim.grid.indices) {
                            val weights = modeChoiceDummy.utilitiesForCalibration(
                                distances[d].toDouble() / 1000.0, stratumAgent, activity, weekday
                            )
                            val pTrip = weights[0] / weights.sum()
                            pCarActivity[o, d] = stratum.stratumShare * pSFSet * carOwnershipP * pTrip
                        }
                    }
                    pCar[activity]?.plusAssign(pCarActivity)
                }
            }
        }
        return pCar
    }

    /**
     * Sample simulation to determine the distribution of trips across the time of day separated into T time slices.
     *
     * @see de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
     *
     * @n Sample size
     * @param weekday Weekday
     * @return Distributions where key is of size T
     */
    @Suppress("SameParameterValue")
    fun monteCarloTripStartDistribution(n: Int, weekday: Weekday = Weekday.UNDEFINED) : Map<ActivityType, DoubleArray> {
        val distr = ActivityType.entries.associateWith {
            DoubleArray(T) { 0.0 }
        }.toMutableMap()

        // Ensure results are deterministic
        omosim.mainRng.setSeed(0)

        // Run Simulation
        val agents = omosim.run(n, verbose = false, start_wd = weekday)
        omosim.doModeChoice(agents, ModeChoiceOption.FAST, false, false)

        // Determine counts at sensors
        val visitor: TripVisitor = { _, _, destinationActivity, departureTime, _, _ ->
            val arr = distr[destinationActivity.type]!!
            val i = departureTime.determineTimeSlice()
            arr[i] = arr[i] + 1
        }
        for (agent in agents) {
            agent.mobilityDemand[0].visitTrips(visitor)
        }

        // Normalize
        for ((key, arr) in distr.entries) {
            distr[key] = arr.normalize() ?: DoubleArray(arr.size) { 0.0 }
        }

        return distr
    }

    /**
     * Determine od-Pairs that contribute to sensor measurements.
     *
     * @param affectedSensors Gives all sensors that are affected by a certain origin-destination pair
     * @return Set of relevant od-Pairs
     */
    fun getRelevantODs(
        affectedSensors: Map<Pair<RealLocation, RealLocation>, List<TrafficSensor>>,
    ): Set<Pair<Int, Int>> {
        val relevantODs = mutableSetOf<Pair<Int, Int>>()
        for ((o, origin) in omosim.grid.withIndex()) {
            for ((d, destination) in omosim.grid.withIndex()) {
                val od = Pair(origin, destination)
                if (od in affectedSensors) {
                    if (affectedSensors[od]!!.isNotEmpty()) {
                        relevantODs.add(Pair(o, d))
                    }
                }
            }
        }
        return relevantODs
    }

    /**
     * Determine expected trips matrix with the given activity at the destination.
     *
     * @param demandBuilder Term builder of the desired model
     * @param mrep Compact markov chain representation of original model.
     * @param expectedTrips Expected trips matrix model to which the new terms are added
     * @param vMatrix Transition matrix containing variable terms
     * @param relevantODs ActivityType for which the gravity model will be variable
     * @param iThresh Performance parameter. @see de.uniwuerzburg.omod.calibration.surrogate.SGGravity.buildDiffModel
     * @param activity Activity at the destination of the trip matrix
     */
    fun <M_IN, M_OUT> addE(
        demandBuilder: DemandBuilder<M_IN, M_OUT>,
        mrep: SGCompactMatrixRep,
        expectedTrips: M_OUT,
        vMatrix: M_IN,
        relevantODs: Set<Pair<Int, Int>>,
        iThresh: Double,
        activity: ActivityType
    ) : M_OUT {
        var expectedTrips = expectedTrips

        val tActivity = if (activity == ActivityType.BUSINESS) {
            ActivityType.OTHER // Edge case: Use other type transition matrix for business activity
        } else {
            activity
        }

        val n = omosim.grid.size
        val pCar = mrep.pCar[tActivity]!!
        val mPriorCnst  = mrep.mPriorCnst[activity]!!
        val mPriorVar   = mrep.mPriorVar[activity]!!
        val mPriorVarT  = mPriorVar.transpose()

        // In the case that the segment starts at vActivity
        var vStart: M_OUT? = null

        // Transition matrix
        val tMatrix = if (activity == ActivityType.HOME) {
            mk.identity<Double>(n)
        } else {
            mrep.tMatrices[tActivity]!!
        }

        // Expected trip contribution unaffected by vActivity
        val mFix = when(activity) {
            in fixActivities -> mPriorCnst.transpose().dot(tMatrix) // F = (K^T)A
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
            in fixActivities -> {
                when (mrep.vActivity) {
                    activity -> {
                        // For segments that started at vActivity: V = (vK)^T
                        // Here we only build v (vStart)
                        vStart = demandBuilder.matrixMult(mrep.h, vMatrix, transpose=false, relevantRCs=null, cTol=iThresh)

                        // For other segments: V = (K^T)X
                        val left  = mPriorCnst.transpose()
                        val right = mk.identity<Double>(n)
                        demandBuilder.matrixMult(
                            left, vMatrix, right, transpose=false, relevantRCs=relevantODs, cTol=iThresh
                        )
                    }
                    in fixActivitiesNotHome -> {
                        // V = ( ( diag(h)XK )^T ) A
                        val left  = mPriorVarT
                        val right = mrep.h.diagonal().transpose().dot(tMatrix)
                        demandBuilder.matrixMult( left, vMatrix, right, transpose=true, relevantRCs=relevantODs, cTol=iThresh)
                    }
                    ActivityType.HOME -> {
                        throw NotImplementedError("Surrogate model dependent on home coefficients is not implemented!")
                    }
                    else -> {
                        // V = ( ( KX )^T ) A
                        val right = mPriorVarT.dot(tMatrix)
                        demandBuilder.matrixMult( vMatrix, right, transpose=true, relevantRCs=relevantODs, cTol=iThresh)
                    }
                }
            }
            in flexActivities -> {
                when (mrep.vActivity) {
                    activity -> {
                        // V = diag(iK)X
                        val ones = mk.ones<Double>(1, n)
                        val left = ones.dot(mPriorCnst).diagonal()
                        demandBuilder.matrixMult( left, vMatrix, transpose=false, relevantRCs=relevantODs, cTol=iThresh)
                    }
                    in fixActivitiesNotHome -> {
                        // V = diag(hXK)A
                        val left = mrep.h
                        val right = mPriorVar
                        demandBuilder.matrixMult( left, vMatrix, right, transpose=false, relevantRCs=relevantODs, cTol=iThresh)
                    }
                    ActivityType.HOME -> {
                        throw NotImplementedError("Surrogate model dependent on home coefficients is not implemented!")
                    }
                    else -> {
                        // V = diag(KX)A
                        val left = mk.ones<Double>(1, n).dot(mPriorVar)
                        demandBuilder.matrixMult( left, vMatrix, transpose=false, relevantRCs=relevantODs, cTol=iThresh)
                    }
                }
            }
            else -> { throw IllegalStateException("$activity neither fixed nor flexible") }
        }

        // E += ( F + V ) odot pCar
        val fix = mFix.times(pCar)
        val tMatrixCar = tMatrix.times(pCar)
        val mPriorVarTCar = mPriorVarT.times(pCar)

        // F
        if (mrep.vActivity != activity) {
            expectedTrips = demandBuilder.add(expectedTrips, fix, relevantODs, cTol=iThresh)
        }

        // V
        if (demandBuilder.shape(mVar).first == 1) {
            expectedTrips = demandBuilder.diagMultAdd(expectedTrips, mVar, tMatrixCar, relevantODs, cTol=iThresh)
        } else {
            expectedTrips = demandBuilder.add(expectedTrips, mVar, pCar, relevantODs, cTol=iThresh)
        }
        if ((mrep.vActivity in fixActivities) and (mrep.vActivity == activity)){
            // For segments that started at vActivity: V = (diag(v)K)^T
            expectedTrips = demandBuilder.diagMultAdd(expectedTrips, vStart!!, mPriorVarTCar, relevantODs, cTol=iThresh)
        }
        return expectedTrips
    }

    /**
     * Build surrogate of OMoSim that is capable of calibrating gravity model parameters.
     * Natively.
     *
     * @param vActivity ActivityType for which the gravity model will be variable
     * @param objective Objective function
     * @param vMatrixBuilder Matrix that defines how the transition matrix of vActivity changes with the parameters.
     * @param iThresh Performance parameter.
     * All terms with coefficients below this value will be ignored and not added to the result.
     * Higher values -> Computes faster but is a rougher approximation of the markov chain representation.
     *
     * @return surrogate model
     */
    fun <M: DifferentiableModel> buildNative(
        vActivity: ActivityType,
        objective: SMGravityObjectiveNative<M>,
        vMatrixBuilder: VMatrixBuilderNative,
        iThresh: Double = 1e-4
    ) : M {
        logger.info("Surrogate (Native): Building native model for activity $vActivity")
        logger.info("Surrogate (Native): Weekday: ${context.weekday}")

        // Core work
        val n = context.omosim.grid.size
        val mrep = generateMarkovChainRep(vActivity) // Compact matrix representation
        val relevantODs = context.getRelevantODs() // Relevant origin-destination pairs for measurements
        val tripStartDistr = monteCarloTripStartDistribution( MC_SAMPLES, context.weekday ) // Temporal trip distribution

        // Transition matrix containing variable terms
        val (vMatrix, nVars) = vMatrixBuilder.build(mrep)

        logger.info("Surrogate (Native): Number of variables: $nVars")

        val demandBuilder = DemandBuilderNative(nVars)

        // Create graph of the expected trips matrix: E(o, d | Car)
        val expectedTrips = ActivityType.entries.associateWith {
            List(n) {
                List(n) {
                    demandBuilder.new()
                }
            }
        }.toMutableMap()

        // Add expected trips for each destination activity
        for (activity in ActivityType.entries) {
            expectedTrips[activity] = addE(
                demandBuilder,
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
        logger.info("Surrogate (Native): Build complete. Number of terms: $terms")

        return model
    }

    /**
     * Build surrogate of OMoSim that is capable of calibrating gravity model parameters.
     * With TensorFlow.
     *
     * @param vActivity ActivityType for which the gravity model will be variable
     * @param objective Objective function
     * @param vMatrixBuilder Matrix that defines how the transition matrix of vActivity changes with the parameters.
     * @return surrogate model
     */
    fun <M: DifferentiableModel> buildTF(
        vActivity: ActivityType,
        objective: SMGravityObjectiveTF<M>,
        vMatrixBuilder: VMatrixBuilderTF
    ) : M {
        logger.info("Surrogate (TF): building tensor flow model for activity $vActivity")
        logger.info("Surrogate (TF): Weekday: ${context.weekday}")

        // Core work
        val n = context.omosim.grid.size
        val mrep = generateMarkovChainRep(vActivity) // Compact matrix representation
        val relevantODs = context.getRelevantODs() // Relevant origin-destination pairs for measurements
        val tripStartDistr = monteCarloTripStartDistribution( MC_SAMPLES, context.weekday ) // Temporal trip distribution

        // Transition matrix containing variable terms
        val bReturn = vMatrixBuilder.build(mrep)
        val vMatrix = bReturn.first
        val modelCore = bReturn.second

        logger.info("Surrogate (TF): Number of variables: ${modelCore.nVars}")

        // Create graph of the expected trips matrix: E(o, d | Car)
        val expectedTrips: MutableMap<ActivityType, Operand<TFloat32>> = ActivityType.entries.associateWith {
            modelCore.tf.zeros(modelCore.tf.constant(intArrayOf(n, n)), TFloat32::class.java)
        }.toMutableMap()

        // Add expected trips for each destination activity
        for (activity in ActivityType.entries) {
            expectedTrips[activity] = addE(
                DemandBuilderTF(modelCore),
                mrep,
                expectedTrips[activity]!!,
                vMatrix,
                relevantODs,
                0.0,
                activity
            )
        }

        // Objective
        val model = objective.build(modelCore, expectedTrips, tripStartDistr)

        // Logging
        val ops = model.getSize()
        logger.info("Surrogate (TF): Build complete. Number of operations: $ops")

        return model
    }
}


