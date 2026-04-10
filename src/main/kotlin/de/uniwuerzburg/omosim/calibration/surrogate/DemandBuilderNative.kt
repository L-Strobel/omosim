package de.uniwuerzburg.omosim.calibration.surrogate

import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.LinearTerm
import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.Term

class DemandBuilderNative(
    val nVars: Int
) : DemandBuilderListBased<Term, LinearTerm>() {
    override fun addVar(term: LinearTerm, v: Term, coefficient: Double) {
        term.addTerm(v, coefficient)
    }

    override fun addConstant(term: LinearTerm, constant: Double) {
        term.addConstant(constant)
    }

    override fun addTerm(term: LinearTerm, other: LinearTerm, coefficient: Double) {
        term.addTerm(other, coefficient)
    }

    override fun new(): LinearTerm {
        return LinearTerm(nVars)
    }
}