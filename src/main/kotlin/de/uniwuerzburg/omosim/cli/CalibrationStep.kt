package de.uniwuerzburg.omosim.cli

import de.uniwuerzburg.omosim.calibration.CalibrationAlgorithm
import de.uniwuerzburg.omosim.calibration.CalibrationType
import de.uniwuerzburg.omosim.core.models.ActivityType

/**
 * Calibration step to undertake. The user can define which calibration are supposed to be done in what order.
 *
 * TYPE:
 *  - GRAVITY: Calibrate attraction scalers of gravity model to fine tune destination choice.
 *  - MODE_CHOICE: Calibrate car mode intercept of mode choice model
 *  - ROUTE_CHOICE: Define probabilities for alternative route options between an origin and a destination
 *  - EVALUATE: Conduct a test run with the current calibration and compare the result to the baseline.
 *
 * @param type See above
 * @param alg Optimization algorithm to use
 * @param activities Only for GRAVITY. Defines for which activities the gravity model is calibrated
 * @param parameters Additional parameters passed to the optimization algorithm
 */
class CalibrationStep (
    val type: CalibrationType?,
    val alg: CalibrationAlgorithm?,
    val activities: List<ActivityType>,
    val parameters: Map<String, String>
) {
    companion object {
        /**
         * Format = TYPE:ALG:ACTIVITY?,..:PARAMS
         */
        fun fromCLIString(str: String) : CalibrationStep {
            val components = str.split(":")
            require(components.size >= 4) {
                "Calibration step definition must contain 3 colons. " +
                "If you want to use the default value for a section leave the section empty." +
                "Format: TYPE:ALG:ACTIVITY,..:PARAMS" +
                "Example: GRAVITY:SM_PSO:WORK,OTHER:iterations=100:lb=0.2"
            }

            // TYPE
            val type = if (components[0] == "") {
                null
            } else {
                try {
                    CalibrationType.valueOf(components[0])
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException()
                }
            }
            if (type == CalibrationType.ROUTE_CHOICE) {
                throw IllegalArgumentException("Route Choice calibration currently not supported!")
            }

            // ALG
            val alg = if (components[1] == "") {
                // Default value
                if (type == CalibrationType.GRAVITY) {
                    CalibrationAlgorithm.SM_PSO // Particle swarm optimization
                } else if (type == CalibrationType.ROUTE_CHOICE) {
                    CalibrationAlgorithm.SM_GD // Gradient descent
                } else {
                    null
                }
            } else {
                CalibrationAlgorithm.valueOf(components[1])
            }

            // Activities
            val activities = if (components[2] == "") {
                listOf()
            } else {
                components[2].split(",").map {  ActivityType.valueOf(it) }
            }

            // Optimization Parameters
            val parameters = mutableMapOf<String, String>()
            for (i in 3 until components.size) {
                if (components[i] == "") { continue }
                val kv = components[i].split("=")
                require(kv.size == 2) {
                    "Parameter ${components[i]} is incorrectly formatted. Format ParameterName=Value"
                }
                parameters[kv[0]] = kv[1]
            }

            return CalibrationStep(type, alg, activities, parameters)
        }
    }
}