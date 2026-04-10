package de.uniwuerzburg.omosim.calibration.surrogate

import com.gurobi.gurobi.GRBLinExpr
import com.gurobi.gurobi.GRBVar

/**
 * Term builder for Gurobi.
 * Used to generate Gurobi Terms from matrix multiplications of the form: AXB,
 * where X is a matrix filled with variable terms.
 */
class DemandBuilderGRB: DemandBuilderListBased<GRBVar, GRBLinExpr>() {
    override fun addVar(term: GRBLinExpr, v: GRBVar, coefficient: Double) {
        term.addTerm(coefficient, v)
    }

    override fun addConstant(term: GRBLinExpr, constant: Double) {
        term.addConstant(constant)
    }

    override fun addTerm(term: GRBLinExpr, other: GRBLinExpr, coefficient: Double) {
        term.multAdd(coefficient, other)
    }

    override fun new(): GRBLinExpr {
        return GRBLinExpr()
    }
}