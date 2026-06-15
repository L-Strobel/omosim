package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.Term

interface VMatrixBuilderNative {
    fun build(mrep: SurrogateGravity.SGCompactMatrixRep) : Pair<List<List<Term>>, Int>
}