package de.uniwuerzburg.omosim.calibration

import com.gurobi.gurobi.*
import de.uniwuerzburg.omosim.calibration.CalibrationConstants.T
import de.uniwuerzburg.omosim.calibration.differentiablemodel.*
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfAccumulatingTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.core.models.ActivityType
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.types.TFloat32

/**
 * Objective: sum(m-s)^2
 *
 * m: measurement
 * s: simulated value
 *
 * For differentiable model.  @see de.uniwuerzburg.omod.calibration.differentiablemodel
 */
fun sseObjective(nVars: Int, sensors: List<TrafficSensor>, simCount: Map<TrafficSensor, List<Term>>) : LinearTerm {
    val obj = LinearTerm(nVars)
    for (sensor in sensors) {
        for (t in 0 until T) {
            // (m - s)^2 = m^2 - 2ms + s^2
            val s = simCount[sensor]!![t]
            val m = sensor.measurements[t]
            obj.addConstant(m * m)
            obj.addTerm(s, -2 * m)
            val qTerm = QuadraticTerm(nVars, s, s,1.0)
            obj.addTerm(qTerm, 1.0)
        }
    }
    return obj
}

/**
 * Objective: sum(m-s)^2
 *
 * m: measurement
 * s: simulated value
 *
 * For Gurobi.
 */
fun grbSseObjective(
    model: GRBModel, sensors: List<TrafficSensor>, simCount: Map<TrafficSensor, List<GRBLinExpr>>
) : GRBExpr {
    // Create Gurobi variable for simCount. Necessary for GRBQuadExpr
    val vSimCount = List(T) {
        model.addVars(
            DoubleArray(sensors.size) { 0.0 },
            null,
            DoubleArray(sensors.size) { 0.0 },
            CharArray(sensors.size) { GRB.CONTINUOUS },
            Array(sensors.size) { "" }
        )
    }
    for ((i, sensor) in sensors.withIndex()) {
        for (t in 0 until T) {
            model.addConstr(
                simCount[sensor]!![t],
                GRB.EQUAL,
                vSimCount[t][i]!!,
                "cnteq"
            )
        }
    }

    // Objective
    val obj = GRBQuadExpr()
    for ((i, sensor) in sensors.withIndex()) {
        for (t in 0 until T) {
            // (Sm - Ss)^2 = Sm^2 - 2SmSs + Ss^2
            obj.addConstant(sensor.measurements[t] * sensor.measurements[t])
            obj.addTerm(-2 * sensor.measurements[t], vSimCount[t][i])
            obj.addTerm(1.0, vSimCount[t][i], vSimCount[t][i])
        }
    }
    return obj
}

interface SGGravityObjectiveNative <M: DifferentiableModel> {
    fun build (
        nVars: Int,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : M
}

interface SGGravityObjectiveTF {
    fun build (
        nVars: Int,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : TfModel
}

class TrafficCountSSE(
    val context: TrafficCountCalibrationContext
) : SGGravityObjectiveNative<DifferentiableModelUVBase> {
    override fun build (
        nVars: Int,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : DifferentiableModelUVBase {
        // Simulated traffic counts
        val simCount = getSimCountsFromDemand(nVars, context, expectedTrips, tripStartDistr)

        // Objective
        val obj = sseObjective(nVars, context.sensors, simCount)

        // Create model
        val model = DifferentiableModelUVBase(nVars)
        model.setRootTerm(obj)

        return model
    }
}

class TrafficCountSeparate(
    val context: TrafficCountCalibrationContext
) : SGGravityObjectiveNative<DifferentiableModelMV> {
    override fun build (
        nVars: Int,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : DifferentiableModelMV {
        // Simulated traffic counts
        val simCount = getSimCountsFromDemand(nVars, context, expectedTrips, tripStartDistr)

        // Create DifferentiableModelMultiOut from simulated counts
        val countsFlat = mutableListOf<Term>()
        for (sensor in context.sensors) {
            for (t in 0 until T) {
                countsFlat.add( simCount[sensor]!![t] )
            }
        }
        val model = DifferentiableModelMV(countsFlat.first().nVars)
        model.setRootTerms(countsFlat)

        return model
    }
}

private fun getSimCountsFromDemand(
    nVars: Int,
    context: TrafficCountCalibrationContext,
    expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
    tripStartDistr: Map<ActivityType, DoubleArray>
) : Map<TrafficSensor, List<LinearTerm>>{
    // Simulated traffic counts
    val simCount = mutableMapOf<TrafficSensor, List<LinearTerm>>()
    for (sensor in context.sensors) {
        simCount[sensor] = List(T) { LinearTerm(nVars) }
    }
    for ((o, origin) in context.omosim.grid.withIndex()) {
        for ((d, destination) in context.omosim.grid.withIndex()) {
            val od = Pair(origin, destination)
            if (od in context.affectedSensors) {
                val affected = context.affectedSensors[od]!!
                for (sensor in affected) {
                    for (t in 0 until T) {
                        for (activity in ActivityType.entries) {
                            simCount[sensor]!![t].addTerm(
                                expectedTrips[activity]!![o][d],
                                coefficient = context.totalPopulation * tripStartDistr[activity]!![t]
                            )
                        }
                    }
                }
            }
        }
    }
    return simCount
}


class DFMatchSSE (
    val activity: ActivityType,
    val mean: Double,
    val m2: Double,
    val context: DistanceFunctionMatchContext
) : SGGravityObjectiveNative<DifferentiableModelUVBase> {
    override fun build (
        nVars: Int,
        expectedTrips: Map<ActivityType, List<List<LinearTerm>>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : DifferentiableModelUVBase {
        val omosim = context.omosim
        val n = context.omosim.grid.size

        val expectedTotalDistance = LinearTerm(nVars)
        val expectedTotalTrips = LinearTerm(nVars)
        val expectedODCount = mutableMapOf<Pair<Int, Int>, LinearTerm>()
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)

            for (d in 0 until n) {
                val expectedTripCount = LinearTerm(nVars)
                for (k in expectedTrips.keys) { // TODO
                    expectedTripCount.addTerm(
                        expectedTrips[k]!![o][d],
                        context.totalPopulation
                    )
                }
                expectedODCount[Pair(o,d)] = expectedTripCount

                expectedTotalDistance.addTerm(
                    expectedTripCount,
                    distances[d] / 1000.0
                )
                expectedTotalTrips.addTerm(
                    expectedTripCount,
                    1.0
                )
            }
        }
        val expectedMean = DivisionTerm(nVars, expectedTotalDistance, expectedTotalTrips)

        val expectedSumOfSquare = LinearTerm(nVars)
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)

            for (d in 0 until n) {
                val expectedTripCount = expectedODCount[Pair(o,d)]!!

                expectedSumOfSquare.addTerm(
                    expectedTripCount,
                    (distances[d] / 1000.0) * (distances[d] / 1000.0)
                )
            }
        }
        val moment2 = DivisionTerm(nVars, expectedSumOfSquare, expectedTotalTrips)

        // (m - s)^2 = m^2 - 2ms + s^2
        val obj = LinearTerm(nVars)
        obj.addConstant(mean * mean * 10)
        obj.addTerm(expectedMean, -2 * mean * 10)
        val qTermMean = QuadraticTerm(nVars, expectedMean, expectedMean,1.0)
        obj.addTerm(qTermMean, 1.0 * 10.0)

        val objMoment2 = LinearTerm(nVars)
        objMoment2.addConstant(m2 * m2)
        objMoment2.addTerm(moment2, -2 * m2)
        val qTermVar = QuadraticTerm(nVars, moment2, moment2,1.0)
        objMoment2.addTerm(qTermVar, 1.0)

        obj.addTerm(
            //objMoment2, 1.0
            PowerTerm(nVars, objMoment2, 0.5), 1.0
        )

        val x = DoubleArray(nVars) { 1.0 }
        println(expectedODCount[Pair(0,1)]!!.evaluate(x))
        println(expectedODCount[Pair(0,2)]!!.evaluate(x))
        println(expectedODCount[Pair(0,3)]!!.evaluate(x))
        println(expectedODCount[Pair(1,1)]!!.evaluate(x))
        println(expectedODCount[Pair(2,1)]!!.evaluate(x))
        println(expectedODCount[Pair(3,1)]!!.evaluate(x))

        val model = DifferentiableModelUVBase(nVars)
        model.setRootTerm(obj)

        return model
    }
}

class DFMatchSSETFTF (
    val activity: ActivityType,
    val mean: Double,
    val m2: Double,
    val model: TfModel,
    val context: DistanceFunctionMatchContext
) : SGGravityObjectiveTF {
    override fun build(
        nVars: Int,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ): TfModel {
        val tf = model.tf
        val omosim = context.omosim
        val n = context.omosim.grid.size

        val arrDistance = Array<DoubleArray>(n) { DoubleArray(n) }
        val arrDistanceSquared = Array<DoubleArray>(n) { DoubleArray(n) }
        for ((o, origin) in omosim.grid.withIndex()) {
            val distances = omosim.routingCache.getDistances(origin, omosim.grid)
            for (d in 0 until n) {
                arrDistance[o][d] = (distances[d] / 1000.0)
                arrDistanceSquared[o][d] = (distances[d] / 1000.0) * (distances[d] / 1000.0)
            }
        }
        val oDistance        = model.addMatrix( arrDistance )
        val oDistanceSquared = model.addMatrix( arrDistanceSquared )

        val totalPopulation = tf.constant(context.totalPopulation.toFloat())
        val expected = tf.math.addN(expectedTrips.values.map { it }) // TODO
        //val expectedTripsScaled = tf.math.mul(expectedTrips[activity]!!.matrixT, totalPopulation)
        val expectedTripsScaled = tf.math.mul(expected, totalPopulation)
        val expectedDistance = tf.math.mul(expectedTripsScaled, oDistance)
        val expectedDistanceSquared = tf.math.mul(expectedTripsScaled, oDistanceSquared)

        val expectedTotalTrips = tf.reduceSum(
            expectedTripsScaled,
            tf.constant(intArrayOf(0, 1))
        )

        val expectedTotalDistance = tf.reduceSum(
            expectedDistance,
            tf.constant(intArrayOf(0, 1))
        )

        val expectedSumOfSquare = tf.reduceSum(
            expectedDistanceSquared,
            tf.constant(intArrayOf(0, 1))
        )

        // TEST
        model.session = Session(model.graph, model.config)
        model.fillInputTensor(DoubleArray(nVars) {1.0})

        model.session.runner()
            .feed(model.x, model.inputTensor)
            .fetch(expectedTripsScaled)
            .run().use { result ->
                val output = result[0] as TFloat32
                println( output.getFloat(0,1) )
                println( output.getFloat(0,2) )
                println( output.getFloat(0,3) )
                println( output.getFloat(1,1) )
                println( output.getFloat(2,1) )
                println( output.getFloat(3,1) )
            }
        // TEST

        val expectedMean = tf.math.div(expectedTotalDistance, expectedTotalTrips)
        val moment2      = tf.math.div(expectedSumOfSquare, expectedTotalTrips)


        val diff1 = tf.math.sub(expectedMean, tf.constant( mean.toFloat() ))
        val t1 = tf.math.mul( tf.math.square( diff1 ), tf.constant(10f) )

        val diff2 = tf.math.sub(moment2, tf.constant( m2.toFloat() ))
        val t2 = tf.math.sqrt( tf.math.square( diff2 ) )

        val obj = tf.math.add(t1, t2)


        // (m - s)^2 = m^2 - 2ms + s^2
        /*val a1 = tf.constant((mean * mean * 10).toFloat())
        val b1 = tf.math.mul(expectedMean, tf.constant((-2 * mean * 10).toFloat()))
        val c1 = tf.math.mul(tf.math.mul(expectedMean, expectedMean), tf.constant(10f))
        val t1 = tf.math.addN(listOf(a1, b1, c1))

        val a2 = tf.constant((m2 * m2).toFloat())
        val b2 = tf.math.mul(moment2, tf.constant((-2 * m2).toFloat()))
        val c2 = tf.math.mul(moment2, moment2)
        val t2 = tf.math.pow(tf.math.addN(listOf(a2, b2, c2)), tf.constant(0.5f))

        val obj = tf.math.add(t1, t2)*/

        model.finalize(obj)
        return model
    }
}