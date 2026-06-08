package de.uniwuerzburg.omosim.calibration.differentiablemodel.tf

import java.lang.ref.Cleaner
import org.tensorflow.ndarray.Shape
import org.tensorflow.types.TFloat32

class ThreadLocalTensorContainer(size: Long) {
    val tensor: TFloat32 = TFloat32.tensorOf(Shape.of(size))

    companion object {
        private val cleaner = Cleaner.create()
    }

    init {
        val capturedTensor = tensor
        cleaner.register(this) {
            capturedTensor.close()
        }
    }
}