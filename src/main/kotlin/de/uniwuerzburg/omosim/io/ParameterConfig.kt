package de.uniwuerzburg.omosim.io

import kotlinx.serialization.Serializable

@Serializable
class ParameterConfig (
    val dependencies: Dependencies
)

@Serializable
class Dependencies (
    val parent: String
)