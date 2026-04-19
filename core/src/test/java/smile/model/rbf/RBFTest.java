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
import smile.math.rbf.GaussianRadialBasis;
import smile.math.rbf.RadialBasisFunction;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}

