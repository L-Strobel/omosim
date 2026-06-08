package de.uniwuerzburg.omosim.calibration.differentiablemodel

import de.uniwuerzburg.omosim.calibration.differentiablemodel.nat.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class NativeUVTest {

    companion object {
        @Suppress("SameParameterValue")
        fun buildLargeTestModel(nVars: Int) : NativeUV {
            val model = NativeUV(nVars)

            val lTerm1 = LinearTerm(nVars)
            for (i in 0 until nVars) {
                val lbTerm = LinearBaseTerm(nVars)
                lbTerm.addConstant(1.3)
                lbTerm.addTerm(i, 3.3)
                lTerm1.addTerm(lbTerm, 1.0)
            }

            val p1 = LinearBaseTerm(nVars)
            p1.addConstant(2.2)
            p1.addTerm(0, 1.1)

            val p2 = LinearBaseTerm(nVars)
            p2.addConstant(2.2)
            p2.addTerm(1, 1.1)

            val top = QuadraticTerm(nVars, p1, p2,-1.1)

            val dTerm = DivisionTerm(nVars, lTerm1, top)
            model.setRootTerm(dTerm)
            return  model
        }
    }

    @Test
    fun largerModel() {
        val nVars = 1000
        val model = buildLargeTestModel(nVars)

        val vars = DoubleArray(nVars) { 1.1 }

        // Backward
        val gradientB = DoubleArray(nVars) { 0.0 }
        model.gradientReverse(vars, gradientB, 1.0)

        // Forward
        val gradientF = DoubleArray(nVars) { 0.0 }
        for (i in 0 until nVars) {
            gradientF[i] = model.gradientForward(i, vars)
        }

        assert(gradientF.zip(gradientB).all { abs(it.first - it.second) <= 1e-5 })
    }

    /**
     * f: 3x + 1y
     * vars: x=1, y=1
     * gradients: d/dx = 3, d/dy = 1
     * value: 4
     *
     * mode: Reverse
     */
    @Test
    fun bbLinearTest1() {
        val vars = doubleArrayOf(1.0, 1.0)

        // Build model
        val term = LinearBaseTerm(2)
        term.addTerm(0, 3.0)
        term.addTerm(1, 1.0)
        val model = NativeUV(2)
        model.setRootTerm(term)

        // Test
        val gradient = DoubleArray(2) { 0.0 }
        val x = model.evaluate(vars)
        model.gradientReverse(vars, gradient, 1.0)
        val gx = gradient[0]
        val gy = gradient[1]

        assert(gx == 3.0)
        assert(gy == 1.0)
        assert(x == 4.0)
    }

    /**
     * f: 3xy
     * vars: x=2, y=5
     * gradients: d/dx = 15, d/dy = 6
     * value: 30
     *
     * mode: Reverse
     */
    @Test
    fun bbQuadraticTest1() {
        val vars = doubleArrayOf(2.0, 5.0)

        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val yTerm = LinearBaseTerm(2)
        yTerm.addTerm(1, 1.0)
        val qTerm = QuadraticTerm(2, xTerm, yTerm, 3.0)
        val model = NativeUV(2)
        model.setRootTerm(qTerm)

        // Test
        val gradient = DoubleArray(2) { 0.0 }
        val x = model.evaluate(vars)
        model.gradientReverse(vars, gradient, 1.0)
        val gx = gradient[0]
        val gy = gradient[1]

        assert(gx == 15.0)
        assert(gy == 6.0)
        assert(x == 30.0)
    }

    /**
     * f: 1.5x^2
     * vars: x=0.3
     * gradients:  d/dx = 0.9, d/dy = 0.0
     * value: 0.13499999999999998
     *
     * mode: Reverse
     */
    @Test
    fun bbQuadraticTest2() {
        val vars = doubleArrayOf(0.3, 100.0)

        // Build Model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val qTerm = QuadraticTerm(2, xTerm, xTerm, 1.5)
        val model = NativeUV(2)
        model.setRootTerm(qTerm)

        // Test
        val gradient = DoubleArray(2) { 0.0 }
        val x = model.evaluate(vars)
        model.gradientReverse(vars, gradient, 1.0)
        val gx = gradient[0]
        val gy = gradient[1]

        assert(gx == 3 * 0.3)
        assert(gy == 0.0)
        assert(x == 0.3 * 0.3 * 1.5)
    }

    /**
     * f: 2e(xy)
     * vars:  x=1, y=5
     * gradients: d/dx = 10e(5) = 1484.131591025766, d/dy = 2e(5) = 296.8263182051532
     * value: 2e(5) = 296.8263182051532
     *
     * mode: Reverse
     */
    @Test
    fun bbExponTest1() {
        val vars = doubleArrayOf(1.0, 5.0)

        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val yTerm = LinearBaseTerm(2)
        yTerm.addTerm(1, 1.0)
        val qTerm = QuadraticTerm(2, xTerm, yTerm, 1.0)
        val eTerm = ExponentialTerm(2, qTerm)
        val fTerm = LinearTerm(2)
        fTerm.addTerm(eTerm, 2.0)
        val model = NativeUV(2)
        model.setRootTerm(fTerm)

        // Test
        val gradient = DoubleArray(2) { 0.0 }
        val x = model.evaluate(vars)
        model.gradientReverse(vars, gradient, 1.0)
        val gx = gradient[0]
        val gy = gradient[1]

        assert(gx == 1484.131591025766)
        assert(gy == 296.8263182051532)
        assert(x == 296.8263182051532)
    }


    /**
     * f: 3.3e(xy + x + 2y - 1)
     * vars: x=3, y=-2
     * gradients: d/dx = 3.3(y + 1)e(xy + x + 2y - 1) = -3.3e(-8) = -0.001107026672078289
     *            d/dy = 3.3(x + 2)e(-8) = 16.5e(-8) = 0.005535133360391445
     * value: 2e(5) = 3.3e(-6 + 3 -4 - 1) = 3.3e(-8) = 0.001107026672078289
     *
     * mode: Reverse
     */
    @Test
    fun bbExponTest2() {
        val vars = doubleArrayOf(3.0, -2.0)

        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val yTerm = LinearBaseTerm(2)
        yTerm.addTerm(1, 1.0)
        val qTerm = QuadraticTerm(2, xTerm, yTerm, 1.0)
        val exponent = LinearTerm(2)
        exponent.addTerm(qTerm, 1.0)
        exponent.addTerm(xTerm, 1.0)
        exponent.addTerm(yTerm, 2.0)
        exponent.addConstant(-1.0)
        val eTerm = ExponentialTerm(2, exponent)
        val fTerm = LinearTerm(2)
        fTerm.addTerm(eTerm, 3.3)
        val model = NativeUV(2)
        model.setRootTerm(fTerm)

        // Test
        val gradient = DoubleArray(2) { 0.0 }
        val x = model.evaluate(vars)
        model.gradientReverse(vars, gradient, 1.0)
        val gx = gradient[0]
        val gy = gradient[1]

        assert(gx == -0.001107026672078289)
        assert(gy == 0.005535133360391445)
        assert(x == 0.001107026672078289)
    }

    /**
     * f: x/y
     * vars: x=3.3, y=-1.2
     * gradients: d/dx = 1/-1.2 = -0.8333333333333334
     *            d/dy = -x/(y*y) = -3.3/(1.44) = -2.2916666666666665
     * value: -2.75
     *
     * mode: Reverse
     */
    @Test
    fun bbDivisionTermTest1() {
        val vars = doubleArrayOf(3.3, -1.2)

        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val yTerm = LinearBaseTerm(2)
        yTerm.addTerm(1, 1.0)
        val dTerm = DivisionTerm(2, xTerm, yTerm)
        val model = NativeUV(2)
        model.setRootTerm(dTerm)

        // Test
        val gradient = DoubleArray(2) { 0.0 }
        val x = model.evaluate(vars)
        model.gradientReverse(vars, gradient, 1.0)
        val gx = gradient[0]
        val gy = gradient[1]

        assert(gx == -0.8333333333333334)
        assert(gy == -2.2916666666666665)
        assert(x == -2.75)
    }

    /**
     * f: x/(1x+2y)
     * vars: x=2, y=-3
     * gradients: d/dx = (x + 2y - x) / (1x+2y)**2 = -6 / 16
     *            d/dy = -2x / (1x+2y)**2 = 4 / 16 = -1/4 = -0.25
     * value: 2 / -4 = -0.5
     *
     * mode: Reverse
     */
    @Test
    fun bbDivisionTermTest2() {
        val vars = doubleArrayOf(2.0, -3.0)

        // Build model
        val dividend = LinearBaseTerm(2)
        dividend.addTerm(0, 1.0)
        val divisor = LinearBaseTerm(2)
        divisor.addTerm(0, 1.0)
        divisor.addTerm(1, 2.0)
        val dTerm = DivisionTerm(2, dividend, divisor)
        val model = NativeUV(2)
        model.setRootTerm(dTerm)

        // Test
        val gradient = DoubleArray(2) { 0.0 }
        val x = model.evaluate(vars)
        model.gradientReverse(vars, gradient, 1.0)
        val gx = gradient[0]
        val gy = gradient[1]

        assert(gx == (-6.0 / 16.0))
        assert(gy == -0.25)
        assert(x ==  -0.5)
    }

    /**
     * f: 3x + 1y
     * vars: x=1, y=1
     * gradients: d/dx = 3, d/dy = 1
     * value: 4
     *
     * mode: Forward
     */
    @Test
    fun linearTest1() {
        val vars = doubleArrayOf(1.0, 1.0)

        // Build model
        val term = LinearBaseTerm(2)
        term.addTerm(0, 3.0)
        term.addTerm(1, 1.0)

        // Test
        val gx = term.gradientForward(0, vars)
        term.clearGradientCache()
        val gy = term.gradientForward(1, vars)
        term.clearGradientCache()
        val x = term.evaluate(vars)

        assert(gx == 3.0)
        assert(gy == 1.0)
        assert(x == 4.0)
    }

    /**
     * f: 3xy
     * vars: x=2, y=5
     * gradients: d/dx = 15, d/dy = 6
     * value: 30
     *
     * mode: Forward
     */
    @Test
    fun quadraticTest1() {
        val vars = doubleArrayOf(2.0, 5.0)

        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val yTerm = LinearBaseTerm(2)
        yTerm.addTerm(1, 1.0)
        val qTerm = QuadraticTerm(2, xTerm, yTerm, 3.0)

        // Test
        val gx = qTerm.gradientForward(0, vars)
        qTerm.clearGradientCache()
        val gy = qTerm.gradientForward(1, vars)
        qTerm.clearGradientCache()
        val x = qTerm.evaluate(vars)

        assert(gx == 15.0)
        assert(gy == 6.0)
        assert(x == 30.0)
    }

    /**
     * f: 1.5xx
     * vars: x=0.3
     * gradients: d/dx = 0.9, d/dy = 0.0
     * value: 0.13499999999999998
     *
     * mode: Forward
     */
    @Test
    fun quadraticTest2() {
        val vars = doubleArrayOf(0.3, 100.0)

        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val qTerm = QuadraticTerm(2, xTerm, xTerm, 1.5)

        // Test
        val gx = qTerm.gradientForward(0, vars)
        qTerm.clearGradientCache()
        val gy = qTerm.gradientForward(1, vars)
        qTerm.clearGradientCache()
        val x = qTerm.evaluate(vars)

        assert(gx == 3 * 0.3)
        assert(gy == 0.0)
        assert(x == 0.3 * 0.3 * 1.5)
    }

    /**
     * f: 2e(xy)
     * vars: x=1, y=5
     * gradients: d/dx = 10e(5) = 1484.131591025766, d/dy = 2e(5) = 296.8263182051532
     * value: 296.8263182051532
     *
     * mode: Forward
     */
    @Test
    fun exponTest1() {
        val vars = doubleArrayOf(1.0, 5.0)

        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val yTerm = LinearBaseTerm(2)
        yTerm.addTerm(1, 1.0)
        val qTerm = QuadraticTerm(2, xTerm, yTerm, 1.0)
        val eTerm = ExponentialTerm(2, qTerm)
        val fTerm = LinearTerm(2)
        fTerm.addTerm(eTerm, 2.0)

        // Test
        val gx = fTerm.gradientForward(0, vars)
        fTerm.clearGradientCache()
        val gy = fTerm.gradientForward(1, vars)
        fTerm.clearGradientCache()
        val x = fTerm.evaluate(vars)

        assert(gx == 1484.131591025766)
        assert(gy == 296.8263182051532)
        assert(x == 296.8263182051532)
    }

    /**
     * f: 3.3e(xy + x + 2y - 1)
     * vars: x=3, y=-2
     * gradients: d/dx = 3.3(y + 1)e(xy + x + 2y - 1) = -3.3e(-8) = -0.001107026672078289
     *            d/dy = 3.3(x + 2)e(-8) = 16.5e(-8) = 0.005535133360391445
     * value: 3.3e(-6 + 3 -4 - 1) = 3.3e(-8) = 0.001107026672078289
     *
     * mode: Forward
     */
    @Test
    fun exponTest2() {
        val vars = doubleArrayOf(3.0, -2.0)

        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val yTerm = LinearBaseTerm(2)
        yTerm.addTerm(1, 1.0)
        val qTerm = QuadraticTerm(2, xTerm, yTerm, 1.0)
        val exponent = LinearTerm(2)
        exponent.addTerm(qTerm, 1.0)
        exponent.addTerm(xTerm, 1.0)
        exponent.addTerm(yTerm, 2.0)
        exponent.addConstant(-1.0)
        val eTerm = ExponentialTerm(2, exponent)
        val fTerm = LinearTerm(2)
        fTerm.addTerm(eTerm, 3.3)

        // Test
        val gx = fTerm.gradientForward(0, vars)
        fTerm.clearGradientCache()
        val gy = fTerm.gradientForward(1, vars)
        fTerm.clearGradientCache()
        val x = fTerm.evaluate(vars)

        assert(gx == -0.001107026672078289)
        assert(gy == 0.005535133360391445)
        assert(x == 0.001107026672078289)
    }

    /**
     * f: x/y
     * vars: x=3.3, y=-1.2
     * gradients: d/dx = 1/-1.2 = -0.8333333333333334
     *            d/dy = -x/(y*y) = -3.3/(1.44) = -2.2916666666666665
     * value: -2.75
     *
     * mode: Forward
     */
    @Test
    fun divisionTermTest1() {
        val vars = doubleArrayOf(3.3, -1.2)

        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val yTerm = LinearBaseTerm(2)
        yTerm.addTerm(1, 1.0)
        val dTerm = DivisionTerm(2, xTerm, yTerm)

        // Test
        val gx = dTerm.gradientForward(0, vars)
        dTerm.clearGradientCache()
        val gy = dTerm.gradientForward(1, vars)
        dTerm.clearGradientCache()
        val x = dTerm.evaluate(vars)

        assert(gx == -0.8333333333333334)
        assert(gy == -2.2916666666666665)
        assert(x == -2.75)
    }

    /**
     * f: x/(1x+2y)
     * vars: x=2, y=-3
     * gradients: d/dx = (x + 2y - x) / (1x+2y)**2 = -6 / 16
     *            d/dy = -2x / (1x+2y)**2 = 4 / 16 = -1/4 = -0.25
     * value: 2 / -4 = -0.5
     *
     * mode: Forward
     */
    @Test
    fun divisionTermTest2() {
        val vars = doubleArrayOf(2.0, -3.0)

        // Build model
        val dividend = LinearBaseTerm(2)
        dividend.addTerm(0, 1.0)
        val divisor = LinearBaseTerm(2)
        divisor.addTerm(0, 1.0)
        divisor.addTerm(1, 2.0)
        val dTerm = DivisionTerm(2, dividend, divisor)

        // Test
        val gx = dTerm.gradientForward(0, vars)
        dTerm.clearGradientCache()
        val gy = dTerm.gradientForward(1, vars)
        dTerm.clearGradientCache()
        val x = dTerm.evaluate(vars)

        assert(gx == (-6.0 / 16.0))
        assert(gy == -0.25)
        assert(x ==  -0.5)
    }

    /**
     * TEST 1
     * f: 2e(xy)
     * vars: x=1, y=5
     * gradients: d/dx = 10e(5) = 1484.131591025766
     *            d/dy = 2e(5) = 296.8263182051532
     * value: 2e(5) = 296.8263182051532
     *
     * TEST 2
     * f: 2e(xy)
     * vars:  x=1, y=-1
     * gradients: d/dx = -2e(-1) = -0.7357588823428847
     *            d/dy = 2e(-1) = 0.7357588823428847
     * value: 2e(-1) = 0.7357588823428847
     *
     * mode: Forward
     */
    @Test
    fun cacheTest1() {
        // Build model
        val xTerm = LinearBaseTerm(2)
        xTerm.addTerm(0, 1.0)
        val yTerm = LinearBaseTerm(2)
        yTerm.addTerm(1, 1.0)
        val qTerm = QuadraticTerm(2, xTerm, yTerm, 1.0)
        val eTerm = ExponentialTerm(2, qTerm)
        val fTerm = LinearTerm(2)
        fTerm.addTerm(eTerm, 2.0)
        val model = NativeUV(2)
        model.setRootTerm(fTerm)

        // Test 1
        val vars = doubleArrayOf(1.0, 5.0)
        val gx = model.gradientForward(0, vars)
        model.clearGradientCache()
        val gy = model.gradientForward(1, vars)
        model.clearGradientCache()
        val x = model.evaluate(vars)
        model.clearEvalCache()

        assert(gx == 1484.131591025766)
        assert(gy == 296.8263182051532)
        assert(x == 296.8263182051532)

        // TEST 2
        val vars2 = doubleArrayOf(1.0, -1.0)
        val gx2 = model.gradientForward(0, vars2)
        model.clearGradientCache()
        val gy2 = model.gradientForward(1, vars2)
        model.clearGradientCache()
        val x2 = model.evaluate(vars2)
        model.clearEvalCache()

        assert(gx2 == -0.7357588823428847)
        assert(gy2 == 0.7357588823428847)
        assert(x2  == 0.7357588823428847)
    }
}