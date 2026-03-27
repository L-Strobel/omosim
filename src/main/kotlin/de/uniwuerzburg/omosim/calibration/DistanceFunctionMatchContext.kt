package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.calibration.algorithms.BFGS
import de.uniwuerzburg.omosim.calibration.algorithms.GradientDescent
import de.uniwuerzburg.omosim.calibration.surrogate.DistanceFunctionVMatrixBuilder
import de.uniwuerzburg.omosim.calibration.surrogate.SGGravity
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import de.uniwuerzburg.omosim.core.LogNormDCUtil
import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.ModeChoiceOption

class DistanceFunctionMatchContext(
    val mean: Double,
    override val omosim: Omosim,
    val activity: ActivityType,
    population: Double? = null
) : CalibrationContext {
    override val totalPopulation: Double

    init {
        // TODO dedup
        // Total population in area. Used to scale the estimated traffic counts.
        totalPopulation = if (population != null) {
            population
        } else if (omosim.censusAvailable) {
            omosim.buildings.sumOf { it.population }
        } else {
            val estimate = omosim.buildings.size * 3.0
            logger.warn(
                "Population size not available. Please supply the population size with --calibration_population or " +
                        "with a census file. Falling back to population size estimate of %.3g".format(estimate))
            estimate
        }
    }

    fun calibrate() {
        evaluate()
        val model = SGGravity(this, DFMatch, DistanceFunctionVMatrixBuilder).build(activity)
        val x0 = doubleArrayOf( -1.384e-01, -1.225e+00) // TODO adapt
        var d = BFGS.run(model, x0, mapOf("lr0" to "0.001", "ub" to "0.0", "lb" to "-1000.0"))
        print(d.toList())
        var dcFn = (omosim.destinationFinder as DestinationFinderDefault).locChoiceWeightFuns[activity]
        dcFn = (dcFn as LogNormDCUtil)
        dcFn.coeff0 = d[0]
        dcFn.coeff1 = d[1]
        evaluate()
    }

    override fun getRelevantODs(): Set<Pair<Int, Int>> {
        val relevantODs = mutableSetOf<Pair<Int, Int>>()
        for (o in 0 until this.omosim.grid.size) {
            for (d in 0 until this.omosim.grid.size) {
                relevantODs.add(Pair(o, d))
            }
        }
        return relevantODs
    }

    // TODO dedup
    private fun runBatchAgents(sharePop: Double) : List<MobiAgent> {
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

    fun evaluate() {
        val agents = runBatchAgents(0.1)
        val tripLengths = mutableListOf<Double>()

        for (agent in agents) {
            for (trip in agent.mobilityDemand.first().trips) {
                tripLengths.add(trip.distance!!)
            }
        }
        println("Mean distance is ${tripLengths.sum() / tripLengths.size}")
    }
}