package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.core.Omosim

interface CalibrationContext {
    val omosim: Omosim
    val totalPopulation: Double

    /**
     * Determine od-Pairs that are relevant for calibration.
     *
     * @return Set of relevant od-Pairs
     */
    fun getRelevantODs() : Set<Pair<Int, Int>>
}