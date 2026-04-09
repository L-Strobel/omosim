package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
import de.uniwuerzburg.omosim.calibration.algorithms.BFGS
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.*
import de.uniwuerzburg.omosim.core.ModeChoiceFast
import de.uniwuerzburg.omosim.core.ModeUtility
import de.uniwuerzburg.omosim.core.models.*
import java.util.*
import kotlin.math.exp
import kotlin.math.ln

/**
 * Objectives for mode choice calibration
 *
 * FitTotalCarTrips: Calibrate the total number of car trips across all measurements: minimize (sum(M) - sum(S))^2
 * FitIndividualMeasurements: Calibrate each measurement individually (normal case): minimize (sum(m - s))^2
 */
enum class ModeChoiceCalibrationObjective {
    FitTotalCarTrips, FitIndividualMeasurements
}

/**
 * Calibrate OMoSim output by adjusting mode choice.
 *
 * Optimizes the intercept of the car mode utility such that the objective is minimized.
 *
 * @param context Calibration context to use. Includes a Simulator (OMoSim) and the traffic count data.
 */
class ModeChoice(
    private val context: TrafficCountCalibrationContext
) {
    /**
     * Calibrate mode choice.
     */
    fun calibrate(objective: ModeChoiceCalibrationObjective) : Array<ModeUtility> {
        context.omosim.mainRng.setSeed(0) // Seed impact low with 100% of agents

        // Run Simulation
        val agents = context.omosim.run(0.1, verbose = false)
        context.omosim.doModeChoice(agents, ModeChoiceOption.FAST, withPath = false, verbose = false)

        // Create mode choice surrogate model
        val mc = ModeChoiceFast(context.omosim.routingCache)
        val model = buildModel(agents, mc, context.omosim.mainRng, objective)

        // Get X0
        val carUtil = mc.tourModeOptions.find { it.mode == Mode.CAR_DRIVER }
        val x0 = doubleArrayOf(carUtil!!.intercept)

        // Optimize
        val x = BFGS.run(model, x0, lb=-50.0, ub=50.0)

        // Store calibration
        carUtil.intercept = x[0]
        return mc.tourModeOptions
    }

    /**
     * Build a differentiable model that computes the objective loss for a given intercept.
     *
     * @param agents OMoSim run result
     * @param mc Mode choice model to calibrate
     * @param rng Random number generator
     * @param objective Type of objective to use @See de.uniwuerzburg.omod.calibration.ModeChoiceCalibrationObjective
     * @return Differentiable Model
     */
    private fun buildModel(
        agents: List<MobiAgent>,
        mc: ModeChoiceFast,
        rng: Random,
        objective: ModeChoiceCalibrationObjective
    ) : DifferentiableModelUV {
        val model = DifferentiableModelUVBase(1) // Only variable: Intercept of car mode

        // Initialize simulated traffic counts
        val simCount = mutableMapOf<TrafficSensor, List<LinearTerm>>()
        for (sensor in context.sensors) {
            simCount[sensor] = List(T) { LinearTerm(model.nVars) }
        }

        // Utility model for the car driver mode
        val driverUtilityModel = mc.tourModeOptions.first { it.mode == Mode.CAR_DRIVER }

        // Add contribution of each agent and each tour to simulated counts
        for (agent in agents) {
            val aTours = mc.getTours(agent.mobilityDemand.first(), rng)

            for (tour in aTours) {
                if (!tour.isHomeHome()) { continue } // Only do HOME-HOME tours

                val carDistance = tour.totalCarDistance()
                val mainPurpose = tour.mainPurpose()
                val weekday = tour.weekday()

                // Car mode utility
                val utilityTerm = LinearTerm(model.nVars)
                val utility = driverUtilityModel.calc(null, carDistance, mainPurpose, agent.carAccess, weekday, agent)

                // Create intercept variable
                val driverUtilityVar = LinearBaseTerm(1)
                driverUtilityVar.addConstant(utility - driverUtilityModel.intercept)
                driverUtilityVar.addTerm(0, 1.0)
                utilityTerm.addTerm(driverUtilityVar, -1.0)

                // Other mode weights
                val otherWeight = ln(mc.tourModeOptions
                    .filter { it.mode != Mode.CAR_DRIVER }
                    .sumOf { util -> exp(util.calc(null, carDistance, mainPurpose, agent.carAccess, weekday, agent)) })
                utilityTerm.addConstant(otherWeight)

                // Car mode probability
                val eTerm = ExponentialTerm(model.nVars, utilityTerm)
                val normTerm = LinearTerm(model.nVars)
                normTerm.addConstant(1.0)
                normTerm.addTerm(eTerm, 1.0)
                val pTerm = PowerTerm(model.nVars, normTerm, -1.0)

                // Add expected origin destination trips to sim counts
                var o = tour.trips.first().fromActivity.location.getAggLoc()!! as Cell
                for (trip in tour.trips) {
                    val d = trip.toActivity.location.getAggLoc()!! as Cell
                    val t = trip.departureTime.determineTimeSlice()
                    val tripOD = Pair(o, d)
                    if (tripOD in context.affectedSensors) {
                        for (sensor in context.affectedSensors[tripOD]!!) {
                            simCount[sensor]!![t].addTerm(pTerm, context.totalPopulation / agents.size)
                        }
                    }
                    o = d
                }
            }
        }

        // Objective
        val objTerm = when(objective) {
            ModeChoiceCalibrationObjective.FitIndividualMeasurements -> {
                sseObjective(model.nVars, context.sensors, simCount) // Normal sum of squares objective
            }
            ModeChoiceCalibrationObjective.FitTotalCarTrips -> {
                // Objective that minimizes the differences between the sum of simulated counts across all
                // traffic counting stations and the sum of measurements.
                // Purpose: Calibrate the total car demand in the area
                var totalMeasured = 0.0
                val totalSim = LinearTerm(model.nVars)
                for (sensor in context.sensors) {
                    for (t in 0 until T) {
                        totalSim.addTerm(simCount[sensor]!![t], 1.0)
                        totalMeasured += sensor.measurements[t]
                    }
                }
                val obj = LinearTerm(model.nVars)
                obj.addConstant(totalMeasured * totalMeasured)
                obj.addTerm(totalSim, -2 * totalMeasured)
                val qTerm = QuadraticTerm(
                    model.nVars,
                    totalSim,
                    totalSim,
                    1.0
                )
                obj.addTerm(qTerm, 1.0)
                obj
            }
        }
        model.setRootTerm(objTerm)
        return model
    }
}
