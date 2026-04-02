package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.calibration.algorithms.GradientDescent
import de.uniwuerzburg.omosim.calibration.surrogate.DistanceFunctionVMatrixBuilder
import de.uniwuerzburg.omosim.calibration.surrogate.SGGravity
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.ActivityType
import kotlin.math.pow

class DistanceFunctionMatchContext(
    override val omosim: Omosim,
    population: Double? = null
) : CalibrationContext {
    override val totalPopulation: Double = population ?: initTotalPopulation()

    // TODO Store Results and Change to interface more similar to TrafficCountCal
    fun calibrate(mean: Double, m2: Double, activity: ActivityType) {
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[activity]!!

        // Calibrate
        val objective = DFMatchSSE(activity, mean, m2)
        val model = SGGravity(this, objective, DistanceFunctionVMatrixBuilder, null).build(activity)
        val base  = dcFunction.getDistanceParameters()
        val calibrated = GradientDescent.run(
            model,
            base,
            mapOf("iterations" to "1000", "lr0" to "0.0000001", "ub" to "0.0", "lb" to "-100.0", "lTol" to "0.00001")
        )

        evaluate(base, calibrated, objective)
        dcFunction.setDistanceParameters(calibrated)
    }

    fun evaluate(base: DoubleArray, calibrated: DoubleArray, objective: DFMatchSSE) {
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[objective.activity]!!

        dcFunction.setDistanceParameters(calibrated)
        val (meanDistanceCal, m2DistanceCal) = getMomentsDistance(objective.activity)

        dcFunction.setDistanceParameters(base)
        val (meanDistanceBase, m2DistanceBase) = getMomentsDistance(objective.activity)

        println("Evaluate Distance Function Match (${objective.activity}):")
        println("Mean trip distance Goal: %.3f km".format(objective.mean))
        println("                   Base: %.3f km".format(meanDistanceBase))
        println("                   Calibrated: %.3f km".format(meanDistanceCal))
        println("M2 trip distance   Goal: %.3f km".format(objective.m2))
        println("                   Base: %.3f km".format(m2DistanceBase))
        println("                   Calibrated: %.3f km".format(m2DistanceCal))
    }

    private fun getMomentsDistance(activity: ActivityType) : Pair<Double, Double> {
        val agents = runBatchAgents(0.1)

        // Determine mean
        val tripLengths = mutableListOf<Double>()
        for (agent in agents) {
            val activities = agent.mobilityDemand.first().activities
            val trips = agent.mobilityDemand.first().trips
            for (i in trips.indices) {
                val trip = trips[i]
                val nextActivity = activities[i+1]

                if (nextActivity.type == activity) {
                    tripLengths.add(trip.distance!!)
                }
            }
        }
        val mean = tripLengths.sum() / tripLengths.size
        val moment2 = tripLengths.sumOf { it.pow(2.0) } / tripLengths.size
        return Pair(mean, moment2)
    }

    /**
     * All origin-destination pairs are relevant here.
     */
    override fun getRelevantODs(): Set<Pair<Int, Int>> {
        val relevantODs = mutableSetOf<Pair<Int, Int>>()
        for (o in 0 until this.omosim.grid.size) {
            for (d in 0 until this.omosim.grid.size) {
                relevantODs.add(Pair(o, d))
            }
        }
        return relevantODs
    }
}