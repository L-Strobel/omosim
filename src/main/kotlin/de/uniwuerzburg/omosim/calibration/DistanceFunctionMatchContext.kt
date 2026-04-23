package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.calibration.algorithms.GradientDescent
import de.uniwuerzburg.omosim.calibration.objective.DFMatchSSETensorFlow
import de.uniwuerzburg.omosim.calibration.objective.DFMatchSSE
import de.uniwuerzburg.omosim.calibration.surrogate.DistanceFunctionVMatrixBuilderTF
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
    fun calibrate(moments: List<Double>, activity: ActivityType) {
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[activity]!!

        // Calibrate
        /*val objective = DFMatchSSE(activity, mean, m2, this)
        val model = SGGravity(
            this, null
        ).buildNative(activity, objective, DistanceFunctionVMatrixBuilder(this))
        val base  = dcFunction.getDistanceParameters()*/

        // Calibrate
        val objective = DFMatchSSETensorFlow(activity, moments, this)
        val model = SGGravity(this, null).buildTF(activity, objective, DistanceFunctionVMatrixBuilderTF(this))
        val base  = dcFunction.getDistanceParameters()
        println( model.evaluate(base) )

        val calibrated = GradientDescent.run(
            model,
            base,
            mapOf(
                "iterations" to "1000",
                "lr0" to "0.001",
                "ub" to "10.0",
                "lb" to "-100.0",
                "lTol" to "0.00001",
                "backTracking" to "true"
            )
        )

        evaluate(base, calibrated, moments, activity)
        dcFunction.setDistanceParameters(calibrated)
        model.close()
    }

    fun evaluate(base: DoubleArray, calibrated: DoubleArray, moments: List<Double>, activity: ActivityType) {
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[activity]!!

        dcFunction.setDistanceParameters(calibrated)
        val (momentsCal, cntCal) = getMomentsDistance(activity, moments.size)

        dcFunction.setDistanceParameters(base)
        val (momentsBase, cntBase) = getMomentsDistance(activity, moments.size)

        println("Evaluate Distance Function Match (${activity}):")
        println("Count Below 500    Base: $cntBase")
        println("                   Calibrated: $cntCal")
        for (i in moments.indices) {
            println("M${i+1} trip distance Goal: %.3f km".format(moments[i]))
            println("                   Base: %.3f km".format(momentsBase[i]))
            println("                   Calibrated: %.3f km".format(momentsCal[i]))
        }
    }

    private fun getMomentsDistance(activity: ActivityType, nMoments: Int) : Pair<List<Double>, Int> {
        val agents = runBatchAgents(0.1)

        // Trip Distances
        var countBelow = 0
        val tripLengths = mutableListOf<Double>()
        for (agent in agents) {
            val activities = agent.mobilityDemand.first().activities
            val trips = agent.mobilityDemand.first().trips
            for (i in trips.indices) {
                val trip = trips[i]
                val nextActivity = activities[i+1]

                if (nextActivity.type == activity) {
                    tripLengths.add(trip.distance!!)

                    if (trip.distance!! <= 0.5) {
                        countBelow += 10
                    }
                }
            }
        }

        // Calculate Moments
        val moments = mutableListOf<Double>()
        for (i in 1 .. nMoments) {
            moments.add(
                tripLengths.sumOf { it.pow(i) } / tripLengths.size
            )
        }
        return Pair(moments, countBelow)
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