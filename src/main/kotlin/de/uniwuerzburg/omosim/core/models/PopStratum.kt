package de.uniwuerzburg.omosim.core.models

import de.uniwuerzburg.omosim.io.logger
import de.uniwuerzburg.omosim.utils.createCumDist
import de.uniwuerzburg.omosim.utils.sampleCumDist
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Random

/**
 * Population stratum.
 * Models a homogenous group in the population, for example a generation.
 *
 * @param stratumShare Share of the stratum of the entire population <=1.0
 * @param carOwnership Percentage of car ownership in the adult part of the stratum
 * @param age Age distribution of the stratum
 * @param monthlyIncome Income distribution of the stratum
 * @param homogenousGroup Distribution of different homogenousGroup/Occupation levels in the stratum
 * @param mobilityGroup Distribution of different mobility groups in the stratum
 * @param sex Distribution of different genders in the stratum
 */
@Serializable
class PopStratum (
    @Suppress("unused") val stratumName: String,
    val stratumShare: Double,
    val carOwnership: Double,
    private val age: ContinuousFeatureDistribution,
    private val monthlyIncome: ContinuousFeatureDistribution,
    private val homogenousGroup: Map<HomogeneousGrp, Double>,
    private val mobilityGroup: Map<MobilityGrp, Double>,
    private val sex: Map<Sex, Double>,
) {
    @Transient
    private val homGroups = getGroupsFromMap(homogenousGroup)
    @Transient
    private val homDistr = getDistrFromMap(homogenousGroup)
    @Transient
    private val mobGroups = getGroupsFromMap(mobilityGroup)
    @Transient
    private val mobDistr =  getDistrFromMap(mobilityGroup)
    @Transient
    private val sexGroups = getGroupsFromMap(sex)
    @Transient
    private val sexDistr = getDistrFromMap(sex)

    private fun <T: Comparable<T>> getDistrFromMap(map: Map<T,Double>) : DoubleArray {
        return createCumDist(map.toList().sortedBy { it.first }.map { it.second }.toDoubleArray())
    }
    private fun <T: Comparable<T>> getGroupsFromMap(map: Map<T,Double>) : List<T> {
        return map.toList().sortedBy { it.first }.map { it.first }
    }

    fun sampleSocDemFeatures(rng: Random, populationWideValues: PopulationWideValues) : SocioDemFeatureSet {
        val hom = homGroups[sampleCumDist(homDistr, rng)]
        val mob = mobGroups[sampleCumDist(mobDistr, rng)]
        val sex = sexGroups[sampleCumDist(sexDistr, rng)]

        // Age
        val age = if (rng.nextDouble() <= age.UNDEFINED) {
            null
        } else {
            age.sample(rng)
        }

        // Sample income
        val monthlyIncome = if (age != null && age < populationWideValues.minIncomeAge) {
            null // skips an agent that is under defined minimum age when assigning income
        } else if (rng.nextDouble() <= monthlyIncome.UNDEFINED) {
            null
        }else {
            monthlyIncome.sample(rng)
        }

        return SocioDemFeatureSet(hom, mob, age, monthlyIncome, sex)
    }

    fun iterateOptions() = iterator {
        val ageMeans = age.binMeansWithP()
        val incomeMeans = monthlyIncome.binMeansWithP()

        for ((hom, pHom) in homogenousGroup) {
            for ((mob, pMob) in mobilityGroup) {
                for ((s, pSex) in sex) {
                    for ((a, pAge) in ageMeans) {
                        for ((i, pIncome) in incomeMeans) {
                            yield(
                                Pair(
                                    SocioDemFeatureSet(hom, mob, a, i, s),
                                    pHom * pMob * pSex * pAge * pIncome
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Distribution of a continuous demographics of a group.
     * The limits and shares define a histogram.
     *
     * Example age:
     * shares[0] defines the share of agents in the age range 0 <= age < limits[0]
     * shares[1] defines the share of agents in the age range  limits[0] <= age < limits[1]
     * ...
     *
     * The distribution inside a bin is considered to be uniform.
     *
     * @param limits Bin limits
     * @param shares Bin sizes
     */
    @Serializable
    class ContinuousFeatureDistribution  (
        val limits: List<Int>,
        val shares: List<Double>,
        @Suppress("PropertyName") val UNDEFINED: Double
    ) {
        @Transient
        private val cDist = getCumDistr()
        @Transient
        private val groups = getGroups()

        init {
            checkErrors()
        }

        fun sample(rng: Random) : Int {
            val i = sampleCumDist(cDist, rng)
            val ub = groups[i]
            val lb = if(i == 0) {
                0
            } else {
                groups[i-1]
            }
            return rng.nextInt(lb, ub)
        }

        fun binMeansWithP() : List<Pair<Int?, Double>> {
            val zipped = limits.zip(shares)
            val sorted = zipped.sortedBy { it.first }

            var lowerBound = 0
            val means = sorted.map { (upperBound, share) ->
                val value = (upperBound - lowerBound) / 2
                lowerBound = upperBound
                value to share
            }

            return listOf(null to UNDEFINED) + means
        }

        private fun getCumDistr() : DoubleArray {
            val zipped = limits.zip(shares)
            val cumDistr = createCumDist(zipped.sortedBy { it.first }.map { it.second }.toDoubleArray())
            return cumDistr
        }

        private fun getGroups() : List<Int> {
            val zipped = limits.zip(shares)
            val groups = zipped.sortedBy { it.first }.map { it.first }
            return groups
        }

        private fun checkErrors() {
            if (limits.isEmpty()) {
                if (UNDEFINED != 1.0) {
                    val msg = "population.json falsely specified! " +
                              "No limits supplied for continuous distribution and share of UNDEFINED group IS NOT 100%."
                    logger.error(msg)
                    throw IllegalArgumentException(msg)
                }
            } else if (limits.size != shares.size) {
                val msg = "population.json falsely specified! " +
                          "Continuous distribution does not have the same number of limits and shares."
                logger.error(msg)
                throw IllegalArgumentException(msg)
            } else if (limits[0] < 0 ) {
                val msg = "population.json falsely specified! Continuous distribution limits can't be negative!"
                logger.error(msg)
                throw IllegalArgumentException(msg)
            } else if (limits[0] == 0) {
                val msg = "population.json falsely specified! " +
                          "The first limit of a continuous distribution can't be zero. " +
                          "The values of 'limits' represent the exclusive upper bounds each group."
                logger.error(msg)
                throw IllegalArgumentException(msg)
            }
        }

    }
}
