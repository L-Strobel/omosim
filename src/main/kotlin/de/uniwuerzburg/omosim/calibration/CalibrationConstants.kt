package de.uniwuerzburg.omosim.calibration

object CalibrationConstants {
    var T = 24 // Number of time slices for calibration day. Will change when the calibration data is read.
    const val MC_SAMPLES = 10_000 // Number of Monte Carlo samples. Used to determine the car trip start distribution.
    const val TENSOR_FLOW = true
}