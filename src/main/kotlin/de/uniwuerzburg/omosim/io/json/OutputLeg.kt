package de.uniwuerzburg.omosim.io.json

import kotlinx.serialization.Serializable

@Serializable
sealed interface OutputLeg {
    val legID: Int
}