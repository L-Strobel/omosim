package de.uniwuerzburg.omosim.core.models

import kotlinx.serialization.Serializable

@Serializable
data class PopulationConfig(
    val populationWideValues: PopulationWideValues = PopulationWideValues(),
    val strata: List<PopStratum>
)