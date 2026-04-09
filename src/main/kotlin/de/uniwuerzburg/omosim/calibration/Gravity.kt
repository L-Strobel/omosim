package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
import de.uniwuerzburg.omosim.calibration.algorithms.*
import de.uniwuerzburg.omosim.calibration.differentiablemodel.*
import de.uniwuerzburg.omosim.calibration.surrogate.*
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.Cell
import org.jetbrains.kotlinx.multik.ndarray.operations.toArray

/**
 * Calibrate OMoSim output by adjusting the gravity model.
 * The gravity model is governed by the following equation:
 *
 *  P(i) = (c_i * A_i * f(od)) / sum_I(c_i * A_i * f(od))
 *
 *  where:
 *      c_i is a scaler determined by the calibration
 *      A_i is the attraction of the location i
 *      f(od) is the deterrence function
 *      I is the set of all locations. In this case, all aggregated locations (omosim.grid).
 *
 * The calibration process attempts to determine the best values for c_i to reproduce the traffic counts.
 * The deterrence function is not altered.
 *
 * @param context Calibration context to use. Includes a Simulator (OMoSim) and the traffic count data.
 */
class Gravity(
    private val context: TrafficCountCalibrationContext
) {
    private val rw = RunWrappers()
    private val o = Objectives()

    /**
     * Run calibration.
     *
     * Will immediately alter the attraction values of the buildings.
     *
     * @param algorithm Algorithm to use
     * @param activities List of activities for which the gravity model will be optimized
     * @param parameters Additional parameters passed to the optimization algorithm
     */
    fun calibrate(algorithm: CalibrationAlgorithm?, activities: List<ActivityType>, parameters: Map<String, String>?) {
        when (algorithm) {
            CalibrationAlgorithm.SM_LBFGS  -> rw.calibrateLBFGSSM(activities, parameters)
            CalibrationAlgorithm.SM_GD     -> rw.calibrateGGSM(activities, parameters)
            CalibrationAlgorithm.SM_PSO    -> rw.calibratePSOSM(activities, parameters)
            CalibrationAlgorithm.PSO       -> rw.calibratePSO(activities, parameters)
            CalibrationAlgorithm.PSO_AO    -> rw.calibratePSOAllAtOnce(activities, parameters)
            CalibrationAlgorithm.SM_SPSA   -> rw.calibrateSPSASM(activities, parameters)
            CalibrationAlgorithm.SPSA      -> rw.calibrateSPSA(activities, parameters)
            CalibrationAlgorithm.SPSA_AO   -> rw.calibrateSPSAAllAtOnce(activities, parameters)
            CalibrationAlgorithm.SM_WSPSA  -> rw.calibrateWSPSASM(activities, parameters)
            CalibrationAlgorithm.WSPSA     -> rw.calibrateWSPSA(activities, parameters)
            //CalibrationAlgorithm.SM_MATRIX -> rw.calibrateMatrix(activities) // TODO
            null -> throw IllegalArgumentException("Algorithm can't be null for Gravity model calibration!")
            else -> throw IllegalArgumentException("Not Implemented") // TODO Remove
        }
    }

    /**
     * Update gravity model of one activity.
     *
     * @param x Calibration result
     * @param activity Activity
     */
    private fun updateCalibration(x: DoubleArray, activity: ActivityType) {
        val dcFunction = context.finder.locChoiceWeightFuns[activity]!!
        for ((cell, v) in context.omosim.grid.zip(x.toTypedArray())) {
            cell.updateAttractionScaler(dcFunction, v)
        }
    }

    /**
     * Update gravity model of multiple activities.
     *
     * @param x Calibration result
     * @param activities Activities
     */
    private fun updateCalibration(x: DoubleArray, activities: List<ActivityType>) {
        for ((ai, activity) in activities.withIndex()) {
            val dcFunction = context.finder.locChoiceWeightFuns[activity]!!
            for ((gi, cell) in context.omosim.grid.withIndex()) {
                cell.updateAttractionScaler(dcFunction, x[ai * context.omosim.grid.size + gi])
            }
        }
    }

    /**
     * Wrappers for different optimization algorithms.
     *
     * Generally have the following responsibilities:
     *  1. Create surrogate model if required.
     *  2. Create objective function.
     *  3. Create initial guess for calibration parameters.
     *  4. Run optimization.
     *  5. Safe calibration result.
     *
     *  These are slightly different for most algorithms. Therefore, the run wrappers are not unified into a single
     *  interface at this point.
     */
    private inner class RunWrappers {
        // L-BFGS
        fun calibrateLBFGSSM(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        )  {
            for (activity in activities) {
                val model = SGGravityNative(context, TrafficCountSSE(context), TrafficCountVMatrixBuilder(context))
                    .build(activity)
                val x0 = DoubleArray(context.omosim.grid.size - 1) { 1.0 }
                var d =  BFGS.run(model, x0, parameters=parameters)
                d = (d.toList() + listOf(1.0)).toDoubleArray()
                updateCalibration(d, activity)
            }
        }

        // Gradient Descent
        fun calibrateGGSM(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        ){
            for (activity in activities) {
                val model = SGGravityNative(context, TrafficCountSSE(context), TrafficCountVMatrixBuilder(context))
                    .build(activity)
                val x0 = DoubleArray(context.omosim.grid.size - 1) { 1.0 }
                var d = GradientDescent.run(model, x0, parameters=parameters)
                d = (d.toList() + listOf(1.0)).toDoubleArray()
                updateCalibration(d, activity)
            }
        }

        // PSO
        fun calibratePSOSM(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        ){
            for (activity in activities) {
                val objective = o.surrogateObj(activity)
                var d = PSO.run(
                    context.omosim.grid.size - 1,
                    objective,
                    parameters = parameters,
                    nWorker = context.omosim.nWorker
                )
                d = (d.toList() + listOf(1.0)).toDoubleArray()
                updateCalibration(d, activity)
            }
        }
        fun calibratePSO(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        ) {
            for (activity in activities) {
                val objective = o.batchObj(activity)
                val d = PSO.run(
                    context.omosim.grid.size,
                    objective,
                    parameters = parameters,
                    nWorker = context.omosim.nWorker
                )
                updateCalibration(d, activity)
            }
        }
        fun calibratePSOAllAtOnce(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        ) {
            val objective = o.batchObj(activities)
            val d = PSO.run(
                context.omosim.grid.size * activities.size,
                objective,
                parameters = parameters,
                nWorker = context.omosim.nWorker
            )
            updateCalibration(d, activities)
        }

        // SPSA
        fun calibrateSPSASM(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        ) {
            for (activity in activities) {
                val objective = o.surrogateObj(activity)
                val x0 = DoubleArray(context.omosim.grid.size - 1) { 1.0 }
                var d = SPSA.run(x0, objective, parameters = parameters)

                d = (d.toList() + listOf(1.0)).toDoubleArray()
                updateCalibration(d, activity)
            }
        }
        fun calibrateSPSAAllAtOnce(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        ) {
            val objective = o.batchObj(activities)
            val x0 = DoubleArray(context.omosim.grid.size * activities.size) { 1.0 }
            val d = SPSA.run(x0, objective, parameters = parameters)
            updateCalibration(d, activities)
        }
        fun calibrateSPSA(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        ) {
            for (activity in activities) {
                val objective = o.batchObj(activity)
                val x0 = DoubleArray(context.omosim.grid.size ) { 1.0 }
                val d = SPSA.run(x0, objective, parameters = parameters)
                updateCalibration(d, activity)
            }
        }

        // W-SPSA
        fun calibrateWSPSASM(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        ) {
            val measurements = context.sensors.map { it.measurements }.flatMap { it.toList() }

            for (activity in activities) {
                val model = SGGravityNative(context, TrafficCountSeparate(context), TrafficCountVMatrixBuilder(context)).build(activity)
                val objective = o.surrogateObjWSPSA(model, context.sensors)
                val x0 = DoubleArray(context.omosim.grid.size - 1) { 1.0 }
                var d = WSPSA.run(
                    x0, objective, measurements, model, parameters = parameters
                )
                d = (d.toList() + listOf(1.0)).toDoubleArray()
                updateCalibration(d, activity)
            }
        }
        fun calibrateWSPSA(
            activities: List<ActivityType>, parameters: Map<String, String>? = null
        ) {
            val measurements = context.sensors.map { it.measurements }.flatMap { it.toList() }

            for (activity in activities) {
                val model = SGGravityNative(context, TrafficCountSeparate(context), TrafficCountVMatrixBuilder(context)).build(activity)
                val objective = o.batchObjWSPSA(activity)
                val x0 = DoubleArray(context.omosim.grid.size - 1) { 1.0 }
                var d = WSPSA.run(
                    x0, objective, measurements, model, parameters = parameters
                )
                d = (d.toList() + listOf(1.0)).toDoubleArray()
                updateCalibration(d, activity)
            }
        }

        /*fun calibrateMatrix(activities: List<ActivityType>) {
            for (activity in activities) {
                val model = SGGravity(context, TrafficCountSSE, TrafficCountVMatrixBuilder, GRBLinExprBuilder)
                val wm = model.optimizeTMatrix(activity)

                val finder = context.omosim.destinationFinder as DestinationFinderDefault
                val force = mutableMapOf<Cell, DoubleArray>()
                for ((i, cell) in context.omosim.grid.withIndex()) {
                    force[cell] = wm!!.toArray()[i]
                }
                finder.forcedTransitionMatrix[activity] = force
            }
        }*/
    }

    /**
     * Objective functions builders for the Gravity model calibration.
     */
    private inner class Objectives {
        /**
         * Sum of squares objective using the surrogate model.
         */
        fun surrogateObj(activity: ActivityType): (DoubleArray) -> Double {
            val model = SGGravityNative(context, TrafficCountSSE(context), TrafficCountVMatrixBuilder(context)).build(activity)
            return { x: DoubleArray ->
                model.evaluate(x)
            }
        }

        /**
         * Special case of the sum of squares objective using the surrogate model for WSPSA.
         * WSPSA requires that the simulated counts at each traffic counting station are returned separately.
         */
        fun surrogateObjWSPSA(model: DifferentiableModelMV, sensors: List<TrafficSensor>): (DoubleArray) ->
        Pair<Double, DoubleArray> {
            return { x: DoubleArray ->
                val simCounts = model.evaluate(x)

                val flows = mutableMapOf<TrafficSensor, DoubleArray>()
                var i = 0
                for (sensor in sensors) {
                    val sensorCounts = DoubleArray(T) {0.0}
                    for (t in 0 until T) {
                        sensorCounts[t] = simCounts[i]
                        i += 1
                    }
                    flows[sensor] = sensorCounts
                }
                Pair(context.sse(flows), simCounts)
            }
        }

        /**
         * Sum of squared objective computed with a simulation run of 10% of the population.
         *
         * For a run where multiple activities are calibrated at once.
         */
        fun batchObj(activities: List<ActivityType>): (DoubleArray) -> Double {
            return { x: DoubleArray ->
                for ((ai, activity) in activities.withIndex()) {
                    val dcFunction = context.finder.locChoiceWeightFuns[activity]!!
                    for ((gi, cell) in context.omosim.grid.withIndex()) {
                        cell.updateAttractionScaler(dcFunction, x[ai * context.omosim.grid.size + gi])
                    }
                }
                val flows = context.runBatch(0.1)
                context.sse(flows)
            }
        }

        /**
         * Sum of squared objective computed with a simulation run of 10% of the population.
         *
         * For a run where only one activity is calibrated.
         */
        fun batchObj(activity: ActivityType): (DoubleArray) -> Double {
            return { x: DoubleArray ->
                val dcFunction = context.finder.locChoiceWeightFuns[activity]!!
                for ((i, cell) in context.omosim.grid.withIndex()) {
                    cell.updateAttractionScaler(dcFunction, x[i])
                }
                val flows = context.runBatch(0.1)
                context.sse(flows)
            }
        }
        /**
         * Sum of squared objective computed with a simulation run of 10% of the population.
         *
         * For a run where only one activity is calibrated.
         *
         * Special case for WSPSA.
         * WSPSA requires that the simulated counts at each traffic counting station are returned separately.
         */
        fun batchObjWSPSA(activity: ActivityType): (DoubleArray) -> Pair<Double, DoubleArray> {
            return { xTmp: DoubleArray ->
                val x = (xTmp.toList() + listOf(1.0)).toDoubleArray()
                val dcFunction = context.finder.locChoiceWeightFuns[activity]!!
                for ((i, cell) in context.omosim.grid.withIndex()) {
                    cell.updateAttractionScaler(dcFunction, x[i])
                }
                val flows = context.runBatch(0.1)
                context.sse(flows)

                val countsFlat = mutableListOf<Double>()
                for (sensor in context.sensors) {
                    for (t in 0 until T) {
                        countsFlat.add( flows[sensor]!![t] )
                    }
                }

                Pair(context.sse(flows), countsFlat.toDoubleArray())
            }
        }
    }
}
