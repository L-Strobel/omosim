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
 * @param income Income distribution of the stratum
 * @param homogenousGroup Distribution of different homogenousGroup/Occupation levels in the stratum
 * @param mobilityGroup Distribution of different mobility groups in the stratum
 * @param sex Distribution of different genders in the stratum
 */
@Serializable
class PopStratum (
    @Suppress("unused") val stratumName: String,
    val stratumShare: Double,
    val carOwnership: Double,
    private val age: AgeDistribution,
    private val income: IncomeDistribution,
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
    @Transient
    private val ageDistr = getAgeDistr(age)
    @Transient
    private val ageGroups = getAgeGroups(age)
    @Transient
    private val incomeDistr = getIncomeDistr(income)
    @Transient
    private val incomeGroups = getIncomeGroups(income)

    private fun <T: Comparable<T>> getDistrFromMap(map: Map<T,Double>) : DoubleArray {
        return createCumDist(map.toList().sortedBy { it.first }.map { it.second }.toDoubleArray())
    }
    private fun <T: Comparable<T>> getGroupsFromMap(map: Map<T,Double>) : List<T> {
        return map.toList().sortedBy { it.first }.map { it.first }
    }

    private fun checkAgeDistrErrors(age: AgeDistribution) {
        if (age.limits.isEmpty()) {
            if (age.UNDEFINED != 1.0) {
                val msg = "population.json falsely specified! No limits supplied and share of UNDEFINED group IS NOT 100%."
                logger.error(msg)
                throw IllegalArgumentException(msg)
            }
        } else if (age.limits.size != age.shares.size) {
            val msg = "population.json falsely specified! There must be the same number of limits and shares."
            logger.error(msg)
            throw IllegalArgumentException(msg)
        } else if (age.limits[0] < 0 ) {
            val msg = "population.json falsely specified! Age limits can't be negative!"
            logger.error(msg)
            throw IllegalArgumentException(msg)
        } else if (age.limits[0] == 0) {
            val msg = "population.json falsely specified! The first age limit can't be zero." +
                      "The values of 'limits' represent the exclusive upper bounds each age group."
            logger.error(msg)
            throw IllegalArgumentException(msg)
        }
    }

    private fun checkIncomeDistrErrors(income: IncomeDistribution) {
        if (income.limits.isEmpty()) {
            if (income.UNDEFINED != 1.0) {
                val msg = "population.json falsely specified! No limits supplied and share of UNDEFINED group IS NOT 100%."
                logger.error(msg)
                throw IllegalArgumentException(msg)
            }
        } else if (income.limits.size != income.shares.size) {
            val msg = "population.json falsely specified! There must be the same number of limits and shares."
            logger.error(msg)
            throw IllegalArgumentException(msg)
        } else if (income.limits[0] < 0 ) {
            val msg = "population.json falsely specified! Income limits can't be negative!"
            logger.error(msg)
            throw IllegalArgumentException(msg)
        } else if (income.limits[0] == 0) {
            val msg = "population.json falsely specified! The first income limit can't be zero." +
                      "The values of 'limits' represent the exclusive bounds each income group."
            logger.error(msg)
            throw IllegalArgumentException(msg)
        }
    }

    private fun getIncomeDistr(income: IncomeDistribution) : DoubleArray {
        checkIncomeDistrErrors(income)
        val zipped = income.limits.zip(income.shares)
        val distr = createCumDist(zipped.sortedBy { it.first }.map { it.second }.toDoubleArray())
        return distr
    }

    private fun getIncomeGroups(income: IncomeDistribution) : List<Int> {
        checkIncomeDistrErrors(income)
        val zipped = income.limits.zip(income.shares)
        val groups = zipped.sortedBy { it.first }.map { it.first }
        return groups
    }

    // Age is a special case because it is a continuous variable
    private fun getAgeDistr(age: AgeDistribution) : DoubleArray {
        checkAgeDistrErrors(age)
        val zipped = age.limits.zip(age.shares)
        val distr = createCumDist(zipped.sortedBy { it.first }.map { it.second }.toDoubleArray())
        return distr
    }

    private fun getAgeGroups(age: AgeDistribution) : List<Int> {
        checkAgeDistrErrors(age)
        val zipped = age.limits.zip(age.shares)
        val groups = zipped.sortedBy { it.first }.map { it.first }
        return groups
    }

    fun sampleSocDemFeatures(rng: Random) : SocioDemFeatureSet {
        val hom = homGroups[sampleCumDist(homDistr, rng)]
        val mob = mobGroups[sampleCumDist(mobDistr, rng)]
        val sex = sexGroups[sampleCumDist(sexDistr, rng)]

        // Age
        val age = if (rng.nextDouble() <= age.UNDEFINED) {
            null
        } else {
            val i = sampleCumDist(ageDistr, rng)
            val ub = ageGroups[i]
            val lb = if (i == 0) {
                0
            } else {
                ageGroups[i-1]
            }
            rng.nextInt(lb, ub)
        }
        // Sample income between groups
        val incomeVal = if (rng.nextDouble() <= income.UNDEFINED) {
            null
        } else {
            val i = sampleCumDist(incomeDistr, rng)
            val ub = incomeGroups[i]
            val lb = if(i == 0) {
                0
            } else {
                incomeGroups[i-1]
            }
            rng.nextInt(lb, ub)
        }

        return SocioDemFeatureSet(hom, mob, age, incomeVal, sex)
    }

    fun iterateOptions() = iterator {
        val aZipped = age.limits.zip(age.shares)
        val ageGroup = aZipped.sortedBy { it.first }
        val incomeGroup = income.limits.zip(income.shares).sortedBy { it.first }

        for ((hom, pHom) in homogenousGroup) {
            for ((mob, pMob) in mobilityGroup) {
                for ((s, pSex) in sex) {
                    // Undefined age and income
                    val uSet = SocioDemFeatureSet(hom, mob, null, income=null, s)
                    val uP = pHom * pMob * pSex * age.UNDEFINED * income.UNDEFINED
                    yield(Pair(uSet, uP))

                    // Defined age, undefined income
                    var ageLb = 0
                    for((ageUb, pAge) in ageGroup) {
                        val a = (ageUb - ageLb) / 2
                        ageLb = ageUb
                        val set = SocioDemFeatureSet(hom, mob, a, income=null, s)
                        val p = pHom * pMob * pSex * pAge * income.UNDEFINED
                        yield(Pair(set, p))
                    }
                    // Defined income, undefined age
                    var incomeLb = 0
                    for ((incomeUb, pIncome) in incomeGroup) {
                        val i = (incomeUb - incomeLb) / 2
                        incomeLb = incomeUb
                        val set = SocioDemFeatureSet(hom, mob, null, i, s)
                        val p = pHom * pMob * pSex * age.UNDEFINED * pIncome
                        yield(Pair(set, p))
                    }
                    // both defined
                    ageLb = 0
                    for ((ageUb, pAge) in ageGroup) {
                        val a = (ageUb - ageLb) / 2
                        ageLb = ageUb
                        incomeLb = 0
                        for ((incomeUb, pIncome) in incomeGroup) {
                            val i = (incomeUb - incomeLb) / 2
                            incomeLb = incomeUb
                            yield(Pair(
                                SocioDemFeatureSet(hom, mob, a, i, s),
                                pHom * pMob * pSex * pAge * pIncome
                            ))
                        }
                    }
                }
            }
        }
    }

    /**
     * Income distribution of group.
     * The limits and shares define a histogram like with the income distribution
     * shares[0] defines the share of agents in the income group 0 <= income < limits[0]
     *
     * Within each bin there is a uniform distributino
     */
    @Serializable
    class IncomeDistribution(
        val limits: List<Int>,
        val shares: List<Double>,
        @Suppress("PropertyName") val UNDEFINED: Double,
    )

    /**
     * Demographics of group.
     * The limits and shares define a histogram.
     * shares[0] defines the share of agents in the age range 0 <= age < limits[0]
     * shares[1] defines the share of agents in the age range  limits[0] <= age < limits[1]
     * ...
     *
     * The age distribution inside a bin is considered to be uniform.
     *
     * @param limits Bin limits
     * @param shares Bin sizes
     */
    @Serializable
    class AgeDistribution (
        val limits: List<Int>,
        val shares: List<Double>,
        @Suppress("PropertyName") val UNDEFINED: Double
    )
}
