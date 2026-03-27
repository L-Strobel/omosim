package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.ModeChoiceOption

interface CalibrationContext {
    val omosim: Omosim
    val totalPopulation: Double

    /**
     * Determine od-Pairs that are relevant for calibration.
     *
     * @return Set of relevant od-Pairs
     */
    fun getRelevantODs() : Set<Pair<Int, Int>>

    /**
     * Simulate a sample of the population.
     *
     * @param sharePop Share of population to use.
     * @return Agents
     */
    fun runBatchAgents(sharePop: Double) : List<MobiAgent> {
        omosim.mainRng.setSeed(0)  // Ensure results are deterministic

        // Run Simulation
        val agents = if (omosim.censusAvailable) {
            omosim.run(sharePop, verbose = false)
        } else {
            omosim.run((sharePop * totalPopulation).toInt(), verbose = false)
        }
        omosim.doModeChoice(agents, ModeChoiceOption.FAST, false, verbose = false)

        return agents
    }

    fun initTotalPopulation() : Double {
        return if (omosim.censusAvailable) {
            omosim.buildings.sumOf { it.population }
        } else {
            val estimate = omosim.buildings.size * 3.0
            logger.warn(
                "Population size not available. Please supply the population size with --calibration_population or " +
                        "with a census file. Falling back to population size estimate of %.3g".format(estimate))
            estimate
        }
    }
}