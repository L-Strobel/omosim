package de.uniwuerzburg.omosim.calibration.algorithms

import de.uniwuerzburg.omosim.calibration.logger
import kotlin.time.Duration

internal object ProgressLogger {
    fun logParameters(name: String, parameters: String) {
        logger.info("Starting $name. Parameters:${parameters}")
    }

    fun logInitialLoss(name: String, loss: Double) {
        logger.info("$name. Initial loss: $loss")
    }

    fun logProgressHeader() {
        logger.debug("Algorithm:Iteration:IterationTime:BestLoss:IterationLoss")
    }

    fun logProgress(algorithm: String, iteration: Int, timeLastIteration: Duration, oval: Double, iOVal: Double) {
        logger.debug("{}:{}:{}:{}:{}", algorithm, iteration, timeLastIteration, oval, iOVal)
    }

    fun logFinalLoss(name: String, loss: Double) {
        logger.info("Finished $name. Loss:$loss")
    }

    fun logEarlyTermination(name: String, reason: String) {
        logger.debug("$name. Early termination: $reason")
    }
}