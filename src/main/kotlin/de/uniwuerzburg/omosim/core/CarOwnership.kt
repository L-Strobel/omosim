package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.PopStratum
import java.util.*

/**
 * Determines car ownership of an agent.
 */
interface CarOwnership {
    fun determine(agent: MobiAgent, stratum: PopStratum, rng: Random) : Boolean
}