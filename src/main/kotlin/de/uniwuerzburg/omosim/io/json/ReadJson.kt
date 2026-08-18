package de.uniwuerzburg.omosim.io.json

import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.io.jsonHandler
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.readText

inline fun <reified T>readJson(path: Path): T {
    return readJson(path.readText(Charsets.UTF_8))
}

inline fun <reified T> readJsonFromResource(res: String): T {
    val txt = Omosim::class.java.classLoader.getResource(res)!!.readText(Charsets.UTF_8)
    return jsonHandler.decodeFromString(txt)
}

inline fun <reified T> readJson(txt: String): T {
    return jsonHandler.decodeFromString(txt)
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> readJsonStream(path: Path): T {
    return jsonHandler.decodeFromStream(path.inputStream())
}