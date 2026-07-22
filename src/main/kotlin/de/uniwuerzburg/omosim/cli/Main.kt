package de.uniwuerzburg.omosim.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*
import de.uniwuerzburg.omosim.calibration.*
import de.uniwuerzburg.omosim.core.DestinationFinderDefault
import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.logger
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.Mode
import de.uniwuerzburg.omosim.core.models.ModeChoiceOption
import de.uniwuerzburg.omosim.core.models.Weekday
import de.uniwuerzburg.omosim.io.formatOutput
import de.uniwuerzburg.omosim.io.json.writeJSONOutput
import de.uniwuerzburg.omosim.io.matsim.writeMatSim
import de.uniwuerzburg.omosim.io.sqlite.RouteGeomTypes
import de.uniwuerzburg.omosim.io.sqlite.writeSQLite
import de.uniwuerzburg.omosim.routing.RoutingMode
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import de.uniwuerzburg.omosim.calibration.logger as calLogger

sealed interface AgentNumberDefinition

class FixedAgentNumber(
    val value: Int
) : AgentNumberDefinition {
    override fun toString(): String {
        return this.value.toString()
    }
}
class ShareOfPop (
    val value: Double
) : AgentNumberDefinition {
    override fun toString(): String {
        return this.value.toString()
    }
}

class CalibrationOptions : OptionGroup (
    help = "Calibration parameters."
) {
    val calibration_traffic_count_file by option(
        help = "[EXPERIMENTAL] Traffic count data that serves as ground truth."
    ).file(mustExist = true, mustBeReadable = true).required()
    val calibration_steps by option(
        help = "[EXPERIMENTAL] Defines one calibration step to undertake.\n" +
               "Format: TYPE:ALG:ACTIVITY,..:PARAMS       \n" +
               "Example: GRAVITY:SM_PSO:OTHER,WORK:iterations=1000:lb=0.2"
    ).convert{ CalibrationStep.fromCLIString(it) }.multiple(
        default = listOf(
            CalibrationStep(CalibrationType.DEBUG, null, listOf(),  mapOf()),
            CalibrationStep(CalibrationType.GRAVITY, CalibrationAlgorithm.SM_GD, listOf(ActivityType.OTHER), mapOf()),
            CalibrationStep(CalibrationType.EVALUATE, null, listOf(),  mapOf())
        )
    )
    val calibration_out_dir by option(
        help = "[EXPERIMENTAL] Calibration output directory. Stores the result of a calibration run." +
               "Calibration results will be stored in this file with the names gravity, mode choice, etc."
    ).path(canBeDir = true, canBeFile = false).default(Paths.get("omosim_calibration/"))
    val calibration_population by option(
        help = "[EXPERIMENTAL] Population of the area (focus + buffer). Necessary input when no census data is supplied. " +
               "Used to scale the estimate traffic counts."
    ).double()
}

/**
 * CLI interface
 */
@Suppress("PrivatePropertyName")
class Run : CliktCommand() {
    // Arguments
    private val area_geojson by argument(
        help = "Path to the GeoJSON file that defines the area for which you want to generate mobility demand. " +
                "Helpful websites for GeoJSON generation: https://geojson.io, https://polygons.openstreetmap.fr"
    ).file(mustExist = true, mustBeReadable = true)
    private val osm_file by argument(
        help = "Path to an osm.pbf file that covers the area completely. " +
               "Recommended download platform: https://download.geofabrik.de/"
    ).file(mustExist = true, mustBeReadable = true)
    // Options
    private val agentNumberDefinition by mutuallyExclusiveOptions(
        option(
            "--n_agents",
            help="Number of agents to simulate. " +
                 "If populate_buffer_area = y, additional agents are created to populate the buffer area."
        ).int().convert { FixedAgentNumber(it) },
        option(
            "--share_pop",
            help="Share of the population to simulate. 0.0 = 0%, 1.0 = 100%" +
                 " If populate_buffer_area = y, additional agents are created to populate the buffer area."
        ).double().convert { ShareOfPop(it) }
    ).single().default( FixedAgentNumber(1000) )
    private val n_days by option(
        help="Number of days to simulate"
    ).int().default(1)
    private val start_wd by option(
        help="First weekday to simulate. If the value is set to UNDEFINED, all simulated days will be UNDEFINED."
    ).enum<Weekday>().default(Weekday.UNDEFINED)
    private val out by option (
        help="Output file. The output format is inferred from the ending: '.json' -> Json, '.xml'-> MATSim, '.db'-> SQLite"
    ).file().multiple(default = listOf(File("output.json")))
    private val routing_mode by option(
        help = "Distance calculation method for destination choice." +
               " Either euclidean distance (BEELINE) or routed distance by car (GRAPHHOPPER)"
    ).enum<RoutingMode>().default(RoutingMode.GRAPHHOPPER)
    private val od by option(
        help="[Deprecated] Path to an OD-Matrix in GeoJSON format. " +
             "The matrix is used to further calibrate the model to the area using k-factors."
    ).file(mustExist = true, mustBeReadable = true)
    private val census by option(
        help="Path to population data in GeoJSON format. " +
             "For an example of how to create such a file see python_tools/format_zensus2011.py. " +
             "Should cover the entire area, but can cover more."
    ).file(mustExist = true, mustBeReadable = true)
    private val grid_precision by option(
        help="Allowed average distance between a focus area building and its corresponding TAZ center. " +
             "The default is 150m and suitable in most cases." +
             "In the buffer area the allowed distance increases quadratically with distance. " +
             "Unit: meters"
    ).double().default(150.0)
    private val buffer by option(
        help="Distance by which the focus area (defined by GeoJSON) is buffered in order" +
             " to account for traffic generated by the surrounding. Unit: meters"
    ).double().default(0.0)
    private val seed by option(help = "RNG seed.").long()
    private val cache_dir by option(help = "Cache directory")
        .path(canBeDir = true, canBeFile = false).default(Paths.get("omosim_cache/"))
    private val populate_buffer_area by option(
        help = "Determines if home locations of agents can be in the buffer area (so outside of the focus area). " +
               "If set to 'y' additional agents will be created so that the proportion of agents in and " +
               "outside the focus area is the same as in the census data. " +
               "The focus area will always be populated by n_agents agents."
    ).boolean().default(false)
    private val distance_matrix_cache_size by option(
        help = "Maximum number of entries of the distance matrix to precompute (only if routing_mode is GRAPHHOPPER). " +
               "A high value will lead to high RAM usage and long initialization times " +
               "but overall significant speed gains. The default value will use approximately 8 GB RAM at maximum."
    ).long().default(400e6.toLong())
    private val mode_choice by option(
        help = "Type of mode choice. " +
               "NONE: Returns trips with undefined modes. " +
               "CAR_ONLY: Select the car mode for every trip. " +
               "GTFS: Uses a logit model with public transit as an option. " +
               "FAST: Same as GTFS but without accounting for travel time. Less precise but computes significantly faster."
    ).enum<ModeChoiceOption>().default(ModeChoiceOption.FAST)
    private val return_path_coords by option(
        help = "Whether lat/lon coordinates of chosen trip paths are returned." +
               "Paths only exist for trips with defined modes and within the focus area + buffer."
    ).boolean().default(false)
    private val parametrization by option(
        help="Parameter set to use (see options under omosim/src/main/resources/parametrization). " +
             "Individual parameter files can be overwritten with by setting them to custom files with the commands:" +
             "--population_file, --activity_group_file, --tour_utilities_file, --tour_utilities_file," +
             "--trip_utilities_file, --trip_utilities_file_for_calibration, --destination_choice_file"
    ).default("publication2023")
    private val population_file by option(
        help="Path to file that describes the socio-demographic makeup of the population. " +
             "Must be formatted like omosim/src/main/resources/parametrization/publication2023/Population.json."
    ).file(mustExist = true, mustBeReadable = true)
    private val activity_group_file by option(
        help="Path to file that describes the activity chains for each population group and the dwell-time distribution for the each chain. " +
        "Must be formatted like omosim/src/main/resources/parametrization/publication2023/ActivityGroup.json"
    ).file(mustExist = true, mustBeReadable = true)
    private val tour_utilities_file by option(
        help="Path to parameter file for the tour mode decision model." +
              "Must be formatted like omosim/src/main/resources/tourModeUtilities.json"
    ).file(mustExist = true, mustBeReadable = true)
    private val trip_utilities_file by option(
        help="Path to parameter file for the trip mode decision model. " +
             "Must be formatted like omosim/src/main/resources/tripModeUtilities.json"
    ).file(mustExist = true, mustBeReadable = true)
    private val trip_utilities_file_for_calibration by option(
        help="[Experimental] Path to parameter file for the trip mode decision model used for calibration." +
             "The model should result in the equivalent mode probabilities as the tour and trip models combined." +
             "Must be formatted like omosim/src/main/resources/tripModeUtilitiesCalibration.json"
    ).file(mustExist = true, mustBeReadable = true)
    private val destination_choice_file by option(
        help="Path to parameter file for the destination choice model. " +
             "Must be formatted like omosim/src/main/resources/LocChoiceWeightFuns.json"
    ).file(mustExist = true, mustBeReadable = true)
    private val n_worker by option(
        help="Number of parallel coroutines that can be executed at the same time. " +
             "Default: Number of CPU-Cores available."
    ).int()
    private val gtfs_file by option(
        help = "Path to an General Transit Feed Specification (GTFS) for the area. " +
               "Required for public transit routing," +
               "for example if public transit is an option in mode choice. " +
               "Must be a .zip file or a directory (see https://gtfs.org/). " +
               "Recommended download platform for Germany: https://gtfs.de/"
    ).file(mustExist = true, mustBeReadable = true)
    private val mapdata_overture by option(
        help = "Use overture map data instead of OSM for buildings and POIs. " +
               "Usage: --mapdata_overture RELEASE. Where RELEASE is a valid overture release. " +
               "For an introduction to Overture Maps see https://overturemaps.org/"
    )
    private val matsim_output_crs by option(
        help = "CRS of MatSIM output. Must be a code understood by org.geotools.referencing.CRS.decode()."
    ).default("EPSG:4326")
    private val mode_speed_up by option(
        help = "Value: MODE=FACTOR. Multiply the travel time of each trip of the mode by the factor." +
               "Example: CAR_DRIVER=0.3, will slow down car travel durations by 70%."
    ).splitPair()
     .convert { (first, second) -> Mode.valueOf(first.uppercase()) to second.toDouble() }
     .multiple()
     .toMap()
    private val calibrationParameters by CalibrationOptions().cooccurring()
    private val calibration_file_gravity by option(
        help = "[EXPERIMENTAL] Calibration file to use for the gravity model (destination choice). Generated by a calibration run."
    ).file(mustExist = true, mustBeReadable = true)
    private val calibration_file_mode_choice by option(
        help = "[EXPERIMENTAL] Calibration file to use for mode choice. Generated by a calibration run."
    ).file(mustExist = true, mustBeReadable = true)
    private val calibration_file_route_choice by option(
        help = "[EXPERIMENTAL] Calibration file to use for route choice. Generated by a calibration run."
    ).file(mustExist = true, mustBeReadable = true)
    private val sqlite_route_geometry_datatype by option(
        help = "Datatype of route column in trip table for SQLite output."
    ).enum<RouteGeomTypes>().default(RouteGeomTypes.TWKB)
    private val osm_file_roads by option(
        help = "Path to an osm.pbf file that is used for the road network (including foot paths). " +
               "Defaults to <osm_file>. " +
               "Only set this explicitly if routing data should be taken from a different osm file when POI and building data. "
    ).file(mustExist = true, mustBeReadable = true).default(osm_file)

    override fun run() {
        if ((census == null) && (agentNumberDefinition is ShareOfPop) && (calibrationParameters == null)) {
            throw Exception(
                "Agent population is supposed to be based on the population but no census file is provided." +
                "Consider adding a census file with --census or use --n_agents instead.")
        }
        if ((gtfs_file == null) && (mode_choice == ModeChoiceOption.GTFS)) {
            throw Exception(
                "Mode choice includes public transit but no GTFS file is provided." +
                "Add a gtfs file with --gtfs_file"
            )
        }

        // Init omosim
        val omosim = Omosim(
            area_geojson, osm_file,
            routingMode = routing_mode,
            odFile = od, censusFile = census,
            gridPrecision = grid_precision, bufferRadius = buffer, seed = seed,
            cache = true, cacheDir = cache_dir,
            populateBufferArea = populate_buffer_area,
            distanceCacheSize = distance_matrix_cache_size,
            populationFile = population_file,
            activityGroupFile = activity_group_file,
            nWorker = n_worker,
            gtfsFile = gtfs_file,
            overtureRelease = mapdata_overture,
            modeSpeedUp = mode_speed_up,
            parametrization = parametrization,
            tourModeUtilityFile = tour_utilities_file,
            tripModeUtilityFile = trip_utilities_file,
            tripModeUtilityCalibrationFile = trip_utilities_file_for_calibration,
            locationChoiceFile = destination_choice_file,
            osmFileRoads = osm_file_roads
        )

        // Apply Calibration
        if (calibration_file_gravity != null) {
            val finder = omosim.destinationFinder as DestinationFinderDefault
            GravityCalibrationStore
                .read(calibration_file_gravity!!, omosim.grid, omosim.buildings, finder.locChoiceWeightFuns)
        }
        if (calibration_file_mode_choice != null) {
            omosim.parameterReader.tourModeUtilityFile = calibration_file_mode_choice
        }
        if (calibration_file_route_choice != null) {
            omosim.routeChoiceCalibration = RouteChoiceCalibrationStore
                .read(omosim,calibration_file_route_choice!!, omosim.grid)
        }

        calibrationParameters?.let {
            // Check for mode choice before gravity
            var mc = false
            for (step in calibrationParameters!!.calibration_steps) {
                if (step.type == CalibrationType.MODE_CHOICE) {
                    mc = true
                }
                if (mc and (step.type == CalibrationType.GRAVITY)) {
                    de.uniwuerzburg.omosim.calibration.logger.warn(
                        "NOT IMPLEMENTED WARNING! Calibration: found mode choice before gravity model step. " +
                        "The mode choice calibration will be ignored during gravity model calibration. " +
                        "This might lead to worse results."
                    )
                }
            }

            // Calibration steps check for unsupported order
            val iLastGravity = calibrationParameters!!.calibration_steps.indexOfLast { it.type == CalibrationType.GRAVITY }
            val iToRemove = mutableListOf<Int>()
            val nSteps = calibrationParameters!!.calibration_steps.size
            for (i in calibrationParameters!!.calibration_steps.indices) {
                val step = calibrationParameters!!.calibration_steps[i]
                if ((i < iLastGravity) and (step.type == CalibrationType.MODE_CHOICE)) {
                    calLogger.warn("Found mode choice calibration before gravity calibration. Currently unsupported. Removing step $i")
                    iToRemove.add(i)
                }
                if ((i < iLastGravity) and (step.type == CalibrationType.ROUTE_CHOICE)) {
                    calLogger.warn("Found route choice calibration before gravity calibration. Currently unsupported. Removing step $i")
                    iToRemove.add(i)
                }
                if ((i < (nSteps - 1)) and (step.type == CalibrationType.EVALUATE)) {
                    calLogger.warn("Found calibration evaluation with remaining calibration steps. Currently unsupported. Removing step $i")
                    iToRemove.add(i)
                }
            }
            val calibrationSteps = calibrationParameters!!.calibration_steps.toMutableList()
            for (i in iToRemove.sortedDescending()) {
                calibrationSteps.removeAt(i)
            }

            // Create output files
            val calDir = calibrationParameters!!.calibration_out_dir
            Files.createDirectories(calDir)

            // Calibrate
            val calibrator = TrafficCountCalibrationContext(
                calibrationParameters!!.calibration_traffic_count_file,
                omosim,
                start_wd,
                calibrationParameters!!.calibration_population,
                calDir
            )
            calibrator.calibrate(calibrationSteps)
            return // Don't continue with normal run
        }

        // Mobility demand
        val agents = when (val aND = agentNumberDefinition ) {
            is FixedAgentNumber -> omosim.run(aND.value, start_wd, n_days)
            is ShareOfPop -> omosim.run(aND.value, start_wd, n_days)
        }

        // Mode Choice
        omosim.doModeChoice(agents, mode_choice, return_path_coords, verbose = true)

        // Format run parameters:
        val runParameters = mutableMapOf<String, String>()
        for (arg in this.registeredArguments()) {
            val sArg = arg.toString().split("=")
            runParameters[sArg.first()] = sArg.drop(1).joinToString("")
        }
        for (opt in this.registeredOptions()) {
            if (opt.toString().contains("=")) {
                val sOpt = opt.toString().split("=")
                runParameters[sOpt.first()] = sOpt.drop(1).joinToString("")
            }
        }

        // Store output
        for (outFn in out) {
            logger.get()?.info("Saving results to ${outFn}...")
            val success: Boolean
            when (outFn.extension) {
                "json" -> {
                    success = writeJSONOutput(
                        agents.map { formatOutput(it) },
                        outFn,
                        runParameters
                    )
                }
                "db" -> {
                    success = writeSQLite(
                        agents.map { formatOutput(it) },
                        outFn,
                        runParameters,
                        routeGeomType = sqlite_route_geometry_datatype
                    )
                }
                "xml" -> {
                    success = writeMatSim(
                        agents.map { formatOutput(it) },
                        outFn,
                        n_days,
                        matsim_output_crs,
                        runParameters
                    )
                }
                else -> {
                    logger.get()?.info(
                        "Warning! output file extension ${outFn.extension} is not implemented." +
                                "Available output formats: .json, .db (sqlite), .xml (MATSim)" +
                                "Falling back to JSON"
                    )
                    val newOut = File(outFn.parent, outFn.nameWithoutExtension + ".json")
                    success = writeJSONOutput(agents.map { formatOutput(it) }, newOut, runParameters)
                }
            }
            if (success) {
                logger.get()?.info("Saving results to ${outFn}... Done!")
            } else {
                logger.get()?.error("Error! Couldn't save results to $outFn")
            }
        }
    }
}

fun main(args: Array<String>) = Run().main(args)
