package de.uniwuerzburg.omosim.calibration.algorithms

import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.time.measureTime

/**
 * Particle swarm optimization.
 *
 * Warning will use random variables independent of seed! (for performance reasons)
 */
object PSO {
    private const val NAME = "PSO"

    object Defaults {
        const val iterations = 1000
        const val lb = 1e-3
        const val ub = 1e3
        const val nParticles = 20
        const val w = 1.0
        const val phiP = 2.05
        const val phiG = 2.05
        const val vClamp = 0.1
        val boundStrategy = BoundStrategy.REFLECT_Z
    }

    fun run (
        nDimensions: Int,
        objective: (DoubleArray) -> Double,
        nWorker: Int? = null,
        parameters: Map<String, String>? = null,
    ) : DoubleArray {
        return run(
            nDimensions,
            objective,
            iterations = parameters?.get("iterations")?.toIntOrNull() ?: Defaults.iterations,
            nWorker,
            lb = parameters?.get("lb")?.toDoubleOrNull() ?: Defaults.lb,
            ub = parameters?.get("ub")?.toDoubleOrNull() ?: Defaults.ub,
            nParticles = parameters?.get("nParticles")?.toIntOrNull() ?: Defaults.nParticles,
            w = parameters?.get("w")?.toDoubleOrNull() ?: Defaults.w,
            phiP = parameters?.get("phiP")?.toDoubleOrNull() ?: Defaults.phiP,
            phiG = parameters?.get("phiG")?.toDoubleOrNull() ?: Defaults.phiG,
            vClamp = parameters?.get("vClamp")?.toDoubleOrNull() ?: Defaults.vClamp,
            boundStrategy = parameters?.get("boundStrategy")?.toBoundStrategy() ?: Defaults.boundStrategy,
        )
    }

    fun run(
        nDimensions: Int,
        objective: (DoubleArray) -> Double,
        iterations: Int = Defaults.iterations,
        nWorker: Int? = null,
        lb: Double = Defaults.lb,
        ub: Double = Defaults.ub,
        nParticles: Int = Defaults.nParticles,
        w: Double = Defaults.w,
        phiP: Double = Defaults.phiP,
        phiG: Double = Defaults.phiG,
        vClamp: Double = Defaults.vClamp,
        boundStrategy: BoundStrategy = Defaults.boundStrategy,
    ) : DoubleArray {
        ProgressLogger.logParameters(
            this.NAME,
            "lb=$lb:ub$ub:nParticles$nParticles:w$w:phiP$phiP:phiG$phiG:vClamp$vClamp:boundStrategy$boundStrategy"
        )

        val maxVelocity = vClamp * (ub - lb)

        // Initial mse
        var globalBestPosition = DoubleArray(nDimensions) { 1.0 }
        var globalBest = objective(globalBestPosition)
        ProgressLogger.logInitialLoss(this.NAME, globalBest)

        // Initialize particles
        val particles = List(nParticles) {
            val x = DoubleArray(nDimensions) { ThreadLocalRandom.current().nextDouble(lb, ub) }
            val v = DoubleArray(nDimensions) { ThreadLocalRandom.current().nextDouble(-maxVelocity, maxVelocity) }
            val oval = objective(x)
            if (oval < globalBest) {
                globalBest = oval
                globalBestPosition = x.copyOf()
            }
            PSOParticle(v, x, x, oval)
        }

        ProgressLogger.logProgressHeader()
        for(iteration in 0 until iterations ) {
            val time = measureTime {
                val executor = if (nWorker == null)
                    Executors.newWorkStealingPool()
                else {
                    Executors.newWorkStealingPool(nWorker)
                }
                for (particle in particles) {
                    executor.submit {
                        var inBound = true
                        for (i in 0 until nDimensions) {
                            val rp = ThreadLocalRandom.current().nextDouble()
                            val rg = ThreadLocalRandom.current().nextDouble()

                            // Update velocity
                            var velocity =
                                w * particle.velocity[i] +
                                        phiP * rp * (particle.bestPosition[i] - particle.position[i]) +
                                        phiG * rg * (globalBestPosition[i] - particle.position[i])

                            // Clamp
                            velocity = min(velocity, maxVelocity)
                            velocity = max(velocity, -maxVelocity)
                            particle.velocity[i] = velocity

                            // Update position
                            particle.position[i] += particle.velocity[i]

                            // Bound handling
                            val dInBound = when(boundStrategy) {
                                BoundStrategy.REFLECT_Z -> bhReflectZ(particle, i, lb, ub, ThreadLocalRandom.current())
                                BoundStrategy.INFINITY  -> bhInfinity(particle, i, lb, ub)
                            }
                            inBound = inBound && dInBound
                        }

                        if (inBound) {
                            // Check performance
                            val oval = objective(particle.position)

                            if (oval < particle.best) {
                                particle.bestPosition = particle.position.copyOf()
                                particle.best = oval
                            }
                        }
                    }
                }
                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.HOURS) // Wait as long as necessary.

                for (particle in particles) {
                    if (particle.best < globalBest) {
                        globalBestPosition = particle.bestPosition.copyOf()
                        globalBest = particle.best
                    }
                }
            }

            ProgressLogger.logProgress(this.NAME, iteration, time, globalBest)
        }
        ProgressLogger.logFinalLoss(this.NAME, globalBest)
        return globalBestPosition
    }

    /**
        Based on:
        doi: 10.1109/TEVC.2012.2189404
    */
    enum class BoundStrategy {
        INFINITY, REFLECT_Z
    }

    private fun String.toBoundStrategy() : BoundStrategy? {
        for (entry in BoundStrategy.entries) {
            if (this == entry.toString()) {
                return entry
            }
        }
        return null
    }

    /**
     * Allow movement out of bounds but assign objective function value of INF
     */
    private fun bhInfinity(
        particle: PSOParticle,
        i: Int,
        lb: Double,
        ub: Double
    ) : Boolean {
        if (particle.position[i] < lb) { return false }
        if (particle.position[i] >= ub) { return false }
        return true
    }

    /**
     * Reflect particle at bound and set velocity to zero.
     */
    private fun bhReflectZ(
        particle: PSOParticle,
        i: Int,
        lb: Double,
        ub: Double,
        rng: Random
    ) : Boolean {
        if (particle.position[i] < lb) {
            particle.position[i] = lb + (lb - particle.position[i])
            particle.velocity[i] = 0.0
        }
        if (particle.position[i] >= ub) {
            particle.position[i] = ub - (particle.position[i] - ub)
            particle.velocity[i] = 0.0
        }
        // If multiple reflections would be necessary randomly place particle
        if ((particle.position[i] < lb) ||  (particle.position[i] > ub)) {
            particle.position[i] = rng.nextDouble(lb, ub)
        }
        return true
    }

    private class PSOParticle(
        var velocity: DoubleArray,
        var position: DoubleArray,
        var bestPosition: DoubleArray,
        var best: Double
    )
}
