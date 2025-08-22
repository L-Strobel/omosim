package de.uniwuerzburg.omosim.io.json

import de.uniwuerzburg.omosim.core.models.Mode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Output format of trip
 */
@Serializable
@SerialName("Trip")
data class OutputTrip (
    override val legID: Int,
    val mode: Mode,
    val startTime: String,
    val distanceKilometer: Double?,
    val timeMinute: Double?,
    val lats: List<Double>?,
    val lons: List<Double>?
) : OutputLeg