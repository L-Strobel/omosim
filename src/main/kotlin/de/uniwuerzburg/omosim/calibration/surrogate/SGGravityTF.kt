package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.CalibrationConstants.MC_SAMPLES
import de.uniwuerzburg.omosim.calibration.CalibrationContext
import de.uniwuerzburg.omosim.calibration.SGGravityObjectiveTF
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.calibration.logger
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.Mode
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

class SGGravityTF(
    val context: CalibrationContext,
    val objective: SGGravityObjectiveTF,
    val vMatrixBuilder: VMatrixBuilderTF,
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
        val expectedTrips: MutableMap<ActivityType, Operand<TFloat32>> = ActivityType.entries.associateWith {
            model.tf.zeros(model.tf.constant(intArrayOf(n, n)), TFloat32::class.java);
        }.toMutableMap()

        // Add expected trips for each destination activity
        for (activity in ActivityType.entries) {
            expectedTrips[activity] = core.addE(
                DemandBuilderTF(model),
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