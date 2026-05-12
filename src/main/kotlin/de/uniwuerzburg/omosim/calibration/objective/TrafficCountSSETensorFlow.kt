package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.TrafficSensor
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.types.TFloat32
import org.tensorflow.types.TInt64

class TrafficCountSSETensorFlow(
    val context: TrafficCountCalibrationContext
) : SGGravityObjectiveTF {
    override fun build (
        model: TfModel,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : TfModel {
        val tf = model.tf
        val n = context.omosim.grid.size
        val totalPopulation = tf.constant(context.totalPopulation.toFloat())

        //Test
        println("Test2")
        model.session = Session(model.graph, model.config)
        model.fillInputTensor(DoubleArray(model.nVars) {1.0})

        model.session.runner()
            .feed(model.x, model.inputTensor)
            .fetch(expectedTrips[ActivityType.OTHER])
            .run().use { result ->
                val output = result[0] as TFloat32
                println( output.getFloat(0,0) )
                println( output.getFloat(0,1) )
                println( output.getFloat(0,2) )
                println( output.getFloat(0,3) )
                println( output.getFloat(0,4) )
                println( output.getFloat(0,5) )
                println( output.getFloat(0,6) )
                println( output.getFloat(0,7) )
                println( output.getFloat(0,8) )
                println( output.getFloat(0,9) )
                println( output.getFloat(1,1) )
                println( output.getFloat(2,1) )
                println( output.getFloat(3,1) )
            }

        model.session.runner()
            .feed(model.x, model.inputTensor)
            .fetch(expectedTrips[ActivityType.WORK])
            .run().use { result ->
                val output = result[0] as TFloat32
                println( output.getFloat(0,0) )
                println( output.getFloat(0,1) )
                println( output.getFloat(0,2) )
                println( output.getFloat(0,3) )
                println( output.getFloat(1,1) )
                println( output.getFloat(2,1) )
                println( output.getFloat(3,1) )
            }


        // Get affected matrix
        val aMatrices = mutableMapOf<TrafficSensor, Array<FloatArray>>()
        for (sensor in context.sensors) {
            aMatrices[sensor] = Array(n) { FloatArray(n) { 0.0f } }
        }
        val sIndices = mutableMapOf<TrafficSensor, MutableList<LongArray>>()
        for (sensor in context.sensors) {
            sIndices[sensor] = mutableListOf<LongArray>()
        }
        for ((o, origin) in context.omosim.grid.withIndex()) {
            for ((d, destination) in context.omosim.grid.withIndex()) {
                val od = Pair(origin, destination)
                if (od in context.affectedSensors) {
                    val affected = context.affectedSensors[od]!!
                    for (sensor in affected) {
                        aMatrices[sensor]!![o][d] = 1.0f
                        sIndices[sensor]!!.add(longArrayOf(o.toLong(), d.toLong()))
                    }
                }
            }
        }
        val oMatrices = mutableMapOf<TrafficSensor, Operand<TFloat32>>()
        for (sensor in context.sensors) {
            oMatrices[sensor] = model.addMatrix(aMatrices[sensor]!!)
        }
        val oIndices = mutableMapOf<TrafficSensor, Operand<TInt64>>()
        for (sensor in context.sensors) {
            oIndices[sensor] = tf.reshape(tf.constant( sIndices[sensor]!!.toTypedArray() ), tf.constant(longArrayOf(-1, 2)))
        }

        // Simulated traffic counts
        /*val simCount = mutableMapOf<TrafficSensor, List<MutableList<Operand<TFloat32>>>>()
        for (sensor in context.sensors) {
            simCount[sensor] = List(CalibrationConstants.T) { mutableListOf() }
        }
        for (activity in ActivityType.entries) {
            val totalPopulation = tf.constant(context.totalPopulation.toFloat())
            val expectedTripsPopScaled = tf.math.mul(expectedTrips[activity]!!, totalPopulation)

            for (t in 0 until CalibrationConstants.T) {
                val timeShare = tf.constant(tripStartDistr[activity]!![t].toFloat())
                val expectedTripsTScaled = tf.math.mul(expectedTripsPopScaled, timeShare)

                for (sensor in context.sensors) {
                    val s = tf.reduceSum(
                        tf.math.mul(expectedTripsTScaled, oMatrices[sensor]!!),
                        tf.constant(intArrayOf(0, 1))
                    )
                    simCount[sensor]!![t].add(s)
                }
            }
        }*/

        val simCount = mutableMapOf<TrafficSensor, List<MutableList<Operand<TFloat32>>>>()
        for (sensor in context.sensors) {
            simCount[sensor] = List(CalibrationConstants.T) { mutableListOf() }
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
                /*val s = tf.reduceSum(
                    tf.math.mul(eTotal, oMatrices[sensor]!!),
                    tf.constant(intArrayOf(0, 1))
                )*/
                val g = tf.gatherNd(eTotal, oIndices[sensor]!!)
                val s = tf.reduceSum(g, tf.constant(0))
                println(s.shape())
                simCount[sensor]!![t].add(s)
            }
        }

        /*val simM = mutableListOf<Operand<TFloat32>>()
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
                simM.add(tf.math.mul(eTotal, oMatrices[sensor]!!))
            }
        }
        val simMStacked = tf.stack(simM)
        val s = tf.reduceSum(simMStacked, tf.constant(intArrayOf(1, 2)))*/

        /* Crashes pc
        // Simulated traffic counts
        val simCount = mutableMapOf<TrafficSensor, List<MutableList<Operand<TFloat32>>>>()
        for (sensor in context.sensors) {
            simCount[sensor] = List(CalibrationConstants.T) { mutableListOf() }
        }
        for ((o, origin) in context.omosim.grid.withIndex()) {
            for ((d, destination) in context.omosim.grid.withIndex()) {
                val od = Pair(origin, destination)
                if (od in context.affectedSensors) {
                    val affected = context.affectedSensors[od]!!
                    for (sensor in affected) {
                        for (t in 0 until CalibrationConstants.T) {
                            for (activity in ActivityType.entries) {
                                val e = tf.gatherNd(expectedTrips[activity]!!, tf.constant(intArrayOf(o, d)))
                                val ePop = tf.math.mul(e, tf.constant(context.totalPopulation.toFloat()))
                                val ePopT = tf.math.mul(ePop, tf.constant(tripStartDistr[activity]!![t].toFloat()))
                                simCount[sensor]!![t].add(ePopT)
                            }
                        }
                    }
                }
            }
        }*/

        // TEST
        /*println("Test3")
        val testTerm1 = tf.math.addN(simCount[context.sensors.first()]!![0])
        val testTerm3 = tf.math.addN(simCount[context.sensors[3]]!![0])
        model.session = Session(model.graph, model.config)
        model.fillInputTensor(DoubleArray(model.nVars) {1.0})

        model.session.runner()
            .feed(model.x, model.inputTensor)
            .fetch(testTerm1)
            .run().use { result ->
                val output = result[0] as TFloat32
                println( output.getFloat() )
            }
        model.session.runner()
            .feed(model.x, model.inputTensor)
            .fetch(testTerm3)
            .run().use { result ->
                val output = result[0] as TFloat32
                println( output.getFloat() )
            }*/
        // TEST

        // Objective
        val oTerms = mutableListOf<Operand<TFloat32>>()
        for (sensor in context.sensors) {
            for (t in 0 until CalibrationConstants.T) {
                val s = tf.math.addN(simCount[sensor]!![t])
                val diff = tf.math.sub(s, tf.constant(sensor.measurements[t].toFloat()))
                val sqrDiff = tf.math.square(diff)
                oTerms.add(sqrDiff)
            }
        }
        val obj = tf.math.addN(oTerms)


        /*val m = mutableListOf<Float>()
        for (t in 0 until CalibrationConstants.T) {
            for (sensor in context.sensors) {
                m.add(sensor.measurements[t].toFloat())
            }
        }
        val mV = tf.constant(m.toFloatArray())
        val diff = tf.math.sub(s, mV)
        val sqrDiff = tf.math.square(diff)
        val obj = tf.reduceSum(sqrDiff, tf.constant(0))*/

        model.finalize(obj)
        return model
    }
}