package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.TrafficSensor
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelMV
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import org.tensorflow.types.TInt64

class TrafficCountSeparateTensorFlow(
    val context: TrafficCountCalibrationContext
) : SGGravityObjectiveTF<TfModelMV>  {
    override fun build (
        core: TfModelCore,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : TfModelMV {
        val tf = core.tf
        val totalPopulation = tf.constant(context.totalPopulation.toFloat())

        // Get origin-destination combinations that affect each sensor
        val sensorAffectedIndices = mutableMapOf<TrafficSensor, MutableList<LongArray>>()
        for (sensor in context.sensors) {
            sensorAffectedIndices[sensor] = mutableListOf<LongArray>()
        }
        for ((o, origin) in context.omosim.grid.withIndex()) {
            for ((d, destination) in context.omosim.grid.withIndex()) {
                val od = Pair(origin, destination)
                if (od in context.affectedSensors) {
                    val affected = context.affectedSensors[od]!!
                    for (sensor in affected) {
                        sensorAffectedIndices[sensor]!!.add(longArrayOf(o.toLong(), d.toLong()))
                    }
                }
            }
        }
        val oIndices = mutableMapOf<TrafficSensor, Operand<TInt64>>()
        for (sensor in context.sensors) {
            val rawIndices = tf.constant( sensorAffectedIndices[sensor]!!.toTypedArray() )
            oIndices[sensor] = tf.reshape(rawIndices, tf.constant(longArrayOf(-1, 2))) // Ensure correct dimensions
        }

        // Simulated traffic counts
        val simCount = mutableMapOf<TrafficSensor, MutableList<Operand<TFloat32>>>()
        for (sensor in context.sensors) {
            simCount[sensor] = MutableList(CalibrationConstants.T) { tf.constant(0f) }
        }
        for (t in 0 until CalibrationConstants.T) {
            val e = mutableListOf<Operand<TFloat32>>()
            for (activity in ActivityType.entries) {
                val expectedTripsPopScaled = tf.math.mul(expectedTrips[activity]!!, totalPopulation)
                val timeShare = tf.constant(tripStartDistr[activity]!![t].toFloat())
                val expectedTripsTScaled = tf.math.mul(expectedTripsPopScaled, timeShare)
                e.add(expectedTripsTScaled)
            }
            val eTotal = tf.math.addN(e)

            for (sensor in context.sensors) {
                val gathered = tf.gatherNd(eTotal, oIndices[sensor]!!)
                simCount[sensor]!![t] = tf.reduceSum(gathered, tf.constant(0))
            }
        }

        // TODO
        // Objective
        val s = mutableListOf<Operand<TFloat32>>()
        val m = mutableListOf<Float>()
        for (sensor in context.sensors) {
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

        val model = TfModelMV(2, core, listOf()) // TODO
        return model
    }
}