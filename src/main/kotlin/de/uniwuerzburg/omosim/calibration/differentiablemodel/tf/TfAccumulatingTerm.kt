package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

class TfAccumulatingTerm(
    var value: Operand<TFloat32>
)