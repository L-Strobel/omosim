package de.uniwuerzburg.omosim.calibration.algorithms

import org.junit.jupiter.api.Test
import java.util.*
import kotlin.math.abs

class PSOTest {
    val tol = 1e-5

    @Test
    fun testDiffModel() {
        val (objective, _) = TestObjectives.diffModel()
        val xOpt = PSO.run(1, objective, lb=-100.0, ub=100.0, iterations = 1000)
        assert(abs(0.0 - objective(xOpt)) <= tol)
    }

    @Test
    fun testQuadraticFunction() {
        val (objective, _) = TestObjectives.sphere(1, 0.0)
        val xOpt = PSO.run(1, objective, lb=-100.0, ub=100.0, iterations = 1000)
        assert(abs(0.0 - objective(xOpt)) <= tol)
    }

    @Test
    fun testSphere() {
        val nDimensions = 10
        val (objective, _) = TestObjectives.sphere(nDimensions, 0.0)
        val xOpt = PSO.run(nDimensions, objective, lb=-100.0, ub=100.0, iterations=1000, w=0.5)
        println(xOpt.toList())
        assert(abs(0.0 - objective(xOpt)) <= tol)
    }

    @Test
    fun testShiftedSphere() {
        val nDimensions = 10
        val (objective, _) = TestObjectives.sphere(nDimensions, 10.0)
        val xOpt = PSO.run(nDimensions, objective, lb=-100.0, ub=100.0, iterations=1000, w=0.5)
        assert(abs(0.0 - objective(xOpt)) <= tol)
    }
}