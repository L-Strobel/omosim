package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.CalibrationConstants.MC_SAMPLES
import de.uniwuerzburg.omosim.calibration.CalibrationContext
import de.uniwuerzburg.omosim.calibration.SGGravityObjectiveNative
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.DifferentiableModel
import de.uniwuerzburg.omosim.calibration.logger
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.Mode

class SGGravityNative<M: DifferentiableModel> (
    val context: CalibrationContext,
    val objective: SGGravityObjectiveNative<M>,
    val vMatrixBuilder: VMatrixBuilderNative,
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
            expectedTrips[activity] = core.addE(
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
        logger.info("Building surrogate complete. Number of terms: $terms")

        return model
    }
}