package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.core.LocationChoiceDCWeightFun
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.Building
import de.uniwuerzburg.omosim.core.models.Cell
import de.uniwuerzburg.omosim.io.json.readJson
import de.uniwuerzburg.omosim.io.json.writeJson
import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * JSON format for the calibration result of the gravity model.
 *
 * @param osmID OSM-ID of a building
 * @param scalers Scaling factors for each activity type that has been calibrated
 */
@Serializable
class GravityCalibrationStore (
    val osmID: Long,
    val scalers: Map<ActivityType, Double>
) {
    companion object {
        /**
         * Write calibration to JSON
         *
         * @param file File to write to
         * @param buildings Buildings for which the calibration is to be saved. Most of the time it's all buildings.
         * @param dcFunctions Destination choice functions for which the attraction is to be saved.
         */
        fun write(
            file: Path,
            buildings: List<Building>,
            dcFunctions: Map<ActivityType,LocationChoiceDCWeightFun>
        ) {
            val store = mutableListOf<GravityCalibrationStore>()
            for (building in buildings) {
                // Read scalers from buildings
                val scalers = mutableMapOf<ActivityType, Double>()
                for ((activityType, dcFunction) in dcFunctions.entries) {
                    val scaler = building.attractionScaler[dcFunction.id]
                    if (scaler != null) {
                        scalers[activityType] = scaler
                    }
                }
                // Add to store
                val info = GravityCalibrationStore(
                    building.osmID,
                    scalers
                )
                store.add(info)
            }
            writeJson(store, file)
        }

        /**
         * Read calibration from JSON and apply it to the given cells and buildings.
         *
         * @param file File to read from
         * @param cells OMoSim grid
         * @param buildings OMoSim buildings
         * @param dcFunctions Destination choice functions to which the calibration is applied.
         */
        fun read(
            file: Path,
            cells: List<Cell>,
            buildings: List<Building>,
            dcFunctions: Map<ActivityType, LocationChoiceDCWeightFun>
        ) {
            // Read data
            val store = readJson<List<GravityCalibrationStore>>(file)
                .associate { it.osmID to it.scalers }

            // Apply to buildings
            for (building in buildings) {
                for ((activityType, dcFunction) in dcFunctions.entries) {
                    val calibrationValue = store[building.osmID]?.get(activityType)
                    if (calibrationValue != null) {
                        building.attractionScaler[dcFunction.id] = calibrationValue
                    }
                }
            }

            // Recalculate attractions on the aggregate level
            for (cell in cells) {
                cell.recalculateAttractions(dcFunctions.values.toList())
            }
        }
    }
}
