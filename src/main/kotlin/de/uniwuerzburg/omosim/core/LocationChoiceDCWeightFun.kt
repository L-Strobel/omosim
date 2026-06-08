package de.uniwuerzburg.omosim.core

import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.Term
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.Variable
import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModel
import de.uniwuerzburg.omosim.core.models.Landuse
import de.uniwuerzburg.omosim.core.models.RealLocation
import de.uniwuerzburg.omosim.io.geojson.property.BuildingProperties
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Return unique IDs used for the destination choice functions.
 * These IDs are used to cache a location's attraction value, as calculated with the function,
 * inside the location itself.
 */
object IDDispenser {
    var nextID = 0

    fun next() : Int {
        val id = nextID
        nextID += 1
        return id
    }
}

/**
 * Destination choice model. Parent class.
 */
@Serializable
sealed class LocationChoiceDCWeightFun {
    abstract val coeffResidentialArea: Double
    abstract val coeffCommercialArea: Double
    abstract val coeffRetailArea: Double
    abstract val coeffIndustrialArea: Double
    abstract val coeffOfficeArea: Double
    abstract val coeffShopArea: Double
    abstract val coeffSchoolArea: Double
    abstract val coeffUniversityArea: Double
    abstract val coeffOtherArea: Double
    abstract val coeffOfficeUnits: Double
    abstract val coeffShopUnits: Double
    abstract val coeffSchoolUnits: Double
    abstract val coeffUniUnits: Double
    abstract val coeffPlaceOfWorshipUnits: Double
    abstract val coeffCafeUnits: Double
    abstract val coeffFastFoodUnits: Double
    abstract val coeffKinderGartenUnits: Double
    abstract val coeffTourismUnits: Double
    abstract val coeffBuildingUnits: Double
    abstract val coeffResidentialUnits: Double
    abstract val coeffCommercialUnits: Double
    abstract val coeffRetailUnits: Double
    abstract val coeffIndustrialUnits: Double
    abstract val limitOfficeUnits: Double?
    abstract val limitShopUnits: Double?
    abstract val limitSchoolUnits: Double?
    abstract val limitUniUnits: Double?
    abstract val limitPlaceOfWorshipUnits: Double?
    abstract val limitCafeUnits: Double?
    abstract val limitFastFoodUnits: Double?
    abstract val limitKinderGartenUnits: Double?
    abstract val limitTourismUnits: Double?
    abstract val limitBuildingUnits: Double?
    abstract val limitResidentialUnits: Double?
    abstract val limitCommercialUnits: Double?
    abstract val limitRetailUnits: Double?
    abstract val limitIndustrialUnits: Double?
    abstract val useLevels: Boolean

    @Transient
    val id: Int = IDDispenser.next()

    /**
     * Calculates the natural logarithm of the deterrence function given the distance from the origin.
     *
     * @param distance The distance between origin and destination
     * @return ln(f(d))
     */
    abstract fun deterrenceFunction(distance: Double) : Double

    open fun deterrenceFunctionAsTerm(distance: Double) : Pair<Term, Int> {
        throw NotImplementedError()
    }

    open fun applyDeterrenceToTensor(distances: Array<FloatArray>, model: TfModel): Operand<TFloat32> {
        throw NotImplementedError()
    }

    open fun getDistanceParameters() : DoubleArray {
        throw NotImplementedError()
    }

    open fun setDistanceParameters(parameters: DoubleArray) {
        throw NotImplementedError()
    }

    fun lnDistances(distances: Array<FloatArray>): Array<FloatArray> {
        return Array(distances.size) { i ->
            FloatArray(distances[0].size) { j ->
                ln(distances[i][j])
            }
        }
    }

    fun lnDistancesSquared(distances: Array<FloatArray>): Array<FloatArray> {
        return Array(distances.size) { i ->
            FloatArray(distances[0].size) { j ->
                ln(distances[i][j]) * ln(distances[i][j])
            }
        }
    }

    fun lnDistancesCubed(distances: Array<FloatArray>): Array<FloatArray> {
        return Array(distances.size) { i ->
            FloatArray(distances[0].size) { j ->
                ln(distances[i][j]) * ln(distances[i][j]) * ln(distances[i][j])
            }
        }
    }

    fun distancesPow(distances: Array<FloatArray>, pow: Int): Array<FloatArray> {
        return Array(distances.size) { i ->
            FloatArray(distances[0].size) { j ->
                distances[i][j].pow(pow)
            }
        }
    }

    /**
     * Calculates the probabilistic weight of a destination given the distance from the origin.
     *
     * @param destination The destination the weight will be calculated for
     * @param distance The distance between origin and destination
     * @return probabilistic weight
     */
    open fun calcFor(destination: RealLocation, distance: Double) : Double {
        // Minimum distance where at which distance has an influence. The left side of the deterrence functions
        // are poorly fitted due to the maximum resolution in the MID being 500m.
        val distanceAdj = if (distance <= 100.0) {
            0.1 // 100 Meters
        } else {
            distance / 1000
        }
        val fd = deterrenceFunction(distanceAdj)
        val attraction = calcForNoOrigin(destination)
        return attraction * exp(fd)
    }

    /**
     * Calculates the probabilistic weight of a destination without knowledge of the origin.
     * Used for the distribution of HOME locations and for destination choice within a routing cell,
     * where the distance difference between the buildings is neglected.
     *
     * @param destination The destination the weight will be calculated for
     * @return probabilistic weight
     */
    fun calcForNoOrigin(destination: RealLocation) : Double {
        return destination.attractions[id]!!
    }

    open fun calcAttraction(properties: BuildingProperties) : Double {
        val area = if(useLevels) {
            properties.area * properties.levels
        } else {
            properties.area
        }
        val areaOffice = if (properties.number_offices > 0) area else 0.0
        val areaShop = if (properties.number_shops > 0) area else 0.0
        val areaSchool = if (properties.number_schools > 0) area else 0.0
        val areaUniversity = if (properties.number_universities > 0) area else 0.0

        val attractionLanduse = when( properties.landuse) {
            Landuse.RESIDENTIAL -> {
                coeffResidentialArea * area + coeffResidentialUnits * 1.0
            }
            Landuse.COMMERCIAL -> {
                coeffCommercialArea * area + coeffCommercialUnits * 1.0
            }
            Landuse.RETAIL -> {
                coeffRetailArea * area + coeffRetailUnits * 1.0
            }
            Landuse.INDUSTRIAL -> {
                coeffIndustrialArea * area + coeffIndustrialUnits * 1.0
            }
            else -> {
                coeffOtherArea * area
            }
        }

        // Clip number of POI per building
        val nOffices = if (limitOfficeUnits != null) {
            max(limitOfficeUnits!!, properties.number_offices)
        } else {
            properties.number_offices
        }
        val nShops = if (limitShopUnits != null) {
            max(limitShopUnits!!, properties.number_shops)
        } else {
            properties.number_shops
        }
        val nSchools = if (limitSchoolUnits != null) {
            max(limitSchoolUnits!!, properties.number_schools)
        } else {
            properties.number_schools
        }
        val nUnis = if (limitUniUnits != null) {
            max(limitUniUnits!!, properties.number_universities)
        } else {
            properties.number_universities
        }
        val nPOW = if (limitPlaceOfWorshipUnits != null) {
            max(limitPlaceOfWorshipUnits!!, properties.number_place_of_worship)
        } else {
            properties.number_place_of_worship
        }
        val nCafe = if (limitCafeUnits != null) {
            max(limitCafeUnits!!, properties.number_cafe)
        } else {
            properties.number_cafe
        }
        val nFastFood = if (limitFastFoodUnits != null) {
            max(limitFastFoodUnits!!, properties.number_fast_food)
        } else {
            properties.number_fast_food
        }
        val nKinderGarten = if (limitKinderGartenUnits != null) {
            max(limitKinderGartenUnits!!, properties.number_kindergarten)
        } else {
            properties.number_kindergarten
        }
        val nTourism = if (limitTourismUnits != null) {
            max(limitTourismUnits!!, properties.number_tourism)
        } else {
            properties.number_tourism
        }

        return  1.0 +
                attractionLanduse +
                coeffOfficeArea * areaOffice +
                coeffShopArea * areaShop +
                coeffSchoolArea * areaSchool +
                coeffUniversityArea * areaUniversity +
                coeffOfficeUnits * nOffices +
                coeffShopUnits * nShops +
                coeffSchoolUnits * nSchools +
                coeffUniUnits * nUnis +
                coeffPlaceOfWorshipUnits * nPOW +
                coeffCafeUnits * nCafe +
                coeffFastFoodUnits * nFastFood +
                coeffKinderGartenUnits * nKinderGarten +
                coeffTourismUnits * nTourism +
                coeffBuildingUnits * 1
    }
}

/**
 * Destination choice function implementation for the HOME location distribution when census data is given.
 */
object ByPopulation: LocationChoiceDCWeightFun () {
    override val coeffResidentialArea: Double get() { throw NotImplementedError() }
    override val coeffCommercialArea: Double get() { throw NotImplementedError() }
    override val coeffRetailArea: Double get() { throw NotImplementedError() }
    override val coeffIndustrialArea: Double get() { throw NotImplementedError() }
    override val coeffOfficeArea: Double get() { throw NotImplementedError() }
    override val coeffShopArea: Double get() { throw NotImplementedError() }
    override val coeffSchoolArea: Double get() { throw NotImplementedError() }
    override val coeffUniversityArea: Double get() { throw NotImplementedError() }
    override val coeffOtherArea: Double get() { throw NotImplementedError() }
    override val coeffOfficeUnits: Double get() { throw NotImplementedError() }
    override val coeffShopUnits: Double get() { throw NotImplementedError() }
    override val coeffSchoolUnits: Double get() { throw NotImplementedError() }
    override val coeffUniUnits: Double get() { throw NotImplementedError() }
    override val coeffPlaceOfWorshipUnits: Double get() { throw NotImplementedError() }
    override val coeffCafeUnits: Double get() { throw NotImplementedError() }
    override val coeffFastFoodUnits: Double get() { throw NotImplementedError() }
    override val coeffKinderGartenUnits: Double get() { throw NotImplementedError() }
    override val coeffTourismUnits: Double get() { throw NotImplementedError() }
    override val coeffBuildingUnits: Double get() { throw NotImplementedError() }
    override val coeffResidentialUnits: Double get() { throw NotImplementedError() }
    override val coeffCommercialUnits: Double get() { throw NotImplementedError() }
    override val coeffRetailUnits: Double get() { throw NotImplementedError() }
    override val coeffIndustrialUnits: Double get() { throw NotImplementedError() }
    override val limitOfficeUnits: Double? get() { throw NotImplementedError() }
    override val limitShopUnits: Double? get() { throw NotImplementedError() }
    override val limitSchoolUnits: Double? get() { throw NotImplementedError() }
    override val limitUniUnits: Double? get() { throw NotImplementedError() }
    override val limitPlaceOfWorshipUnits: Double? get() { throw NotImplementedError() }
    override val limitCafeUnits: Double? get() { throw NotImplementedError() }
    override val limitFastFoodUnits: Double? get() { throw NotImplementedError() }
    override val limitKinderGartenUnits: Double? get() { throw NotImplementedError() }
    override val limitTourismUnits: Double? get() { throw NotImplementedError() }
    override val limitBuildingUnits: Double? get() { throw NotImplementedError() }
    override val limitResidentialUnits: Double? get() { throw NotImplementedError() }
    override val limitCommercialUnits: Double? get() { throw NotImplementedError() }
    override val limitRetailUnits: Double? get() { throw NotImplementedError() }
    override val limitIndustrialUnits: Double? get() { throw NotImplementedError() }
    override val useLevels: Boolean = true

    override fun calcAttraction(properties: BuildingProperties): Double {
        return properties.population ?: 0.0
    }

    override fun deterrenceFunction(distance: Double): Double {
        throw NotImplementedError()
    }

    override fun calcFor(destination: RealLocation, distance: Double): Double {
        throw NotImplementedError()
    }
}

/**
 * Destination choice function implementation for the HOME location distribution when census data is not given.
 */
@Serializable
@SerialName("PureAttraction")
@Suppress("unused")
class PureAttraction (
    override val coeffResidentialArea: Double,
    override val coeffCommercialArea: Double,
    override val coeffRetailArea: Double,
    override val coeffIndustrialArea: Double,
    override val coeffOfficeArea: Double,
    override val coeffShopArea: Double,
    override val coeffSchoolArea: Double,
    override val coeffUniversityArea: Double,
    override val coeffOtherArea: Double,
    override val coeffOfficeUnits: Double,
    override val coeffShopUnits: Double,
    override val coeffSchoolUnits: Double,
    override val coeffUniUnits: Double,
    override val coeffPlaceOfWorshipUnits: Double,
    override val coeffCafeUnits: Double,
    override val coeffFastFoodUnits: Double,
    override val coeffKinderGartenUnits: Double,
    override val coeffTourismUnits: Double,
    override val coeffBuildingUnits: Double,
    override val coeffResidentialUnits: Double,
    override val coeffCommercialUnits: Double,
    override val coeffRetailUnits: Double,
    override val coeffIndustrialUnits: Double,
    override val limitOfficeUnits: Double? = null,
    override val limitShopUnits: Double? = null,
    override val limitSchoolUnits: Double? = null,
    override val limitUniUnits: Double? = null,
    override val limitPlaceOfWorshipUnits: Double? = null,
    override val limitCafeUnits: Double? = null,
    override val limitFastFoodUnits: Double? = null,
    override val limitKinderGartenUnits: Double? = null,
    override val limitTourismUnits: Double? = null,
    override val limitBuildingUnits: Double? = null,
    override val limitResidentialUnits: Double? = null,
    override val limitCommercialUnits: Double? = null,
    override val limitRetailUnits: Double? = null,
    override val limitIndustrialUnits: Double? = null,
    override val useLevels: Boolean = true
    ) : LocationChoiceDCWeightFun( ) {

    override fun deterrenceFunction(distance: Double): Double {
        throw NotImplementedError()
    }

    override fun calcFor(destination: RealLocation, distance: Double): Double {
        throw NotImplementedError()
    }
}

/**
 * Destination choice function with log-normal functional form.
 *
 * Deterrence function:
 * ln(f(d)) = a ln^2(d) + b ln(d)
 */
@Serializable
@SerialName("LogNorm")
@Suppress("unused")
class LogNormDCUtil (
    override val coeffResidentialArea: Double,
    override val coeffCommercialArea: Double,
    override val coeffRetailArea: Double,
    override val coeffIndustrialArea: Double,
    override val coeffOfficeArea: Double,
    override val coeffShopArea: Double,
    override val coeffSchoolArea: Double,
    override val coeffUniversityArea: Double,
    override val coeffOtherArea: Double,
    override val coeffOfficeUnits: Double,
    override val coeffShopUnits: Double,
    override val coeffSchoolUnits: Double,
    override val coeffUniUnits: Double,
    override val coeffPlaceOfWorshipUnits: Double,
    override val coeffCafeUnits: Double,
    override val coeffFastFoodUnits: Double,
    override val coeffKinderGartenUnits: Double,
    override val coeffTourismUnits: Double,
    override val coeffBuildingUnits: Double,
    override val coeffResidentialUnits: Double,
    override val coeffCommercialUnits: Double,
    override val coeffRetailUnits: Double,
    override val coeffIndustrialUnits: Double,
    override val limitOfficeUnits: Double? = null,
    override val limitShopUnits: Double? = null,
    override val limitSchoolUnits: Double? = null,
    override val limitUniUnits: Double? = null,
    override val limitPlaceOfWorshipUnits: Double? = null,
    override val limitCafeUnits: Double? = null,
    override val limitFastFoodUnits: Double? = null,
    override val limitKinderGartenUnits: Double? = null,
    override val limitTourismUnits: Double? = null,
    override val limitBuildingUnits: Double? = null,
    override val limitResidentialUnits: Double? = null,
    override val limitCommercialUnits: Double? = null,
    override val limitRetailUnits: Double? = null,
    override val limitIndustrialUnits: Double? = null,
    override val useLevels: Boolean = true,

    // For deterrence function
    private var coeff0: Double,
    private var coeff1: Double,
    ) : LocationChoiceDCWeightFun( ) {

    override fun deterrenceFunction(distance: Double) : Double {
        return coeff0 * ln(distance) * ln(distance) + coeff1 * ln(distance)
    }

    override fun deterrenceFunctionAsTerm(distance: Double): Pair<Term, Int> {
        val nVars = 2
        val termA = Variable(nVars, 0, ln(distance) * ln(distance))
        val termB = Variable(nVars, 1, ln(distance))

        val term = LinearTerm(nVars)
        term.addTerm(termA, 1.0)
        term.addTerm(termB, 1.0)

        return Pair(term, nVars)
    }

    override fun applyDeterrenceToTensor(distances: Array<FloatArray>, model: TfModel): Operand<TFloat32> {
        val tf = model.tf

        val vA = model.getVariable(0)
        val vB = model.getVariable(1)

        val oLnDistance = model.addMatrix( lnDistances(distances) )
        val oLnDistanceSquared = model.addMatrix( lnDistancesSquared(distances) )

        val tA = tf.math.mul(vA, oLnDistanceSquared)
        val tB = tf.math.mul(vB, oLnDistance)

        val term = tf.math.add(tA, tB)
        return term
    }

    override fun getDistanceParameters(): DoubleArray {
        return doubleArrayOf(coeff0, coeff1)
    }

    override fun setDistanceParameters(parameters: DoubleArray) {
        coeff0 = parameters[0]
        coeff1 = parameters[1]
    }
}

/**
 * Destination choice function with log-normal functional form.
 *
 * Deterrence function:
 * ln(f(d)) = a*ln^2(d) + b*ln(d) + c*d
 */
@Serializable
@SerialName("LogNormPower")
@Suppress("unused")
class LogNormPowerDCUtil (
    override val coeffResidentialArea: Double,
    override val coeffCommercialArea: Double,
    override val coeffRetailArea: Double,
    override val coeffIndustrialArea: Double,
    override val coeffOfficeArea: Double,
    override val coeffShopArea: Double,
    override val coeffSchoolArea: Double,
    override val coeffUniversityArea: Double,
    override val coeffOtherArea: Double,
    override val coeffOfficeUnits: Double,
    override val coeffShopUnits: Double,
    override val coeffSchoolUnits: Double,
    override val coeffUniUnits: Double,
    override val coeffPlaceOfWorshipUnits: Double,
    override val coeffCafeUnits: Double,
    override val coeffFastFoodUnits: Double,
    override val coeffKinderGartenUnits: Double,
    override val coeffTourismUnits: Double,
    override val coeffBuildingUnits: Double,
    override val coeffResidentialUnits: Double,
    override val coeffCommercialUnits: Double,
    override val coeffRetailUnits: Double,
    override val coeffIndustrialUnits: Double,
    override val limitOfficeUnits: Double? = null,
    override val limitShopUnits: Double? = null,
    override val limitSchoolUnits: Double? = null,
    override val limitUniUnits: Double? = null,
    override val limitPlaceOfWorshipUnits: Double? = null,
    override val limitCafeUnits: Double? = null,
    override val limitFastFoodUnits: Double? = null,
    override val limitKinderGartenUnits: Double? = null,
    override val limitTourismUnits: Double? = null,
    override val limitBuildingUnits: Double? = null,
    override val limitResidentialUnits: Double? = null,
    override val limitCommercialUnits: Double? = null,
    override val limitRetailUnits: Double? = null,
    override val limitIndustrialUnits: Double? = null,
    override val useLevels: Boolean = true,

    // For deterrence function
    private var coeff0: Double,
    private var coeff1: Double,
    private var coeff2: Double
) : LocationChoiceDCWeightFun( ) {
    @Transient
    private var maxValidDistance: Double = Double.MAX_VALUE

    init {
        // Determine minimum of deterrence function
        val earthCircumference = 40_075.017 // Unit: km
        var previousValue = Double.MAX_VALUE
        for (distance in 1 until (earthCircumference / 2).toInt()) {
            val value = deterrenceFunction(distance.toDouble())

            // Check if minimum found
            if (previousValue < value) {
                maxValidDistance = (distance - 1).toDouble()
                break
            } else {
                previousValue = value
            }
        }
    }

    override fun deterrenceFunction(distance: Double) : Double {
        return coeff0 * ln(distance) * ln(distance) + coeff1 * ln(distance) + coeff2 * distance
    }

    override fun deterrenceFunctionAsTerm(distance: Double): Pair<Term, Int> {
        val nVars = 3
        val termA = Variable(nVars, 0, ln(distance) * ln(distance))
        val termB = Variable(nVars, 1, ln(distance))
        val termC = Variable(nVars, 2, distance)

        val term = LinearTerm(nVars)
        term.addTerm(termA, 1.0)
        term.addTerm(termB, 1.0)
        term.addTerm(termC, 1.0)

        return Pair(term, nVars)
    }

    override fun applyDeterrenceToTensor(distances: Array<FloatArray>, model: TfModel): Operand<TFloat32> {
        val tf = model.tf

        val vA = model.getVariable(0)
        val vB = model.getVariable(1)
        val vC = model.getVariable(2)

        val oLnDistanceSquared = model.addMatrix( lnDistancesSquared(distances) )
        val oLnDistance = model.addMatrix( lnDistances(distances) )
        val oDistance   = model.addMatrix(distances)

        val tA = tf.math.mul(vA, oLnDistanceSquared)
        val tB = tf.math.mul(vB, oLnDistance)
        val tC = tf.math.mul(vC, oDistance)

        val term = tf.math.addN( listOf(tA, tB, tC) )
        return term
    }

    override fun getDistanceParameters(): DoubleArray {
        return doubleArrayOf(coeff0, coeff1, coeff2)
    }

    override fun setDistanceParameters(parameters: DoubleArray) {
        coeff0 = parameters[0]
        coeff1 = parameters[1]
        coeff2 = parameters[2]
    }

    override fun calcFor(destination: RealLocation, distance: Double): Double {
        return  if (distance / 1000 > maxValidDistance) {
            return 0.0
        } else {
            super.calcFor(destination, distance)
        }
    }
}

/**
 * Destination choice function with power-expon functional form.
 *
 * Deterrence function:
 * ln(f(d)) = a*d + b*ln(d)
 */
@Serializable
@SerialName("CombinedPowerExpon")
@Suppress("unused")
data class CombinedDCUtil(
    override val coeffResidentialArea: Double,
    override val coeffCommercialArea: Double,
    override val coeffRetailArea: Double,
    override val coeffIndustrialArea: Double,
    override val coeffOfficeArea: Double,
    override val coeffShopArea: Double,
    override val coeffSchoolArea: Double,
    override val coeffUniversityArea: Double,
    override val coeffOtherArea: Double,
    override val coeffOfficeUnits: Double,
    override val coeffShopUnits: Double,
    override val coeffSchoolUnits: Double,
    override val coeffUniUnits: Double,
    override val coeffPlaceOfWorshipUnits: Double,
    override val coeffCafeUnits: Double,
    override val coeffFastFoodUnits: Double,
    override val coeffKinderGartenUnits: Double,
    override val coeffTourismUnits: Double,
    override val coeffBuildingUnits: Double,
    override val coeffResidentialUnits: Double,
    override val coeffCommercialUnits: Double,
    override val coeffRetailUnits: Double,
    override val coeffIndustrialUnits: Double,
    override val limitOfficeUnits: Double? = null,
    override val limitShopUnits: Double? = null,
    override val limitSchoolUnits: Double? = null,
    override val limitUniUnits: Double? = null,
    override val limitPlaceOfWorshipUnits: Double? = null,
    override val limitCafeUnits: Double? = null,
    override val limitFastFoodUnits: Double? = null,
    override val limitKinderGartenUnits: Double? = null,
    override val limitTourismUnits: Double? = null,
    override val limitBuildingUnits: Double? = null,
    override val limitResidentialUnits: Double? = null,
    override val limitCommercialUnits: Double? = null,
    override val limitRetailUnits: Double? = null,
    override val limitIndustrialUnits: Double? = null,
    override val useLevels: Boolean = true,

    // For deterrence function
    private var coeff0: Double,
    private var coeff1: Double,
) : LocationChoiceDCWeightFun( ) {

    override fun deterrenceFunction(distance: Double) : Double {
        return coeff0 * distance  + coeff1 * ln(distance)
    }

    override fun deterrenceFunctionAsTerm(distance: Double): Pair<Term, Int> {
        val nVars = 2
        val termA = Variable(nVars, 0, distance)
        val termB = Variable(nVars, 1, ln(distance))

        val term = LinearTerm(nVars)
        term.addTerm(termA, 1.0)
        term.addTerm(termB, 1.0)

        return Pair(term, nVars)
    }

    override fun applyDeterrenceToTensor(distances: Array<FloatArray>, model: TfModel): Operand<TFloat32> {
        val tf = model.tf

        val vA = model.getVariable(0)
        val vB = model.getVariable(1)

        val oDistance   = model.addMatrix(distances)
        val oLnDistance = model.addMatrix( lnDistances(distances) )

        val tA = tf.math.mul(vA, oDistance)
        val tB = tf.math.mul(vB, oLnDistance)

        val term = tf.math.add(tA, tB)
        return term
    }

    override fun getDistanceParameters(): DoubleArray {
        return doubleArrayOf(coeff0, coeff1)
    }

    override fun setDistanceParameters(parameters: DoubleArray) {
        coeff0 = parameters[0]
        coeff1 = parameters[1]
    }
}

@Serializable
@SerialName("Ln3")
@Suppress("unused")
class Ln3 (
    override val coeffResidentialArea: Double,
    override val coeffCommercialArea: Double,
    override val coeffRetailArea: Double,
    override val coeffIndustrialArea: Double,
    override val coeffOfficeArea: Double,
    override val coeffShopArea: Double,
    override val coeffSchoolArea: Double,
    override val coeffUniversityArea: Double,
    override val coeffOtherArea: Double,
    override val coeffOfficeUnits: Double,
    override val coeffShopUnits: Double,
    override val coeffSchoolUnits: Double,
    override val coeffUniUnits: Double,
    override val coeffPlaceOfWorshipUnits: Double,
    override val coeffCafeUnits: Double,
    override val coeffFastFoodUnits: Double,
    override val coeffKinderGartenUnits: Double,
    override val coeffTourismUnits: Double,
    override val coeffBuildingUnits: Double,
    override val coeffResidentialUnits: Double,
    override val coeffCommercialUnits: Double,
    override val coeffRetailUnits: Double,
    override val coeffIndustrialUnits: Double,
    override val limitOfficeUnits: Double? = null,
    override val limitShopUnits: Double? = null,
    override val limitSchoolUnits: Double? = null,
    override val limitUniUnits: Double? = null,
    override val limitPlaceOfWorshipUnits: Double? = null,
    override val limitCafeUnits: Double? = null,
    override val limitFastFoodUnits: Double? = null,
    override val limitKinderGartenUnits: Double? = null,
    override val limitTourismUnits: Double? = null,
    override val limitBuildingUnits: Double? = null,
    override val limitResidentialUnits: Double? = null,
    override val limitCommercialUnits: Double? = null,
    override val limitRetailUnits: Double? = null,
    override val limitIndustrialUnits: Double? = null,
    override val useLevels: Boolean = true,

    // For deterrence function
    private var coeff0: Double,
    private var coeff1: Double,
    private var coeff2: Double
) : LocationChoiceDCWeightFun( ) {
    override fun deterrenceFunction(distance: Double) : Double {
        return coeff0 * ln(distance) * ln(distance) + coeff1 * ln(distance) + coeff2 * ln(distance) * ln(distance) * ln(distance)
    }

    override fun deterrenceFunctionAsTerm(distance: Double): Pair<Term, Int> {
        val nVars = 3
        val termA = Variable(nVars, 0, ln(distance) * ln(distance))
        val termB = Variable(nVars, 1, ln(distance))
        val termC = Variable(nVars, 2, ln(distance) * ln(distance) * ln(distance))

        val term = LinearTerm(nVars)
        term.addTerm(termA, 1.0)
        term.addTerm(termB, 1.0)
        term.addTerm(termC, 1.0)

        return Pair(term, nVars)
    }

    override fun applyDeterrenceToTensor(distances: Array<FloatArray>, model: TfModel): Operand<TFloat32> {
        val tf = model.tf

        val vA = model.getVariable(0)
        val vB = model.getVariable(1)
        val vC = model.getVariable(2)

        val oLnDistance = model.addMatrix( lnDistances(distances) )
        val oLnDistanceSquared = model.addMatrix( lnDistancesSquared(distances) )
        val oLnDistanceCubed = model.addMatrix( lnDistancesCubed(distances) )

        val tA = tf.math.mul(vA, oLnDistanceSquared)
        val tB = tf.math.mul(vB, oLnDistance)
        val tC = tf.math.mul(vC, oLnDistanceCubed)

        val term = tf.math.addN(listOf(tA, tB, tC))
        return term
    }

    override fun getDistanceParameters(): DoubleArray {
        return doubleArrayOf(coeff0, coeff1, coeff2)
    }

    override fun setDistanceParameters(parameters: DoubleArray) {
        coeff0 = parameters[0]
        coeff1 = parameters[1]
        coeff2 = parameters[2]
    }
}
