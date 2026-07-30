package de.uniwuerzburg.omosim.io.json


import de.uniwuerzburg.omosim.io.jsonHandler
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText


@Suppress("unused")
inline fun <reified T> writeJson(data: T, path: Path) {
    path.writeText(jsonHandler.encodeToString(data))
}

inline fun <reified T> writeJson(data: T, file: File) {
    file.writeText(jsonHandler.encodeToString(data))
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> writeJsonStream(data: T, path: Path) {
    Files.newOutputStream(path).use { outputStream ->
        jsonHandler.encodeToStream( data, outputStream)
    }
}

