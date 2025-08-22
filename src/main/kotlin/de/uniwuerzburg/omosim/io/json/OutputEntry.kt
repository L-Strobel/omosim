package de.uniwuerzburg.omosim.io.json

import de.uniwuerzburg.omosim.core.models.HomogeneousGrp
import de.uniwuerzburg.omosim.core.models.MobilityGrp
import de.uniwuerzburg.omosim.core.models.Sex
import kotlinx.serialization.Serializable

/**
 * omosim result format of on agent
 */
@Serializable
data class OutputEntry (
    val id: Int,
    val homogenousGroup: HomogeneousGrp,
    val mobilityGroup: MobilityGrp,
    val age: Int?,
    val sex: Sex,
    val carAccess: Boolean,
    val mobilityDemand: List<OutputDiary>
)