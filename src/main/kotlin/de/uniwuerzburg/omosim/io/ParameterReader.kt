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
        val res = Omosim::class.java.classLoader.getResource(fn)

        return if (res != null) {
            fn
        } else {
           "parametrization/${config.dependencies.parent}/${resName}"
        }
    }

    private inline fun <reified T> getParameter(fnParam: String, customFile: File?) : T {
        val source: String
        val parameter: T = if (customFile != null) {
            source = customFile.toString()
            readJson(customFile)
        } else {
            source = getInternalCalibrationRes(fnParam)
            readJsonFromResource(source)
        }
        logger.debug("Using ${fnParam.split(".").first()} from: $source")
        return parameter
    }

    fun getTourModeUtilities() : Array<ModeUtility>  {
        return getParameter("tourModeUtilities.json", tourModeUtilityFile)
    }

    fun getTripModeUtilities() : Array<ModeUtility>  {
        return getParameter("tripModeUtilities.json", tripModeUtilityFile)
    }

    fun getTripModeUtilitiesCalibration() : Array<ModeUtility>  {
        return getParameter("tripModeUtilitiesCalibration.json", tripModeUtilityForCalibrationFile)
    }

    fun getPopulationDistribution() : List<PopStratum> {
        return getParameter("Population.json", populationFile)
    }

    fun getActivityGroups() : List<ActivityGroup>{
        return getParameter("ActivityGroups.json", activityGroupFile)
    }

    fun getLocationChoiceFuns() : MutableMap<ActivityType, LocationChoiceDCWeightFun> {
        return getParameter("LocChoiceWeightFuns.json", locationChoiceFile)
    }

    fun getCarOwnershipUtility() : CarOwnershipUtility {
        return getParameter("carOwnershipUtility.json", carOwnershipUtilityFile)
    }
}