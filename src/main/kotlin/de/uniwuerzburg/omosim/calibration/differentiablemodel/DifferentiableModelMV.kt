package de.uniwuerzburg.omosim.calibration.differentiablemodel

interface DifferentiableModelMV : DifferentiableModel {
    fun jacobian(vals: DoubleArray, nWorker: Int? = null) : Array<DoubleArray>
}