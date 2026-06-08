package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.differentiablemodel.tf.TfModelUV
import org.tensorflow.Operand
import org.tensorflow.types.TFloat32

interface VMatrixBuilderTF {
    fun build(mrep: SGGravity.SGCompactMatrixRep) : Pair<Operand<TFloat32>, TfModelUV>
}