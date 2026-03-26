package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.calibration.algorithms.GradientDescent
import de.uniwuerzburg.omosim.calibration.surrogate.SGGravity
import de.uniwuerzburg.omosim.core.models.ActivityType

class DistanceFunctionMatchContext(
    val mean: Double,
    val activity: ActivityType
) {

    fun calibrate() {
        /*val model = SGGravity(context).buildModelSSE(activity)
        val x0 = DoubleArray(context.omosim.grid.size - 1) { 1.0 }
        var d = GradientDescent.run(model, x0, parameters=parameters)*/
    }
}