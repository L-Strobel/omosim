package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.Weekday
import kotlin.math.pow

/**
 * Dormant. Calibrate the trip distance distribution.
 */
class DistanceFunctionMatchContext(
    override val omosim: Omosim,
    override val weekday: Weekday,
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
        /*val objective = DFMatchSSETensorFlow(activity, moments, this)
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
        println(calibrated.toList())*/
        val base  = dcFunction.getDistanceParameters()
        val calibrated  = dcFunction.getDistanceParameters()
        evaluate(base, calibrated, moments, activity)
        dcFunction.setDistanceParameters(calibrated)
        //model.close()
    }

    fun evaluate(base: DoubleArray, calibrated: DoubleArray, moments: List<Double>, activity: ActivityType) {
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[activity]!!

        dcFunction.setDistanceParameters(calibrated)
        val (momentsCal, pCal) = getMomentsDistance(activity, moments.size)

        dcFunction.setDistanceParameters(base)
        val (momentsBase, pBase) = getMomentsDistance(activity, moments.size)

        println("Evaluate Distance Function Match (${activity}):")
        println("Count Below 0.5    Base: %.5f".format(pBase[0]))
        println("                   Calibrated: %.5f".format(pCal[0]))
        println("Count Below 0.5-2  Base: %.5f".format(pBase[1]))
        println("                   Calibrated: %.5f".format(pCal[1]))
        println("Count Below 2-5    Base: %.5f".format(pBase[2]))
        println("                   Calibrated: %.5f".format(pCal[2]))
        println("Count Below 5-10   Base: %.5f".format(pBase[3]))
        println("                   Calibrated: %.5f".format(pCal[3]))
        println("Count Below 10-50  Base: %.5f".format(pBase[4]))
        println("                   Calibrated: %.5f".format(pCal[4]))
        println("Count Below 50-100 Base: %.5f".format(pBase[5]))
        println("                   Calibrated: %.5f".format(pCal[5]))
        for (i in moments.indices) {
            println("M${i+1} trip distance Goal: %.3f km".format(moments[i]))
            println("                   Base: %.3f km".format(momentsBase[i]))
            println("                   Calibrated: %.3f km".format(momentsCal[i]))
        }
    }

    private fun getMomentsDistance(activity: ActivityType, nMoments: Int) : Pair<List<Double>, List<Double>> {
        val agents = runBatchAgents(0.03)

        // Trip Distances
        val counts = DoubleArray(6) { 0.0 }
        val tripLengths = mutableListOf<Double>()
        for (agent in agents) {
            val activities = agent.mobilityDemand.first().activities
            val trips = agent.mobilityDemand.first().trips
            for (i in trips.indices) {
                val trip = trips[i]
                val nextActivity = activities[i + 1]

                if (nextActivity.type == activity) {
                    tripLengths.add(trip.distance!!)

                    if (trip.distance!! <= 0.5) {
                        counts[0] += 1.0
                    } else if (trip.distance!! <= 2.0) {
                        counts[1] += 1.0
                    } else if (trip.distance!! <= 5.0) {
                        counts[2] += 1.0
                    } else if (trip.distance!! <= 10.0) {
                        counts[3] += 1.0
                    } else if (trip.distance!! <= 50.0) {
                        counts[4] += 1.0
                    } else if (trip.distance!! <= 100.0) {
                        counts[5] += 1.0
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
        return Pair(moments, counts.map { it / tripLengths.size.toDouble() })
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