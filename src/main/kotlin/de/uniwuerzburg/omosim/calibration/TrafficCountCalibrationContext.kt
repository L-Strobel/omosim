package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
import de.uniwuerzburg.omosim.calibration.objective.ModeChoiceCalibrationObjective
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
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.pow

/**
 * Entry point for traffic count calibration.
 *
 * Context for OMoSim calibration. Stores the simulator to calibrate in addition to the traffic count data to be used
 * as the reference.
 *
 * @param trafficCountDataFile File containing the traffic count data.
 * @param omosim Simulator
 */
class TrafficCountCalibrationContext(
    trafficCountDataFile: File,
    override val omosim: Omosim,
    override val weekday: Weekday,
    population: Double? = null,
    private val calibrationOutputFolder: Path
) : CalibrationContext {
    val sensors: List<TrafficSensor> = TrafficSensor.readSensorData(trafficCountDataFile, omosim.transformer)
    val finder = omosim.destinationFinder as DestinationFinderDefault
    val affectedSensors: Map<Pair<RealLocation, RealLocation>, List<TrafficSensor>>
    var affectedAltSensors: Map<Pair<RealLocation, RealLocation>, List<List<TrafficSensor>>> = mapOf()
    override val totalPopulation: Double = population ?: initTotalPopulation()
    private val gravityOut: File = Paths.get(calibrationOutputFolder.toString(), "gravity.json").toFile()
    private val modeChoiceOut: File = Paths.get(calibrationOutputFolder.toString(), "mode_choice.json").toFile()
    private val routeChoiceOut: File = Paths.get(calibrationOutputFolder.toString(), "route_choice").toFile()

    init {
        T = sensors.first().measurements.size // Set number of time slices
        if (!sensors.all { it.measurements.size == T }) {
            throw IllegalArgumentException(
                "Sensor measurement arrays are not uniformly sized!" +
                "Validate the --cal_traffic_count_file file. "
            )
        }
        affectedSensors = affectedSensors()
    }

    /**
     * Calibration entry point.
     *
     * @param steps Calibration steps to be done.
     */
    fun calibrate(
        steps: List<CalibrationStep>
    ) {
        // If alternative routes need to be computed
        if (
            (CalibrationType.ROUTE_CHOICE in steps.map { it.type }) or
            (omosim.altPercentages.isNotEmpty())
        ) {
            affectedAltSensors = altAffectedSensors()
        }

        // Complete the steps in the given order
        for ((i, step) in steps.withIndex()) {
            when(step.type) {
                CalibrationType.GRAVITY -> {
                    Gravity(this, calibrationOutputFolder)
                        .calibrate(step.alg, step.activities, step.parameters)
                    val finder = omosim.destinationFinder as DestinationFinderDefault
                    GravityCalibrationStore.write(gravityOut, omosim.buildings, finder.locChoiceWeightFuns)
                }
                CalibrationType.MODE_CHOICE -> {
                    val mcResult = ModeChoice(this)
                        .calibrate(
                            ModeChoiceCalibrationObjective.FitIndividualMeasurements,
                            step.alg,
                            step.parameters
                        )
                    writeJson(mcResult, modeChoiceOut)
                    omosim.parameterReader.tourModeUtilityFile = modeChoiceOut
                }
                CalibrationType.ROUTE_CHOICE -> {
                    RouteChoice(this)
                        .calibrate(step.alg, step.parameters)
                    RouteChoiceCalibrationStore(omosim).write(routeChoiceOut, omosim.altPercentages)
                }
                CalibrationType.EVALUATE -> {
                    evaluate(0.1)
                }
                CalibrationType.DEBUG -> {
                    debug(0.1)
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
                cell.resetAttractionScaler(dcFunction) // Gravity: attraction values
            }
        }
        finder.forcedTransitionMatrix.clear() // Gravity: transition matrix
        omosim.parameterReader.tourModeUtilityFile = null // Mode choice
        omosim.altPercentages = mapOf() // Route choice

        // Run uncalibrated
        val simBase = runBatch(sharePop)

        printTable(simBase, simCal)
    }

    /**
     * Test the sensor locations and directions.
     * Will measure if at least irrelevantMeasurement vehicles pass the sensor
     * and if the share of the traffic in the given direction is above unrealisticDirectionShare.
     * Useful sanity check.
     *
     * @param sharePop Share of population to simulate in test.
     * @param irrelevantMeasurement Number of vehicles per day below which the measurement is likely a faulty.
     * @param unrealisticDirectionShare Share of traffic threshold. If less than this portion of the traffic passing
     * through the sensor's fov is being picked up, the direction of the sensor is likely wrong.
     */
    private fun debug(sharePop: Double, irrelevantMeasurement: Int = 10, unrealisticDirectionShare: Double = 0.2) {
        logger.info("Checking sensor locations and directions...")

        // Determine affected sensors when directionality is ignored.
        val affectedSensorsAllDirections = affectedSensors(leeway = 360.0)

        // Run
        val agents = runBatchAgents(sharePop)
        val simCountDirectional = determineSimCounts(agents, affectedSensors, mapOf(), mapOf())
        val simCountAllDirections = determineSimCounts(agents, affectedSensorsAllDirections, mapOf(), mapOf())

        var nIssues = 0
        for (sensor in sensors) {
            val countDirectional = simCountDirectional[sensor]!!.sum()
            val countAllDirections = simCountAllDirections[sensor]!!.sum()

            if (sensor.measurements.sum() >= irrelevantMeasurement) {
                // Traffic share check
                if (
                    (countAllDirections > irrelevantMeasurement) and
                    (countDirectional < countAllDirections * unrealisticDirectionShare)
                ) {
                    val shareStr = "%.2f".format(countDirectional/countAllDirections * 100.0)
                    logger.warn(
                        "Sensor ${sensor.name} measures only $shareStr % of traffic passing through its field-of-vision. " +
                        "The sensor is likely facing the wrong direction."
                    )
                    nIssues += 1
                }
                // Traffic amount check
                else if (countDirectional < irrelevantMeasurement) {
                    logger.warn(
                        "Sensor ${sensor.name} measures only $irrelevantMeasurement veh/day. " +
                        "The sensor is likely unreachable, facing the wrong direction, placed at the wrong location."
                    )
                    nIssues += 1
                }
            }
        }
        if (nIssues == 0) {
            logger.info("Sensor check complete! No issues found with traffic sensor data.")
        } else {
            logger.info("Sensor check complete! Found issues with $nIssues sensors.")
        }
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
        println("Evaluate Traffic Counts:")

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
       val agents = runBatchAgents(sharePop)
       val scaledSimCount = determineSimCounts(agents, affectedSensors, omosim.altPercentages, affectedAltSensors)
       return scaledSimCount
    }

    /**
     * Determine the simulated traffic counts.
     * The result is scaled up to the entire simulation.
     *
     * altPercentages and affectedAltSensors should either be both empty or both filled.
     *
     * @param agents Simulated agents that have undergone mode choice.
     * @param affectedSensors key: Origin-Destination pair, value: Traffic sensors that measure a car trip for that pair.
     * @param altPercentages key: Origin-Destination-Time triple,
     * value: Probability distribution for each possible route alternative for the triple.
     * @param affectedAltSensors key: Origin-Destination pair
     * value: List that contains the sensors affected by each alternative route for the od pair.
     * @return Agents
     */
    private fun determineSimCounts(
        agents: List<MobiAgent>,
        affectedSensors: Map<Pair<RealLocation, RealLocation>, List<TrafficSensor>>,
        altPercentages: Map<ODTTriple, List<Double>>,
        affectedAltSensors: Map<Pair<RealLocation, RealLocation>, List<List<TrafficSensor>>>,
    ) : Map<TrafficSensor, DoubleArray> {
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

                    if (odt in altPercentages) {
                        // With route choice calibration
                        if (od in affectedAltSensors) {
                            val p = altPercentages[odt]!!
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
    private fun altAffectedSensors(leeway: Double = 30.0) : Map<Pair<RealLocation, RealLocation>, List<List<TrafficSensor>>> {
        return affectedSensors(true, leeway = leeway)
    }

    /**
     * Determine all sensors that count a given origin-destination trip.
     *
     * @return Key: origin-destination pair. Value: List of all sensors affected by the pair.
     */
    private fun affectedSensors(leeway: Double = 30.0) : Map<Pair<RealLocation, RealLocation>, List<TrafficSensor>> {
        val affectedSensors = affectedSensors(false, leeway = leeway)
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

    /**
     * Determine od-Pairs that contribute to sensor measurements.
     * @return Set of relevant od-Pairs
     */
    override fun getRelevantODs(): Set<Pair<Int, Int>> {
        val relevantODs = mutableSetOf<Pair<Int, Int>>()
        for ((o, origin) in omosim.grid.withIndex()) {
            for ((d, destination) in omosim.grid.withIndex()) {
                val od = Pair(origin, destination)
                if (od in affectedSensors) {
                    if (affectedSensors[od]!!.isNotEmpty()) {
                        relevantODs.add(Pair(o, d))
                    }
                }
            }
        }
        return relevantODs
    }
}




