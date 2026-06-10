package de.uniwuerzburg.omosim.calibration

/**
 * Optimization algorithm options
 *
 * @see de.uniwuerzburg.omosim.calibration.algorithms
 */
enum class CalibrationAlgorithm {
    SM_LBFGS,
    SM_MINBC,
    SM_GD,
    SM_PSO, PSO, PSO_AO,
    SM_SPSA, SPSA, SPSA_AO,
    SM_WSPSA, WSPSA,
    SM_MATRIX,
}