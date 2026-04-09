package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.calibration.surrogate.DistanceFunctionVMatrixBuilder
import de.uniwuerzburg.omosim.calibration.surrogate.DistanceFunctionVMatrixBuilderTFTensor
import de.uniwuerzburg.omosim.calibration.surrogate.SGGravityNative
import de.uniwuerzburg.omosim.calibration.surrogate.SGGravityTF
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
        /*
        val objective = DFMatchSSE(activity, mean, m2, this)
        val model = SGGravityNative(
            this, objective, DistanceFunctionVMatrixBuilder(this), null
        ).build(activity, iThresh = 0.0)
        val base  = dcFunction.getDistanceParameters()*/

        /*
        // Test
        println("Test Start")
        DistanceFunctionVMatrixBuilder.TFTestBuilder(
            TfTermBuilder(tfModel),
            this,
            ActivityType.OTHER
        )
        println("Test Stop")
        throw AssertionError("Done")
        //
        */

        // Calibrate
        val (_, nVars) = dcFunction.deterrenceFunctionAsTerm(1.0)
        val tfModel = TfModel(nVars)
        val objective = DFMatchSSETFTF(activity, mean, m2, tfModel, this)
        val model = SGGravityTF(
            this,
            objective,
            DistanceFunctionVMatrixBuilderTFTensor(this),
            null
        ).build(tfModel, activity)
        val base  = dcFunction.getDistanceParameters()

        println( model.evaluate(base) )
        tfModel.close()

        /*val calibrated = GradientDescent.run(
            model,
            base,
            mapOf(
                "iterations" to "1",
                "lr0" to "0.001",
                "ub" to "10.0",
                "lb" to "-100.0",
                "lTol" to "0.00001",
                "backTracking" to "true"
            )
        )

        evaluate(base, calibrated, objective.mean, objective.m2, objective.activity)
        dcFunction.setDistanceParameters(calibrated)*/
    }

    fun evaluate(base: DoubleArray, calibrated: DoubleArray, mean: Double, m2: Double, activity: ActivityType) {
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val dcFunction = finder.locChoiceWeightFuns[activity]!!

        dcFunction.setDistanceParameters(calibrated)
        val (meanDistanceCal, m2DistanceCal) = getMomentsDistance(activity)

        dcFunction.setDistanceParameters(base)
        val (meanDistanceBase, m2DistanceBase) = getMomentsDistance(activity)

        println("Evaluate Distance Function Match (${activity}):")
        println("Mean trip distance Goal: %.3f km".format(mean))
        println("                   Base: %.3f km".format(meanDistanceBase))
        println("                   Calibrated: %.3f km".format(meanDistanceCal))
        println("M2 trip distance   Goal: %.3f km".format(m2))
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