package de.uniwuerzburg.omosim.io.json

import de.uniwuerzburg.omosim.core.models.Mode
import kotlinx.serialization.Serializable

@Serializable
class OutputPTLeg (
    val mode: Mode,
    val timeMinute: Double,
    val distanceKilometer: Double?,
    val departureStop: String?
)