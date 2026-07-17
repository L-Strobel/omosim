package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.TripVisitor
import de.uniwuerzburg.omosim.utils.ProgressBar
import de.uniwuerzburg.omosim.utils.runParallel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource

interface Assignment {
    val tripVisitor: TripVisitor // Assignment logic

    /**
     * Assign routes to agent trips. Unnecessary if the routes have already been determined during mode choice.
     */
    fun assign(
        agents: List<MobiAgent>,
        verbose: Boolean,
        dispatcher: CoroutineDispatcher,
        rng: Random,
    ) : List<MobiAgent> {
        dispatcher.runParallel(
            agents, rng, progressBar = true, processName = "Assigning routes", logger = logger.get()
        ) { agent, seed ->
            val taskRng = Random(seed)
            for (diary in agent.mobilityDemand) {
                diary.visitTrips(tripVisitor, taskRng)
            }
        }
        return agents
    }
}