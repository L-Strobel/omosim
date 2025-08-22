package de.uniwuerzburg.omosim.io.json

import de.uniwuerzburg.omosim.core.models.ActivityType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * omosim result format of on activity
 */
@Serializable
@SerialName("Activity")
data class OutputActivity (
    override val legID: Int,
    val activityType: ActivityType,
    val startTime: String,
    val stayTimeMinute: Double?,
    val lat: Double,
    val lon: Double,
    val dummyLoc: Boolean,
    val inFocusArea: Boolean
) : OutputLeg