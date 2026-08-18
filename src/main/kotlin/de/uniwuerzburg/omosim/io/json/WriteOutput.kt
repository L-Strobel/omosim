package de.uniwuerzburg.omosim.io.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Path
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
fun writeJSONOutput(output: List<OutputEntry>, file: Path, runParams: Map<String, String>) : Boolean {
    val amendedOutput = OutputFormat(runParams, output)
    file.outputStream().use { f ->
        Json.encodeToStream( amendedOutput, f)
    }
    return true
}