package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.core.models.MobiAgent
import de.uniwuerzburg.omosim.core.models.TripVisitor
import de.uniwuerzburg.omosim.utils.ProgressBar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        dispatcher: CoroutineDispatcher
    ) : List<MobiAgent> {

        // Progressbar setup
        val timeSource = TimeSource.Monotonic
        val timestampStartInit = timeSource.markNow()
        val jobsDone = AtomicInteger()
        val totalJobs = (agents.size).toDouble()

        // Assign in parallel
        for (chunk in agents.chunked(AppConstants.nAllowedCoroutines)) { // Don't launch to many coroutines at once
            runBlocking(dispatcher) {
                for (agent in chunk) {
                    launch(dispatcher) {
                        for (diary in agent.mobilityDemand) {
                            diary.visitTrips(tripVisitor)
                        }
                        val done = jobsDone.incrementAndGet()
                        if (verbose) {
                            print("Assigning routes: ${ProgressBar.show(done / totalJobs)}\r")
                        }
                    }
                }
            }
        }

        if(verbose) { println("Assigning routes: " + ProgressBar.done()) }
        logger.get()?.info("Assigning routes took: ${timeSource.markNow() - timestampStartInit}")
        return agents
    }
}