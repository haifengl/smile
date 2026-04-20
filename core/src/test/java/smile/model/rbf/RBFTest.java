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
package smile.model.rbf;

import org.junit.jupiter.api.Test;
import smile.math.MathEx;
import smile.math.rbf.GaussianRadialBasis;
import smile.math.rbf.RadialBasisFunction;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted robustness tests for {@link RBF}.
 */
public class RBFTest {
    @Test
    public void testGivenMismatchedCentersAndBasisWhenCreatingRBFArrayThenThrowsMeaningfulException() {
        // Given
        Double[] centers = {0.0, 1.0};
        RadialBasisFunction[] basis = {new GaussianRadialBasis(1.0)};

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RBF.of(centers, basis, (x, y) -> Math.abs(x - y)));

        // Then
        assertTrue(exception.getMessage().contains("doesn't match"));
    }

    @Test
    public void testGivenInvalidNearestNeighborCountWhenFittingThenThrowsMeaningfulException() {
        // Given
        double[][] x = {
                {0.0},
                {1.0},
                {2.0}
        };

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RBF.fit(x, 2, 0));

        // Then
        assertTrue(exception.getMessage().contains("Invalid number of nearest neighbors"));
    }

    @Test
    public void testGivenInvalidScalingParameterWhenFittingThenThrowsMeaningfulException() {
        // Given
        double[][] x = {
                {0.0},
                {1.0},
                {2.0}
        };

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RBF.fit(x, 2, 0.0));

        // Then
        assertTrue(exception.getMessage().contains("Invalid scaling parameter"));
    }

    @Test
    public void givenNaNScalingParameter_whenFitting_thenThrowsIllegalArgumentException() {
        // NaN slipped through 'r <= 0' before the fix; now '!(r > 0)' catches it.
        double[][] x = {{0.0}, {1.0}, {2.0}, {3.0}};
        assertThrows(IllegalArgumentException.class,
                () -> RBF.fit(x, 2, Double.NaN));
    }

    @Test
    public void givenKnownCenterAndBasis_whenCallingF_thenReturnsGaussianEvaluation() {
        // Given: center={0}, RBF=Gaussian(scale=1), Euclidean distance
        // f({1}) = exp(-0.5 * 1^2 / 1^2) = exp(-0.5)
        RBF<double[]> neuron = new RBF<>(new double[]{0.0},
                new GaussianRadialBasis(1.0),
                MathEx::distance);

        // When
        double result = neuron.f(new double[]{1.0});

        // Then
        assertEquals(Math.exp(-0.5), result, 1E-10);
    }

    @Test
    public void givenCenterAtQueryPoint_whenCallingF_thenReturnsOne() {
        // Gaussian at distance 0 always equals 1 regardless of scale
        RBF<double[]> neuron = new RBF<>(new double[]{3.0, 4.0},
                new GaussianRadialBasis(2.0),
                MathEx::distance);

        assertEquals(1.0, neuron.f(new double[]{3.0, 4.0}), 1E-12);
    }

    @Test
    public void givenSharedBasis_whenCreatingNeuronsWithOf_thenAllNeuronsUseSameBasis() {
        // Given: two 1-D centers sharing one Gaussian basis
        Double[] centers = {0.0, 2.0};
        RadialBasisFunction basis = new GaussianRadialBasis(1.0);

        // When
        @SuppressWarnings("unchecked")
        RBF<Double>[] neurons = RBF.of(centers, basis, (a, b) -> Math.abs(a - b));

        // Then: neuron[0] at center 0 evaluated at x=0 → distance=0 → f=1
        assertEquals(1.0, neurons[0].f(0.0), 1E-12);
        // neuron[1] at center 2 evaluated at x=0 → distance=2 → exp(-0.5*4) = exp(-2)
        assertEquals(Math.exp(-2.0), neurons[1].f(0.0), 1E-10);
    }

    @Test
    public void givenPerNeuronBases_whenCreatingNeuronsWithOf_thenDifferentWidthsApplied() {
        // Given: two centers, each with a distinct Gaussian scale
        Double[] centers = {0.0, 0.0};  // both at origin
        RadialBasisFunction[] bases = {
                new GaussianRadialBasis(1.0),   // neuron 0: exp(-0.5 * d^2)
                new GaussianRadialBasis(2.0)    // neuron 1: exp(-0.5 * d^2 / 4)
        };

        @SuppressWarnings("unchecked")
        RBF<Double>[] neurons = RBF.of(centers, bases, (a, b) -> Math.abs(a - b));

        // At x=1: d=1
        // neuron 0: exp(-0.5 * 1/1)   = exp(-0.5)
        // neuron 1: exp(-0.5 * 1/4)   = exp(-0.125)
        assertEquals(Math.exp(-0.5),   neurons[0].f(1.0), 1E-10);
        assertEquals(Math.exp(-0.125), neurons[1].f(1.0), 1E-10);
        assertNotEquals(neurons[0].f(1.0), neurons[1].f(1.0), 1E-10);
    }

    @Test
    public void givenValidDataAndK_whenFittingWithKMeans_thenReturnsKNeurons() {
        // Given: well-separated 1-D points, k=3 clusters
        double[][] x = {{0.0}, {0.1}, {1.0}, {1.1}, {2.0}, {2.1}};
        int k = 3;

        // When
        RBF<double[]>[] neurons = RBF.fit(x, k);

        // Then: correct count, and every activation is in (0, 1]
        assertEquals(k, neurons.length);
        double[] query = {0.5};
        for (RBF<double[]> n : neurons) {
            double v = n.f(query);
            assertTrue(v > 0.0 && v <= 1.0,
                    "Gaussian output should be in (0,1] but was " + v);
        }
    }

    @Test
    public void givenValidP_whenFittingWithNearestNeighborHeuristic_thenReturnsKNeurons() {
        // Given: k=3 centers, p=2 (< k) is the valid boundary
        double[][] x = {{0.0}, {0.1}, {1.0}, {1.1}, {2.0}, {2.1}};

        // Valid p < k should succeed
        RBF<double[]>[] neurons = RBF.fit(x, 3, 2);
        assertEquals(3, neurons.length);
    }

    @Test
    public void givenPEqualsKMinus1_whenFitting_thenDoesNotThrow() {
        // p == k-1 is the largest valid value (since p >= k is rejected)
        double[][] x = {{0.0}, {0.1}, {1.0}, {1.1}, {2.0}, {2.1}};
        int k = 3;
        // p = k-1 = 2 is the boundary that should be accepted without exception
        assertDoesNotThrow(() -> RBF.fit(x, k, k - 1));
    }

    @Test
    public void givenPEqualsK_whenFitting_thenThrowsIllegalArgumentException() {
        double[][] x = {{0.0}, {1.0}, {2.0}, {3.0}};
        assertThrows(IllegalArgumentException.class, () -> RBF.fit(x, 3, 3));
    }

    @Test
    public void givenPositiveScalingFactor_whenFittingWithClusterWidth_thenReturnsKNeurons() {
        // Given: valid r > 0 should succeed
        double[][] x = {{0.0}, {0.1}, {1.0}, {1.1}, {2.0}, {2.1}};

        RBF<double[]>[] neurons = RBF.fit(x, 3, 1.0);
        assertEquals(3, neurons.length);
    }
}

