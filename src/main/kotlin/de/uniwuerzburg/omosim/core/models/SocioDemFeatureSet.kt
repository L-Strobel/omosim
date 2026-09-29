package de.uniwuerzburg.omosim.core.models

/**
 * A combination of socio demographic features. Used in agent creation process.
 */
class SocioDemFeatureSet (
    val hom: HomogeneousGrp,
    val mob: MobilityGrp,
    val age: Int?,
    val income: Int?,
    val sex: Sex
)