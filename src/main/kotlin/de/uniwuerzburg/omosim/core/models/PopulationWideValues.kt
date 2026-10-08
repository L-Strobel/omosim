package de.uniwuerzburg.omosim.core.models

import kotlinx.serialization.Serializable

@Serializable
data class PopulationWideValues(
    val minDrivingAge: Int = 17,
    val minIncomeAge: Int = 16,
)