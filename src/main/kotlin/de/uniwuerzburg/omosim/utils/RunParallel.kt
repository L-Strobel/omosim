package de.uniwuerzburg.omosim.utils

import de.uniwuerzburg.omosim.core.AppConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.util.*
import kotlin.time.TimeSource

class SeededData<T>(val data: T, val seed: Long)

fun <T> CoroutineDispatcher.runParallel(
    taskData: List<T>,
    rng: Random,
    progressBar: Boolean = false,
    processName: String = "",
    logger: Logger? = null,
    task: (T, seed: Long) -> Unit,
) {
    // Progressbar setup
    val timeSource = TimeSource.Monotonic
    val timestampStartInit = timeSource.markNow()
    val pBar = ProgressBar(processName, taskData.size, enabled = progressBar)

    // Assign in parallel
    runBlocking(this) {
        val channel = Channel<SeededData<T>>(capacity = AppConstants.nAllowedCoroutines)

        // Producer Coroutine
        launch {
            for (data in taskData) {
                val seed = rng.nextLong() // Ensure determinism
                channel.send(SeededData(data, seed)) // Suspends when channel is full
            }
            channel.close()
        }

        // Worker Coroutines
        repeat(AppConstants.nAllowedCoroutines) {
            launch {
                for (data in channel) {
                    task(data.data, data.seed)
                    pBar.singleTaskComplete()
                }
            }
        }
    }

    pBar.done()
    logger?.info("$processName took: ${timeSource.markNow() - timestampStartInit}")
}