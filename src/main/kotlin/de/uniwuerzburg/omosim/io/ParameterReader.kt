package de.uniwuerzburg.omosim.io

import com.akuleshov7.ktoml.Toml
import de.uniwuerzburg.omosim.core.CarOwnershipUtility
import de.uniwuerzburg.omosim.core.LocationChoiceDCWeightFun
import de.uniwuerzburg.omosim.core.ModeUtility
import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.ActivityType
import de.uniwuerzburg.omosim.core.models.PopStratum
import de.uniwuerzburg.omosim.io.json.ActivityGroup
import de.uniwuerzburg.omosim.io.json.readJson
import de.uniwuerzburg.omosim.io.json.readJsonFromResource
import kotlinx.serialization.decodeFromString
import java.io.FileNotFoundException
import java.nio.file.Path

class ParameterReader(
    val calName: String,
    var tourModeUtilityFile: Path?,
    var tripModeUtilityFile: Path?,
    var tripModeUtilityForCalibrationFile: Path?,
    var populationFile: Path?,
    var activityGroupFile: Path?,
    var locationChoiceFile: Path?,
    var carOwnershipUtilityFile: Path?,
) {
    private val config: ParameterConfig = loadConfig(calName)

    private fun loadConfig(name: String): ParameterConfig {
        val fnConfig = "parametrization/${name}/config.toml"
        val toml = Omosim::class.java.classLoader.getResource(fnConfig)!!.readText(Charsets.UTF_8)
        return Toml.decodeFromString<ParameterConfig>(toml)
    }

    private fun getCalibrationResource(resName: String, searchFolder: String = calName): String {
        val fn = "parametrization/${searchFolder}/${resName}"
        val res = Omosim::class.java.classLoader.getResource(fn)

        return if (res != null) {
            fn
        } else {
            val folderConfig = if(searchFolder == calName) {
                config
            } else {
                loadConfig(searchFolder)
            }

            val parent = folderConfig.dependencies.parent

            // Current folder is the root folder and file was not found
            if (parent == "") {
                throw FileNotFoundException("Parametrization file $resName not found!")
            }

            getCalibrationResource(resName, parent)
        }
    }

    private inline fun <reified T> getParameterJson(fnParam: String, customFile: Path?) : T {
        val source: String
        val parameter: T = if (customFile != null) {
            source = customFile.toString()
            readJson(customFile)
        } else {
            source = getCalibrationResource(fnParam)
            readJsonFromResource(source)
        }
        logger.debug("Using ${fnParam.split(".").first()} from: $source")
        return parameter
    }

    fun getTourModeUtilities() : Array<ModeUtility>  {
        return getParameterJson("tourModeUtilities.json", tourModeUtilityFile)
    }

    fun getTripModeUtilities() : Array<ModeUtility>  {
        return getParameterJson("tripModeUtilities.json", tripModeUtilityFile)
    }

    fun getTripModeUtilitiesCalibration() : Array<ModeUtility>  {
        return getParameterJson("tripModeUtilitiesCalibration.json", tripModeUtilityForCalibrationFile)
    }

    fun getPopulationDistribution() : List<PopStratum> {
        return getParameterJson("Population.json", populationFile)
    }

    fun getActivityGroups() : List<ActivityGroup>{
        return getParameterJson("ActivityGroups.json", activityGroupFile)
    }

    fun getLocationChoiceFuns() : MutableMap<ActivityType, LocationChoiceDCWeightFun> {
        return getParameterJson("LocChoiceWeightFuns.json", locationChoiceFile)
    }

    fun getCarOwnershipUtility() : CarOwnershipUtility {
        return getParameterJson("carOwnershipUtility.json", carOwnershipUtilityFile)
    }
}