package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelMV
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.io.json.writeJson
import kotlinx.serialization.Serializable
import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTWriter
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.ndarray.StdArrays
import org.tensorflow.types.TFloat32
import java.io.File

class SMEvaluatorTF(
    val context: TrafficCountCalibrationContext,
    val outputFile: File
) : SMGravityObjectiveTF<TfModelMV>  {
    override fun build (
        core: TfModelCore,
        expectedTrips: Map<ActivityType, Operand<TFloat32>>,
        tripStartDistr: Map<ActivityType, DoubleArray>
    ) : TfModelMV {
        val simCount = getSimCountsFromDemandTF(context, core, expectedTrips, tripStartDistr)

        val s = mutableListOf<Operand<TFloat32>>()
        for (sensor in context.sensors) {
            for (t in 0 until CalibrationConstants.T) {
                s.add(simCount[sensor]!![t])
            }
        }

        // Evaluate simulated traffic counts
        val x0 = DoubleArray(core.nVars) { 1.0 }
        val testModel = TfModelMV(core, s)
        val evalSimCounts = testModel.evaluate(x0).toList()

        // Simulated count labels
        val sensorNames = mutableListOf<String>()
        val timeSteps = mutableListOf<Int>()
        for (sensor in context.sensors) {
            for (t in 0 until CalibrationConstants.T) {
                sensorNames.add(sensor.name)
                timeSteps.add(t)
            }
        }

        // Evaluate expected origin destination matrix
        val n = context.omosim.grid.size
        val floatData = FloatArray(x0.size) { i -> x0[i].toFloat() }
        StdArrays.copyTo(floatData, core.inputTensorContainer.get().tensor)
        val evalExpectedTrips = mutableMapOf<ActivityType, List<List<Float>>>()
        for (activityType in ActivityType.entries) {
            val outArray = Array(n) { FloatArray(n) }
            val session = Session(core.graph, core.config)
            session.runner()
                .feed(core.x, core.inputTensorContainer.get().tensor)
                .fetch(expectedTrips[activityType])
                .run().use { result ->
                    val eOutput = result[0] as TFloat32
                    StdArrays.copyFrom(eOutput, outArray)
                }
            evalExpectedTrips[activityType] = outArray.map { it.toList() }.toList()
        }

        // Grid cells
        val wktWriter = WKTWriter()
        val geometryFactory = GeometryFactory()
        val hulls = mutableListOf<String>()
        for (cell in context.omosim.grid) {
            val points = cell.buildings.map { it.coord }.toTypedArray()
            val hull = ConvexHull(points, geometryFactory).convexHull
            val latlonHull = context.omosim.transformer.toLatLon(hull)
            val hullString = wktWriter.write(latlonHull)
            hulls.add(hullString)
        }

        // Save output
        val evalSMOutput = SMEvaluateOutput(
            evalSimCounts,
            sensorNames,
            timeSteps,
            evalExpectedTrips,
            hulls
        )
        writeJson(evalSMOutput, outputFile)

        return TfModelMV(core, s)
    }
}

@Serializable
class SMEvaluateOutput(
    val simCounts: List<Double>,
    val simCountsSensorNames: List<String>,
    val simCountsTimeSteps: List<Int>,
    val expectedTrips: Map<ActivityType, List<List<Float>>>,
    val gridCellHulls: List<String>
)