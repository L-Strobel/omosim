package de.uniwuerzburg.omosim.utils

import com.google.common.util.concurrent.AtomicDouble
import java.util.concurrent.atomic.AtomicInteger

/**
 * Progress bar
 *
 * @param processDescription Static text to the left of the progressbar
 * @param increments Step sizes of printed percentage increases (Default: 0.01% if interactive else 5%)
 */
class ProgressBar(
    val processDescription: String,
    totalTasks: Int,
    increments: Double? = null,
    var enabled: Boolean = true
) {
    val pauseLength: Double
    val totalTasks: Double = totalTasks.toDouble()
    var nextPrint = AtomicDouble(0.0)
    var tasksCompleted = AtomicInteger(0)

    init {
        pauseLength = if (increments != null) {
            increments
        } else if (System.console() == null) {
            5.0 // 5 %, likely Headless or IDE
        } else {
            0.1 // 0.1%, likely Terminal
        }
    }

    /**
     * Print progress after a single task is completed.
     */
    fun singleTaskComplete() {
        if (!enabled) { return }
        val n = tasksCompleted.incrementAndGet()
        val progress = n / totalTasks
        show(progress)
    }

    /**
     * Print progress
     * @param progress Percent of task completed [0, 1]
     */
    fun show(progress: Double) {
        if (!enabled) { return }
        val percent = progress * 100

        if (percent >= nextPrint.get()) {
            nextPrint.addAndGet(pauseLength)

            val ticks = (percent / 2).toInt()
            val bar = "[${"=".repeat(ticks)}${" ".repeat(50 - ticks)}]"
            val number = "%.2f".format(null, percent)
            print("$processDescription: $bar $number %\r")
        }
    }

    /**
     * Print completed progressbar
     */
    fun done() {
        if (!enabled) { return }

        val bar = "[${"=".repeat(50)}]"
        println("$processDescription: $bar Done!")
    }
}