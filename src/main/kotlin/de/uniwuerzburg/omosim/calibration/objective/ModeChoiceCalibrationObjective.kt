package de.uniwuerzburg.omosim.calibration.objective

/**
 * Objectives for mode choice calibration
 *
 * FitTotalCarTrips: Calibrate the total number of car trips across all measurements: minimize (sum(M) - sum(S))^2
 * FitIndividualMeasurements: Calibrate each measurement individually (normal case): minimize (sum(m - s))^2
 */
enum class ModeChoiceCalibrationObjective {
    FitTotalCarTrips, FitIndividualMeasurements
}