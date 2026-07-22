package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficSensor
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.QuadraticTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.Term
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

/**
 * Objective: sum(m-s)^2
 *
 * m: measurement
 * s: simulated value
 *
 * For differentiable model build with TensorFlow.  @see de.uniwuerzburg.omod.calibration.differentiablemodel
 */
fun sseObjectiveTF(
    core: TfModelCore,
    sensors: List<TrafficSensor>,
    simCount: Map<TrafficSensor, List<Operand<TFloat32>>>
) : Operand<TFloat32> {
    val tf = core.tf

    val s = mutableListOf<Operand<TFloat32>>()
    val m = mutableListOf<Float>()
    for (sensor in sensors) {
        for (t in 0 until CalibrationConstants.T) {
            s.add(simCount[sensor]!![t])
            m.add(sensor.measurements[t].toFloat())
        }
    }
    val vSim = tf.stack(s)
    val vMeasured = tf.constant(m.toFloatArray())
    val diff = tf.math.sub(vSim, vMeasured)
    val sqrDiff = tf.math.square(diff)
    val obj = tf.reduceSum(sqrDiff, tf.constant(0))

    return obj
}