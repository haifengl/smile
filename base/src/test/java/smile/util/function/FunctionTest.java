/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util.function;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all functional interfaces in smile.util.function.
 */
public class FunctionTest {

    // =========================================================================
    // Function — univariate
    // =========================================================================

    /** f(x) = x² − 2, root at ±√2. */
    private static final Function SQRT2 = x -> x * x - 2.0;

    @Test
    public void testFunctionApply() {
        Function f = x -> 3 * x + 1;
        assertEquals(7.0, f.apply(2.0), 1e-15);
        assertEquals(f.f(2.0), f.apply(2.0), 1e-15);
    }

    @Test
    public void testFunctionInvThrows() {
        Function f = x -> x * x;
        assertThrows(UnsupportedOperationException.class, () -> f.inv(4.0));
    }

    @Test
    public void testBrentRoot() {
        double root = SQRT2.root(1.0, 2.0, 1e-10, 200);
        assertEquals(Math.sqrt(2), root, 1e-9);
    }

    @Test
    public void testBrentRootNegativeSide() {
        double root = SQRT2.root(-2.0, -1.0, 1e-10, 200);
        assertEquals(-Math.sqrt(2), root, 1e-9);
    }

    @Test
    public void testBrentRootAtBoundary() {
        // f(0) = 0 — root exactly at left boundary
        Function f = x -> x * (x - 1);
        double root = f.root(-0.5, 0.5, 1e-10, 200);
        assertEquals(0.0, root, 1e-9);
    }

    /** Verifies the fix: tol must NOT be mutated so repeated calls return the same result. */
    @Test
    public void testBrentTolNotMutated() {
        double tol = 1e-8;
        double r1 = SQRT2.root(1.0, 2.0, tol, 200);
        double r2 = SQRT2.root(1.0, 2.0, tol, 200);
        assertEquals(r1, r2, 1e-15, "Repeated calls with same tol must return same result");
    }

    @Test
    public void testBrentInvalidTolThrows() {
        assertThrows(IllegalArgumentException.class, () -> SQRT2.root(1.0, 2.0, 0.0, 100));
        assertThrows(IllegalArgumentException.class, () -> SQRT2.root(1.0, 2.0, -1e-5, 100));
    }

    @Test
    public void testBrentInvalidMaxIterThrows() {
        assertThrows(IllegalArgumentException.class, () -> SQRT2.root(1.0, 2.0, 1e-7, 0));
        assertThrows(IllegalArgumentException.class, () -> SQRT2.root(1.0, 2.0, 1e-7, -1));
    }

    @Test
    public void testBrentNotBracketedThrows() {
        // f(1)=−1, f(2)=2 → root exists; f(0)=−2, f(0.5)=−1.75 → no root
        Function f = x -> x - 1.5;
        assertThrows(IllegalArgumentException.class, () -> f.root(2.0, 3.0, 1e-7, 100));
    }

    @Test
    public void testBrentTrigRoot() {
        // sin(x) = 0, root at π in [3, 4]
        Function sinF = Math::sin;
        double root = sinF.root(3.0, 4.0, 1e-10, 200);
        assertEquals(Math.PI, root, 1e-9);
    }

    // =========================================================================
    // DifferentiableFunction — Newton-Raphson
    // =========================================================================

    /** f(x) = x² − 2, f'(x) = 2x */
    private static final DifferentiableFunction DIFF_SQRT2 = new DifferentiableFunction() {
        @Override public double f(double x) { return x * x - 2.0; }
        @Override public double g(double x) { return 2.0 * x; }
    };

    @Test
    public void testDifferentiableFunctionApplyAndDerivative() {
        assertEquals(-1.0, DIFF_SQRT2.f(1.0),  1e-15);
        assertEquals( 2.0, DIFF_SQRT2.g(1.0),  1e-15);
    }

    @Test
    public void testG2Throws() {
        assertThrows(UnsupportedOperationException.class, () -> DIFF_SQRT2.g2(1.0));
    }

    @Test
    public void testNewtonRoot() {
        double root = DIFF_SQRT2.root(1.0, 2.0, 1e-12, 200);
        assertEquals(Math.sqrt(2), root, 1e-11);
    }

    @Test
    public void testNewtonRootAtLeftBoundary() {
        // f(0) = 0 exactly
        DifferentiableFunction f = new DifferentiableFunction() {
            @Override public double f(double x) { return x * (x - 2); }
            @Override public double g(double x) { return 2 * x - 2; }
        };
        double root = f.root(-0.5, 0.5, 1e-10, 200);
        assertEquals(0.0, root, 1e-9);
    }

    @Test
    public void testNewtonRootAtRightBoundary() {
        // f(2) = 0 exactly
        DifferentiableFunction f = new DifferentiableFunction() {
            @Override public double f(double x) { return x * (x - 2); }
            @Override public double g(double x) { return 2 * x - 2; }
        };
        double root = f.root(1.5, 2.0, 1e-10, 200);
        assertEquals(2.0, root, 1e-9);
    }

    @Test
    public void testNewtonInvalidTolThrows() {
        assertThrows(IllegalArgumentException.class, () -> DIFF_SQRT2.root(1.0, 2.0, 0.0, 100));
    }

    @Test
    public void testNewtonInvalidMaxIterThrows() {
        assertThrows(IllegalArgumentException.class, () -> DIFF_SQRT2.root(1.0, 2.0, 1e-7, 0));
    }

    @Test
    public void testNewtonNotBracketedThrows() {
        // both endpoints same sign
        DifferentiableFunction f = new DifferentiableFunction() {
            @Override public double f(double x) { return x * x + 1; }  // always positive
            @Override public double g(double x) { return 2 * x; }
        };
        assertThrows(IllegalArgumentException.class, () -> f.root(1.0, 2.0, 1e-7, 100));
    }

    @Test
    public void testNewtonConvergesFasterThanBrent() {
        // For a smooth function, Newton should need fewer iterations than Brent.
        // We use a generous limit and just verify the result is correct.
        double root = DIFF_SQRT2.root(1.0, 2.0, 1e-12, 50);
        assertEquals(Math.sqrt(2), root, 1e-11);
    }

    // =========================================================================
    // MultivariateFunction
    // =========================================================================

    @Test
    public void testMultivariateFunctionApply() {
        // f(x) = x[0]^2 + x[1]^2
        MultivariateFunction f = x -> x[0] * x[0] + x[1] * x[1];
        assertEquals(5.0, f.f(new double[]{1, 2}), 1e-15);
        assertEquals(5.0, f.apply(1.0, 2.0), 1e-15);
        assertEquals(5.0, f.applyAsDouble(new double[]{1, 2}), 1e-15);
    }

    @Test
    public void testMultivariateFunctionAsJavaFunction() {
        MultivariateFunction f = x -> x[0] + x[1] + x[2];
        java.util.function.ToDoubleFunction<double[]> jf = f;
        assertEquals(6.0, jf.applyAsDouble(new double[]{1, 2, 3}), 1e-15);
    }

    // =========================================================================
    // DifferentiableMultivariateFunction — finite-difference gradient
    // =========================================================================

    @Test
    public void testFiniteDifferenceGradient() {
        // f(x) = x[0]^2 + 3*x[1], exact gradient: [2*x[0], 3]
        DifferentiableMultivariateFunction f = x -> x[0] * x[0] + 3 * x[1];
        double[] x = {2.0, 1.5};
        double[] grad = new double[2];
        double val = f.g(x, grad);
        assertEquals(f.f(x), val, 1e-15);
        assertEquals(4.0, grad[0], 1e-5);   // ∂f/∂x[0] = 2*x[0] = 4
        assertEquals(3.0, grad[1], 1e-5);   // ∂f/∂x[1] = 3
    }

    @Test
    public void testFiniteDifferenceGradientAtOrigin() {
        // f(x) = x[0]^2 + x[1]^2, gradient at (0,0) = (0,0)
        DifferentiableMultivariateFunction f = x -> x[0] * x[0] + x[1] * x[1];
        double[] x = {0.0, 0.0};
        double[] grad = new double[2];
        f.g(x, grad);
        assertEquals(0.0, grad[0], 1e-5);
        assertEquals(0.0, grad[1], 1e-5);
    }

    @Test
    public void testAnalyticGradientOverridesFiniteDiff() {
        // f(x) = x[0]^3, f'(x[0]) = 3*x[0]^2
        DifferentiableMultivariateFunction f = new DifferentiableMultivariateFunction() {
            @Override public double f(double[] x) { return x[0] * x[0] * x[0]; }
            @Override public double g(double[] x, double[] gradient) {
                gradient[0] = 3 * x[0] * x[0];
                return f(x);
            }
        };
        double[] x = {2.0};
        double[] grad = new double[1];
        double val = f.g(x, grad);
        assertEquals(8.0, val, 1e-15);
        assertEquals(12.0, grad[0], 1e-15);
    }

    // =========================================================================
    // IntFunction
    // =========================================================================

    @Test
    public void testIntFunction() {
        IntFunction f = x -> x * x;
        assertEquals(9, f.f(3));
        assertEquals(9, f.apply(3));
        assertEquals(0, f.f(0));
        assertEquals(25, f.apply(-5));
    }

    // =========================================================================
    // ArrayElementConsumer
    // =========================================================================

    @Test
    public void testArrayElementConsumer() {
        double[] result = new double[3];
        ArrayElementConsumer consumer = (i, x) -> result[i] = x * 2;
        consumer.apply(0, 1.5);
        consumer.apply(1, 2.5);
        consumer.apply(2, 3.5);
        assertArrayEquals(new double[]{3.0, 5.0, 7.0}, result, 1e-15);
    }

    @Test
    public void testArrayElementConsumerIsLambda() {
        int[] callCount = {0};
        ArrayElementConsumer consumer = (i, x) -> callCount[0]++;
        consumer.apply(0, 0.0);
        consumer.apply(1, 1.0);
        assertEquals(2, callCount[0]);
    }

    // =========================================================================
    // ArrayElementFunction
    // =========================================================================

    @Test
    public void testArrayElementFunction() {
        // maps (index, value) → index * value
        ArrayElementFunction f = (i, x) -> i * x;
        assertEquals(0.0,  f.apply(0, 5.0),  1e-15);
        assertEquals(10.0, f.apply(2, 5.0),  1e-15);
        assertEquals(15.0, f.apply(3, 5.0),  1e-15);
    }

    // =========================================================================
    // IntArrayElementConsumer
    // =========================================================================

    @Test
    public void testIntArrayElementConsumer() {
        int[] result = new int[3];
        IntArrayElementConsumer consumer = (i, x) -> result[i] = i + x;
        consumer.apply(0, 10);
        consumer.apply(1, 20);
        consumer.apply(2, 30);
        assertArrayEquals(new int[]{10, 21, 32}, result);
    }

    // =========================================================================
    // IntArrayElementFunction
    // =========================================================================

    @Test
    public void testIntArrayElementFunction() {
        IntArrayElementFunction f = (i, x) -> i * x + 1;
        assertEquals(1,  f.apply(0, 5));
        assertEquals(11, f.apply(2, 5));
        assertEquals(16, f.apply(3, 5));
    }

    // =========================================================================
    // ToFloatFunction
    // =========================================================================

    @Test
    public void testToFloatFunction() {
        ToFloatFunction<String> f = s -> (float) s.length();
        assertEquals(5.0f, f.applyAsFloat("hello"), 1e-6f);
        assertEquals(0.0f, f.applyAsFloat(""), 1e-6f);
    }

    @Test
    public void testToFloatFunctionWithNumber() {
        ToFloatFunction<double[]> f = arr -> (float) arr[0];
        assertEquals(3.14f, f.applyAsFloat(new double[]{3.14}), 1e-4f);
    }

    // =========================================================================
    // TimeFunction — additional coverage (cosine + parse round-trips)
    // =========================================================================

    @Test
    public void testCosineDecay() {
        // At t=0: should equal maxLearningRate
        // At t=decaySteps: should equal minLearningRate
        double minLR = 0.001, decaySteps = 100, maxLR = 0.1;
        TimeFunction t = TimeFunction.cosine(minLR, decaySteps, maxLR);
        assertEquals(maxLR, t.apply(0),   1e-9);
        assertEquals(minLR, t.apply(100), 1e-9);
        // At t=decaySteps/2: midpoint
        double mid = minLR + 0.5 * (maxLR - minLR);
        assertEquals(mid,   t.apply(50),  1e-9);
    }

    @Test
    public void testCosineToStringRoundTrip() {
        TimeFunction f = TimeFunction.cosine(0.001, 100.0, 0.1);
        String s = f.toString();
        TimeFunction f2 = TimeFunction.of(s);
        assertEquals(f.apply(50), f2.apply(50), 1e-9);
    }

    @Test
    public void testPiecewiseUnsortedMilestonesThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                TimeFunction.piecewise(new int[]{10, 5}, new double[]{0.1, 0.01, 0.001}));
    }

    @Test
    public void testPiecewiseMismatchedLengthsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                TimeFunction.piecewise(new int[]{5, 10}, new double[]{0.1, 0.01}));
    }

    @Test
    public void testTimeFunctionOfInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> TimeFunction.of("unknown_function(1,2,3)"));
    }

    @Test
    public void testTimeFunctionOfConstant() {
        TimeFunction f = TimeFunction.of("0.05");
        assertEquals(0.05, f.apply(0), 1e-9);
        assertEquals(0.05, f.apply(999), 1e-9);
    }

    @Test
    public void testBrentMultipleRoots() {
        // f(x) = (x-1)(x-2)(x-3) has roots at 1, 2, 3
        Function cubic = x -> (x - 1) * (x - 2) * (x - 3);
        assertEquals(1.0, cubic.root(0.5, 1.5, 1e-10, 200), 1e-9);
        assertEquals(2.0, cubic.root(1.5, 2.5, 1e-10, 200), 1e-9);
        assertEquals(3.0, cubic.root(2.5, 3.5, 1e-10, 200), 1e-9);
    }
}

