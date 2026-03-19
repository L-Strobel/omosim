package de.uniwuerzburg.omosim.calibration

/**
 *  Options for what part of the model should be calibrated.
 *
 *  GRAVITY: Attraction scaling in the gravity model
 *  MODE_CHOICE: Car mode intercept in mode choice model
 *  ROUTE_CHOICE: Route choice between alternatives given by GraphHopper
 *  EVALUATE: Test the current calibration and print out a summary
 */
enum class CalibrationType {
    GRAVITY, MODE_CHOICE, ROUTE_CHOICE, EVALUATE, DEBUG
}