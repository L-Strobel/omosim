package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.Mode
import kotlinx.coroutines.CoroutineDispatcher
import java.util.*

interface Assignment {
    /**
     * Assign routes to agent trips. Unnecessary if the routes have already been determined during mode choice.
     */
    fun assign(
        agents: List<MobiAgent>,
        verbose: Boolean,
        dispatcher: CoroutineDispatcher,
        rng: Random,
        modeSpeedUp: Map<Mode, Double>
    ) : List<MobiAgent>
}