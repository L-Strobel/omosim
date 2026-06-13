package de.uniwuerzburg.omosim.calibration.objective

import de.uniwuerzburg.omosim.calibration.CalibrationConstants
import de.uniwuerzburg.omosim.calibration.TrafficCountCalibrationContext
import de.uniwuerzburg.omosim.calibration.TrafficSensor
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelCore
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelMV
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.io.json.writeJson
import kotlinx.serialization.Serializable
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.ndarray.StdArrays
import org.tensorflow.types.TFloat32
import org.tensorflow.types.TInt64
import java.io.File

class TrafficCountSeparateTensorFlow(
    val context: TrafficCountCalibrationContext
) : SGGravityObjectiveTF<TfModelMV>  {
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
        /*
        // Eval model code
        // TODO Move into own function.
        // TODO Output should contain the grid cells
        println("Evaluating SM")
        val x0 = DoubleArray(core.nVars) { 1.0 }
        val testModel = TfModelMV(core, s)
        val evalSimCounts = testModel.evaluate(x0).toList()

        val n = context.omosim.grid.size
        val floatData = FloatArray(x0.size) { i -> x0[i].toFloat() }
        StdArrays.copyTo(floatData, core.inputTensorContainer.get().tensor)

        val evalExpectedTrips = mutableMapOf<ActivityType, List<List<Double>>>()
        for (activityType in ActivityType.entries) {
            val outArray = Array(n) { FloatArray(n) }
            val session = Session(core.graph, core.config)
            session.runner()
                .feed(core.x, core.inputTensorContainer.get().tensor)
                .fetch(expectedTrips[ActivityType.OTHER])
                .run().use { result ->
                    val eOutput = result[0] as TFloat32
                    StdArrays.copyFrom(eOutput, outArray)
                }
            val doubleArray = outArray.map {
                    row -> row.map {
                    entry -> entry.toDouble()
                }.toList()
            }.toList()
            evalExpectedTrips[activityType] = doubleArray
        }
        val evalSMOutput = EvalSMOutput(evalSimCounts, evalExpectedTrips)
        writeJson(evalSMOutput, File("debugOut/evalSMOutput.json"))
        */
        return TfModelMV(core, s)
    }
}

@Serializable
class EvalSMOutput(
    val simCounts: List<Double>,
    val expectedTrips: Map<ActivityType, List<List<Double>>>
)