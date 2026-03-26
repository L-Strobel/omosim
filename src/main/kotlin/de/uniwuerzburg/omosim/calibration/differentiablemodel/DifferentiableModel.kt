package de.uniwuerzburg.omosim.calibration.differentiablemodel

interface DifferentiableModel {
    fun visit(visitor: (term: Term) -> Unit)
}