package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
import de.uniwuerzburg.omosim.cli.CalibrationStep
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.*
import de.uniwuerzburg.omosim.io.json.writeJson
import de.uniwuerzburg.omosim.routing.routeCarAlternatives
import de.uniwuerzburg.omosim.routing.routeWith
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.geotools.filter.function.StaticGeometry.intersection
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiLineString
import org.locationtech.jts.index.hprtree.HPRtree
import java.io.File
import kotlin.math.pow

/**
 * Entry point for calibration.
 *
 * Context for OMoSim calibration. Stores the simulator to calibrate in addition to the traffic count data to be used
 * as the reference.
 *
 * @param trafficCountDataFile File containing the traffic count data.
 * @param omosim Simulator
 */
class TrafficCountCalibrationContext(
    trafficCountDataFile: File,
    val omosim: Omosim,
    population: Double? = null,
) {
    val sensors: List<TrafficSensor> = TrafficSensor.readSensorData(trafficCountDataFile, omosim.transformer)
    val finder = omosim.destinationFinder as DestinationFinderDefault
    val affectedSensors: Map<Pair<RealLocation, RealLocation>, List<TrafficSensor>>
    var affectedAltSensors: Map<Pair<RealLocation, RealLocation>, List<List<TrafficSensor>>> = mapOf()
    val totalPopulation: Double

    init {
        T = sensors.first().measurements.size // Set number of time slices
        if (!sensors.all { it.measurements.size == T }) {
            throw IllegalArgumentException(
                "Sensor measurement arrays are not uniformly sized!" +
                "Validate the --cal_traffic_count_file file. "
            )
        }
        affectedSensors = affectedSensors()

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

    /**
     * Calibration entry point.
     *
     * @param gravityCalOut File where to store the calibration result for the GRAVITY step
     * @param modeChoiceCalOut  File where to store the calibration result for the MODE_CHOICE step
     * @param routeChoiceCalOut  File where to store the calibration result for the ROUTE_CHOICE step
     * @param steps Calibration steps to be done.
     */
    fun calibrate(
        gravityCalOut: File,
        modeChoiceCalOut: File,
        routeChoiceCalOut: File,
        steps: List<CalibrationStep>
    ) {
        // If alternative routes need to be computed
        if (CalibrationType.ROUTE_CHOICE in steps.map { it.type }) {
            affectedAltSensors = altAffectedSensors()
        }

        // Complete the steps in the given order
        for ((i, step) in steps.withIndex()) {
            when(step.type) {
                CalibrationType.GRAVITY -> {
                    Gravity(this).calibrate(step.alg, step.activities, step.parameters)
                    val finder = omosim.destinationFinder as DestinationFinderDefault
                    GravityCalibrationStore.write(gravityCalOut, omosim.buildings, finder.locChoiceWeightFuns)
                }
                CalibrationType.MODE_CHOICE -> {
                    val mcResult = ModeChoice(this).calibrate(ModeChoiceCalibrationObjective.FitIndividualMeasurements)
                    writeJson(mcResult, modeChoiceCalOut)
                    omosim.tourModeUtilityFn = modeChoiceCalOut
                }
                CalibrationType.ROUTE_CHOICE -> {
                    RouteChoice(this).calibrate()
                    RouteChoiceCalibrationStore(omosim).write(routeChoiceCalOut, omosim.altPercentages)
                }
                CalibrationType.EVALUATE -> {
                    evaluate(0.1)
                }
                null -> { logger.warn("Calibration step $i skipped. Step type is null.") }
            }
        }
    }

    /**
     * Evaluate the current calibration with simulation runs and print the result to the standard output.
     *
     * @param sharePop Share of population to use.
     */
    fun evaluate(sharePop: Double) {
        logger.info("Evaluating calibration...")

        // Get calibrated run
        val finder = omosim.destinationFinder as DestinationFinderDefault
        val simCal = runBatch(sharePop)

        // Clear calibration
        for (activity in ActivityType.entries) {
            val dcFunction = finder.locChoiceWeightFuns[activity]!!
            for (cell in omosim.grid) {
                cell.updateAttractionScaler(dcFunction, 1.0) // Gravity: attraction values
            }
        }
        finder.forcedTransitionMatrix.clear() // Gravity: transition matrix
        omosim.tourModeUtilityFn = null // Mode choice
        omosim.altPercentages = mapOf() // Route choice

        // Run uncalibrated
        val simBase = runBatch(sharePop)

        printTable(simBase, simCal)
    }

    /**
     * Print evaluation result
     * 
     * @param simBase Simulation result at each traffic sensor at each time step without calibration
     * @param simCal Simulation result at each traffic sensor at each time step with calibration
     * @param cellWidth Size of each table cell (number of characters)
     */
    private fun printTable(
        simBase: Map<TrafficSensor, DoubleArray>,
        simCal: Map<TrafficSensor, DoubleArray>,
        cellWidth: Int = 15
    ) {
        // Calculate MSE
        val mseCal  = mse(simCal)
        val mseBase = mse(simBase)

        // Print table
        println("EVALUATION RESULT:")

        // Header
        println("_".repeat(cellWidth*5 + 4*3))
        println("${"Sensor".padEnd(cellWidth)} | " +
                "${"T".padEnd(cellWidth)} | " +
                "${"Sim. Calibrated".padEnd(cellWidth)} | " +
                "${"Sim. Base".padEnd(cellWidth)} | " +
                "Measured".padEnd(cellWidth)
        )
        printTabHLine(cellWidth)

        // MSE Result
        println(" ".repeat(cellWidth) +
                " | " + " ".repeat(cellWidth) +
                " | " + "%.4g".format(mseCal).padStart(cellWidth)  +
                " | " + "%.4g".format(mseBase).padStart(cellWidth)  +
                " | " + " ".repeat(cellWidth)
        )
        printTabHLine(cellWidth)

        // Measurement vs Simulated
        for (sensor in sensors) {
            // Print results for aggregated time windows
            for (seg in listOf(Pair(0, T))) {
                // Sum over time window
                var cal = 0.0
                var base = 0.0
                var measurement = 0.0
                for (t in seg.first until seg.second) {
                    cal += simCal[sensor]!![t]
                    base += simBase[sensor]!![t]
                    measurement += sensor.measurements[t]
                }

                println(
                    "${sensor.name.padEnd(cellWidth)} | " +
                            "${(seg.first.toString() + "-" + seg.second).padEnd(cellWidth)} | " +
                            "%.2f".format(cal).padStart(cellWidth) + " | " +
                            "%.2f".format(base).padStart(cellWidth) + " | " +
                            "%.2f".format(measurement).padStart(cellWidth)
                )
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun printTabHLine(wCell: Int) {
        println(
            "_".repeat(wCell) +
            " | " + "_".repeat(wCell)  +
            " | " + "_".repeat(wCell)  +
            " | " + "_".repeat(wCell)  +
            " | " + "_".repeat(wCell)
        )
    }

    /**
     * Simulate a sample of the population and return the simulated traffic counts.
     * The result is scaled up to the entire simulation.
     *
     * @param sharePop Share of population to use.
     * @return Simulated traffic counts at each traffic sensor.
     */
    @Suppress("SameParameterValue")
    fun runBatch(sharePop: Double) : Map<TrafficSensor, DoubleArray> {
       // Ensure results are deterministic
       omosim.mainRng.setSeed(0)

       // Run Simulation
       val agents = if (omosim.censusAvailable) {
           omosim.run(sharePop, verbose = false)
       } else {
           omosim.run((sharePop * totalPopulation).toInt(), verbose = false)
       }
       omosim.doModeChoice(agents, ModeChoiceOption.FAST, false, verbose = false)

       // Determine counts at sensors
       val simCount = sensors.associateWith { Array(T) {0.0} }.toMutableMap()
       val visitor: TripVisitor = { trip, originActivity, destinationActivity, departureTime, _, _ ->
           val t = departureTime.determineTimeSlice()

           if (trip.mode == Mode.CAR_DRIVER) {
               val origin = originActivity.location.getAggLoc()
               val destination = destinationActivity.location.getAggLoc()

               // Check if trip is from a real location to a real location.
               // Always true if no legacy calibration was applied.
               if ((origin is Cell) and (destination is Cell)) {
                   val od = Pair(origin as Cell, destination as Cell)
                   val odt = ODTTriple(od.first, od.second, t)

                   if (odt in omosim.altPercentages) {
                       // With route choice calibration
                       if (od in affectedAltSensors) {
                           val p = omosim.altPercentages[odt]!!
                           for ((i, alternative) in affectedAltSensors[od]!!.withIndex()) {
                               for (sensor in alternative) {
                                 simCount[sensor]!![t] = simCount[sensor]!![t] + p[i] // Add traffic
                               }
                           }
                       }
                   } else {
                       // Case without route choice calibration
                       if (od in affectedSensors) {
                           val sensors = affectedSensors[od]!!
                           for (sensor in sensors) {
                               simCount[sensor]!![t] = simCount[sensor]!![t] + 1 // Add traffic
                           }
                       }
                   }
               }
           }
       }
       for (agent in agents) {
           agent.mobilityDemand.first().visitTrips(visitor)
       }

       // Scale traffic to total population
       val scaledSimCount = sensors.associateWith { DoubleArray(T) {0.0} }.toMutableMap()
       for (sensor in sensors) {
           for (t in 0 until T) {
               scaledSimCount[sensor]!![t] = simCount[sensor]!![t] * totalPopulation / agents.size
           }
       }
       return scaledSimCount
    }

    /**
     * Determine all sensors that count a given origin-destination trip.
     *
     * @return Key: origin-destination pair. Value: List of alternatives that contain lists of all sensors affected by
     * the alternative.
     */
    private fun altAffectedSensors() : Map<Pair<RealLocation, RealLocation>, List<List<TrafficSensor>>> {
        return affectedSensors(true)
    }

    /**
     * Determine all sensors that count a given origin-destination trip.
     *
     * @return Key: origin-destination pair. Value: List of all sensors affected by the pair.
     */
    private fun affectedSensors() : Map<Pair<RealLocation, RealLocation>, List<TrafficSensor>> {
        val affectedSensors = affectedSensors(false)
            .mapValues { (_, v) -> v.first() }
            .filter{ (_, v) -> v.isNotEmpty()}
        return affectedSensors
    }

    /**
     * Determine all sensors that count a given origin-destination trip.
     *
     * @param checkAlternatives If true also check alternative routes between origin and destination. If false only
     * check the 'best' route according to GraphHopper
     * @param geometryFactory GeometryFactory to use for creating LineStrings from GraphHopper responses
     * @param leeway Maximum angle between a measurement direction and a route that still counts as 'same direction'.
     * In degrees.
     * @return Key: origin-destination pair. Value: List of alternatives that contain lists of all sensors affected by
     * the alternative.
     */
    private fun affectedSensors(
        checkAlternatives: Boolean,
        geometryFactory: GeometryFactory = GeometryFactory(),
        leeway: Double = 30.0
    ) : Map<Pair<RealLocation, RealLocation>, List<List<TrafficSensor>>> {
        // Create spatial index of sensor FOVs
        val sensorTree = HPRtree()
        for (sensor in sensors) {
            sensorTree.insert(sensor.fov.envelopeInternal, sensor)
        }

        val affectedSensors: Map<Pair<RealLocation, RealLocation>, List<List<TrafficSensor>>> = runBlocking(omosim.dispatcher) {
            channelFlow {
                for (origin in omosim.grid) {
                    launch {
                        for (destination in omosim.grid) {
                            val odAffects = mutableListOf<List<TrafficSensor>>()

                            // Route the origin destination pair
                            val paths = if (checkAlternatives) {
                                routeCarAlternatives(origin, destination, omosim.hopper!!).all
                            } else {
                                listOf( routeWith("car", origin, destination, omosim.hopper!!).best )
                            }

                            // Check which paths intersect with which sensors
                            for (path in paths) {
                                val coords = path.points.map { Coordinate(it.lat, it.lon) }.toTypedArray()

                                if (coords.size >= 2) {
                                    val route = omosim.transformer.toModelCRS(geometryFactory.createLineString(coords))

                                    // Alternative affects these sensor counts:
                                    val altAffects = sensorTree
                                        // Sensors that intersect the route envelope
                                        .query(route.envelopeInternal).map { it as TrafficSensor }
                                        // Sensors that intersect the route
                                        .filter { sensor ->
                                            sensor.fov.envelope.intersects(route) && sensor.fov.intersects(route)
                                        }
                                        // Sensors that intersect the route and face the right direction
                                        .filter { sensor ->
                                            val inter = intersection(sensor.fov, route)
                                            if (inter is LineString) {
                                                sensor.isInMeasurementDirection(inter, leeway)
                                            } else if (inter is MultiLineString) {
                                                // If route crosses the sensor fov more than once check if any crossing
                                                // is in the right direction
                                                var sameDir = false
                                                for (n in 0 until inter.numGeometries) {
                                                    val crossingN = inter.getGeometryN(n) as LineString
                                                    if (sensor.isInMeasurementDirection(crossingN, leeway)) {
                                                        sameDir = true
                                                        break
                                                    }
                                                }
                                                sameDir
                                            } else {
                                                false
                                            }
                                        }

                                    // Add affected sensors. Might be an empty list.
                                    odAffects.add(altAffects)
                                }
                            }

                            if(odAffects.isNotEmpty()) {
                                send(Pair(Pair(origin, destination), odAffects))
                            }
                        }
                    }
                }
            }.toList()
        }.toMap()

        return affectedSensors
    }

    /**
     * Compute the mean square error between simulation and measurements across all sensors and time steps.
     *
     * @param simCount Simulated traffic at sensor and time step
     * @return Mean squared error
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun mse(simCount: Map<TrafficSensor, DoubleArray>) : Double {
        return sse(simCount) / (sensors.size * T)
    }

    /**
     * Compute the sum of squares error between simulation and measurements across all sensors and time steps.
     *
     * @param simCount Simulated traffic at sensor and time step
     * @return Sum of squares error
     */
    fun sse(simCount: Map<TrafficSensor, DoubleArray>) : Double {
        var sse = 0.0
        for (sensor in sensors) {
            for (t in 0 until T) {
                sse += (simCount[sensor]!![t] - sensor.measurements[t]).pow(2)
            }
        }
        return sse
    }
}




