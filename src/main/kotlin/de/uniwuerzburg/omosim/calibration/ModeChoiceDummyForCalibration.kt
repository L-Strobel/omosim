package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.core.ModeUtility
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.Weekday
import de.uniwuerzburg.omosim.io.ParameterReader
import de.uniwuerzburg.omosim.io.json.readJsonFromResource
import kotlin.math.exp

/**
 * TEMPORARY. Trip based mode choice for calibration only.
 * Is based on the default calibration.
 *
 * This version is static and will not be affected by other calibration runs.
 */
class ModeChoiceDummyForCalibration(
    parameterReader: ParameterReader
) {
    private val tripModeOptions = parameterReader.getTripModeUtilitiesCalibration()

    fun utilitiesForCalibration(
        carDistance: Double, agent: MobiAgent, activity: ActivityType, weekday: Weekday
    ) : DoubleArray {
        val weights = tripModeOptions
            .map { util -> exp(util.calc(null, carDistance, activity, agent.carAccess, weekday, agent)) }
            .toDoubleArray()
        return weights
    }
}