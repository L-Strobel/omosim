package de.uniwuerzburg.omosim.calibration.algorithms

import de.uniwuerzburg.omosim.calibration.differentiablemodel.DifferentiableModelUV
import smile.math.BFGS
import kotlin.time.measureTimedValue

object BFGS {
    private const val NAME = "BFGS"

    object Defaults {
        const val iterations = 100
        const val m = 5
        const val gTol = 1e-5
        const val lb = 1e-3
        const val ub = 1e3
    }

    fun run (
        model: DifferentiableModelUV,
        x0: DoubleArray,
        parameters: Map<String, String>? = null,
    ) : DoubleArray {
        return run(
            model,
            x0,
            iterations = parameters?.get("iterations")?.toIntOrNull() ?: Defaults.iterations,
            lb = parameters?.get("lb")?.toDoubleOrNull() ?: Defaults.lb,
            ub = parameters?.get("ub")?.toDoubleOrNull() ?: Defaults.ub,
            m = parameters?.get("m")?.toIntOrNull() ?: Defaults.m,
            gTol = parameters?.get("gTol")?.toDoubleOrNull() ?: Defaults.gTol,
        )
    }

    fun run(
        model: DifferentiableModelUV,
        x0: DoubleArray,
        iterations: Int = Defaults.iterations,
        lb: Double = Defaults.lb,
        ub: Double = Defaults.ub,
        m: Int = Defaults.m,
        gTol: Double = Defaults.gTol,
    ) : DoubleArray {
        ProgressLogger.logParameters(this.NAME,"lb=$lb:ub$ub:m$m:gTol$gTol")

        val x = x0.copyOf()
        val l = DoubleArray(model.nVars){ lb }
        val u = DoubleArray(model.nVars){ ub }

        ProgressLogger.logInitialLoss(this.NAME, model.evaluate(x0))
        ProgressLogger.logProgressHeader()
        val (solution, time) = measureTimedValue {
            BFGS.minimize(model, m, x, l, u, gTol, iterations)
        }

        ProgressLogger.logProgress(this.NAME, iterations, time, solution) // Progress logging not possible with SMILE
        ProgressLogger.logFinalLoss(this.NAME, solution)
        return x
    }
}