package de.uniwuerzburg.omosim.io

import com.akuleshov7.ktoml.Toml
import de.uniwuerzburg.omosim.core.*
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.PopStratum
import de.uniwuerzburg.omosim.io.json.ActivityGroup
import de.uniwuerzburg.omosim.io.json.readJson
import de.uniwuerzburg.omosim.io.json.readJsonFromResource
import kotlinx.serialization.decodeFromString
import java.io.File

class ParameterReader(
    val calName: String,
    var tourModeUtilityFile: File?,
    var tripModeUtilityFile: File?,
    var tripModeUtilityForCalibrationFile: File?,
    var populationFile: File?,
    var activityGroupFile: File?,
    var locationChoiceFile: File?,
    var carOwnershipUtilityFile: File?,
) {
    val config: ParameterConfig

    init {
        val fnConfig = "parametrization/${calName}/config.toml"
        val toml = Omosim::class.java.classLoader.getResource(fnConfig)!!.readText(Charsets.UTF_8)
        config = Toml.decodeFromString<ParameterConfig>(toml)
    }

    private fun getInternalCalibrationRes(resName: String): String {
        val fn = "parametrization/${calName}/${resName}"
        val file = File(fn)

        return if (file.exists()) {
            fn
        } else {
           "parametrization/${config.dependencies.parent}/${resName}"
        }
    }

    fun getTourModeUtilities() : Array<ModeUtility>  {
        return if (tourModeUtilityFile != null) {
            readJson(tourModeUtilityFile!!)
        } else {
            val res = getInternalCalibrationRes("tourModeUtilities.json")
            readJsonFromResource(res)
        }
    }

    fun getTripModeUtilities() : Array<ModeUtility>  {
        return if (tripModeUtilityFile != null) {
            readJson(tripModeUtilityFile!!)
        } else {
            val res = getInternalCalibrationRes("tripModeUtilities.json")
            readJsonFromResource(res)
        }
    }

    fun getTripModeUtilitiesCalibration() : Array<ModeUtility>  {
        return if (tripModeUtilityForCalibrationFile != null) {
            readJson(tripModeUtilityForCalibrationFile!!)
        } else {
            val res = getInternalCalibrationRes("tripModeUtilitiesCalibration.json")
            readJsonFromResource(res)
        }
    }

    fun getPopulationDistribution() : List<PopStratum> {
        return if (populationFile != null) {
            readJson(populationFile!!)
        } else {
            val res = getInternalCalibrationRes("Population.json")
            readJsonFromResource(res)
        }
    }

    fun getActivityGroups() : List<ActivityGroup>{
        return if (activityGroupFile != null) {
            readJson(activityGroupFile!!)
        } else {
            val res = getInternalCalibrationRes("ActivityGroups.json")
            readJsonFromResource(res)
        }
    }

    fun getLocationChoiceFuns() : MutableMap<ActivityType, LocationChoiceDCWeightFun> {
        return if (locationChoiceFile != null) {
            readJson(locationChoiceFile!!)
        } else {
            val res = getInternalCalibrationRes("LocChoiceWeightFuns.json")
            readJsonFromResource(res)
        }
    }

    fun getCarOwnershipUtility() : CarOwnershipUtility {
        return if (carOwnershipUtilityFile != null) {
            readJson(carOwnershipUtilityFile!!)
        } else {
            val res = getInternalCalibrationRes("carOwnershipUtility.json")
            readJsonFromResource(res)
        }
    }
}