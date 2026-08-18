package de.uniwuerzburg.omosim.io.gtfs

import de.uniwuerzburg.omosim.io.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.locationtech.jts.geom.Envelope
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.*


/**
 * Clip GTFS file to bounding box and write the result to the cache directory.
 * Methodology:
 * 1. Clip stops by bounding box. Yields stop IDs.
 * 2. Filter stop times by the remaining stop IDs. Yields trip IDs.
 * 3. Filter trips by remaining trip IDs. Yields route and service IDs.
 * 4. Filter routes by remaining route IDs. Yields agency IDS.
 * 5. Filter agencies by remaining agency IDs.
 * 6. Filter calendar and calendar_dates by service IDs.
 *
 * Transfers.txt is currently NOT included.
 *
 * @param bbBox Bounding box
 * @param gtfsPath Location of gtfs data can be a directory or a zip file
 * @param cacheDir Cache directory
 */
fun clipGTFSFile(
    bbBox: Envelope,
    gtfsPath: Path,
    cacheDir: Path,
    dispatcher: CoroutineDispatcher
) {
    logger.info("Clipping GTFS to bounding box...")
    var inputStreams = loadInputStreams(gtfsPath)

    // Create directory in cache
    Files.createDirectories(cacheDir)

    // Clip Stops
    val stops = filterGTFSFile(
        inputStreams["stops.txt"]!!,
        cacheDir.resolve("stops.txt"),
        listOf("stop_id"),
        BBFilter(bbBox),
        dispatcher
    ).first().toMutableSet()

    // Get trips
    val trips = filterGTFSFile(
        inputStreams["stop_times.txt"]!!,
        cacheDir.resolve("stop_times.txt"),
        listOf("trip_id"),
        ForeignKeyFilter(stops, "stop_id"),
        dispatcher
    ).first()

    // Reload input streams. Necessary to access stop times again.
    inputStreams.forEach{ it.value.close() }
    inputStreams = loadInputStreams(gtfsPath)

    // Filter stop times
    stops += filterGTFSFile(
        inputStreams["stop_times.txt"]!!,
        cacheDir.resolve("stop_times.txt"),
        listOf("stop_id"),
        ForeignKeyFilter(trips, "trip_id"),
        dispatcher
    ).first()

    // Get stops on trips
    filterGTFSFile(
        inputStreams["stops.txt"]!!,
        cacheDir.resolve("stops.txt"),
        listOf(),
        ForeignKeyFilter(stops, "stop_id"),
        dispatcher
    )

    // Filter trips
    val tripsFKeys = filterGTFSFile(
        inputStreams["trips.txt"]!!,
        cacheDir.resolve("trips.txt"),
        listOf("route_id", "service_id"),
        ForeignKeyFilter(trips, "trip_id"),
        dispatcher
    )
    val routes   = tripsFKeys[0]
    val services = tripsFKeys[1]

    // Filter routes
    val agencies = filterGTFSFile(
        inputStreams["routes.txt"]!!,
        cacheDir.resolve("routes.txt"),
        listOf("agency_id"),
        ForeignKeyFilter(routes, "route_id"),
        dispatcher
    ).first()

    // Filter agencies
    filterGTFSFile(
        inputStreams["agency.txt"]!!,
        cacheDir.resolve("agency.txt"),
        listOf(),
        ForeignKeyFilter(agencies, "agency_id"),
        dispatcher
    )

    // Filter calendar
    filterGTFSFile(
        inputStreams["calendar.txt"]!!,
        cacheDir.resolve("calendar.txt"),
        listOf(),
        ForeignKeyFilter(services, "service_id"),
        dispatcher
    )
    try {
        // Filter calendar dates
        filterGTFSFile(
            inputStreams["calendar_dates.txt"]!!,
            cacheDir.resolve("calendar_dates.txt"),
            listOf(),
            ForeignKeyFilter(services, "service_id"),
            dispatcher
        )
    }catch(_: NullPointerException){
        logger.warn("GTFS data does not contain a calendar_dates file!")
    }
    logger.info("Clipping GTFS to bounding box... Done!")
}

/**
 * Read GTFS data table, apply a specified filter, and write it to another location.
 * Optionally returns the unique values in several columns after filtering.
 * These are usually used as foreign keys into different tables.
 *
 * @param inputStream Source of GTFS data table
 * @param outputPath Place to store the reduced GTFS table
 * @param colsToExtract Return the unique values in these columns
 * @param filter Filter to apply to the data table
 * @return Unique values in specified columns after filtering is done
 */
private fun filterGTFSFile(
    inputStream: InputStream,
    outputPath: Path,
    colsToExtract: List<String>,
    filter: GTFSFilter,
    dispatcher: CoroutineDispatcher
) : List<Set<String>> {
    val writer = outputPath.bufferedWriter()
    val reader = inputStream.bufferedReader(Charsets.UTF_8)

    // Regex for csv separator. ',' but not in quotations.
    val delimiter = Regex(""",(?=(?:[^"]*"[^"]*")*[^"]*${'$'})""")

    // Parse header
    var header = reader.readLine()
    header = header.removePrefix("\uFEFF") // Remove BOM
    val idxMap = header
        .split(delimiter).withIndex().associate { (i, v) ->
            v.trim().removeSurrounding("\"") to i
        }
    writer.appendLine(header)

    // Index of cols to extract
    val extractIdxs = colsToExtract.map { idxMap[it]!! }

    // Read and parse body in parallel
    val (extractedData, filteredRecords) = runBlocking(dispatcher) {
        channelFlow {
            for (recordChunk in reader.lineSequence().chunked(100_000)) {
                launch {
                    for (record in recordChunk) {
                        val values = record
                            .split(delimiter)
                            .map { it.trim().removeSurrounding("\"") }
                        if (filter.filter(values, idxMap)) {
                            val extractedValues = extractIdxs.map { values[it] }
                            send(Pair(extractedValues, record))
                        }
                    }
                }
            }
        }.toList()
    }.unzip()

    // Sort records based on the first column to extract
    val sortedRecords = extractedData.zip(filteredRecords)
        .sortedBy { (extractedValues, _) -> extractedValues.joinToString() }
        .map { (_, record) -> record }

    // Write filtered body
    for (record in sortedRecords) {
        writer.append(record)
        writer.newLine()
    }

    val extractedSets: List<MutableSet<String>> = List(colsToExtract.size) { mutableSetOf() }
    for (data in extractedData) {
        for ((i, v) in data.withIndex()) {
            extractedSets[i].add(v)
        }
    }

    // Close all
    writer.close()
    reader.close()
    inputStream.close()
    return extractedSets
}

private fun loadInputStreams( gtfsPath: Path ) : MutableMap<String, InputStream> {
    val inputStreams: MutableMap<String, InputStream> = mutableMapOf()
    if (gtfsPath.isDirectory()){
        for (path in gtfsPath.listDirectoryEntries("*.txt")) {
            inputStreams[path.name] = path.inputStream()
        }
    } else if (gtfsPath.toFile().extension == "zip") {
        val zipFile = ZipFile(gtfsPath.toFile())
        for (entry in zipFile.entries()) {
            inputStreams[entry.name] = zipFile.getInputStream(entry)
        }
    } else {
        throw IOException("GTFS file must be directory or .zip!")
    }
    return inputStreams
}
