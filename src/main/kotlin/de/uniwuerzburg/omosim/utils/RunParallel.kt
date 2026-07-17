package de.uniwuerzburg.omosim.utils

import de.uniwuerzburg.omosim.core.AppConstants
import de.uniwuerzburg.omosim.core.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.util.Random
import kotlin.collections.chunked
import kotlin.time.TimeSource


fun <T> CoroutineDispatcher.runParallel(
    taskData: List<T>,
    rng: Random,
    progressBar: Boolean = false,
    processName: String = "",
    logger: Logger? = null,
    task: (T, rng: Random) -> Unit,
) {
    // Progressbar setup
    val timeSource = TimeSource.Monotonic
    val timestampStartInit = timeSource.markNow()
    val pBar = ProgressBar(processName, taskData.size, enabled = progressBar)

    // Assign in parallel
    for (chunk in taskData.chunked(AppConstants.nAllowedCoroutines)) { // Don't launch to many coroutines at once
        runBlocking(this) {
            for (data in chunk) {
                val coroutineRng = Random(rng.nextLong())
                launch {
                    task(data, coroutineRng)
                    pBar.singleTaskComplete()
                }
            }
        }
    }

    pBar.done()
    logger?.info("$processName took: ${timeSource.markNow() - timestampStartInit}")
}