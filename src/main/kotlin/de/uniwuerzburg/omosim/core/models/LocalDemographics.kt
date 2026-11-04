package de.uniwuerzburg.omosim.core.models

import de.uniwuerzburg.omosim.core.logger
import de.uniwuerzburg.omosim.utils.createCumDist
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class LocalDemographics(
    val strataNames: List<String>,
    val localStrataShares: List<Double>
) {
    @Transient
    lateinit var strata: List<PopStratum>

    val strataDistribution by lazy(LazyThreadSafetyMode.PUBLICATION) {
        createCumDist(localStrataShares.toDoubleArray())
    }

    /**
     * Prepare demographics for sampling.
     */
    fun prepare(populationConfig: PopulationConfig) {
        if (!::strata.isInitialized) {
            val largestStratum = populationConfig.strata.maxBy { it.stratumShare }
            val strataList = mutableListOf<PopStratum>()
            for (stratumName in strataNames) {
                var found = false
                for (stratum in populationConfig.strata) {
                    if (stratumName == stratum.stratumName) {
                        strataList.add(stratum)
                        found = true
                        break
                    }
                }
                if (!found) {
                    strataList.add(largestStratum)
                    logger.get()?.warn(
                        "Stratum $stratumName is unknown." +
                                "Has been replaced with largest known stratum ${largestStratum.stratumName}"
                    )
                }
            }
            strata = strataList
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocalDemographics

        if (strataNames != other.strataNames) return false
        if (localStrataShares != other.localStrataShares) return false

        return true
    }

    override fun hashCode(): Int {
        var result = strataNames.hashCode()
        result = 31 * result + localStrataShares.hashCode()
        return result
    }
}