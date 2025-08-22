package de.uniwuerzburg.omosim.io

import kotlinx.serialization.json.Json

// Globally used json config
val jsonHandler = Json { encodeDefaults = true; ignoreUnknownKeys = true}