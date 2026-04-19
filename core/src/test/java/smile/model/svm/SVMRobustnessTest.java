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
package smile.model.svm;

import org.junit.jupiter.api.Test;
import smile.math.kernel.LinearKernel;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Targeted robustness tests for SVM helpers in {@code smile.model.svm}.
 */
public class SVMRobustnessTest {
    @Test
    public void testGivenInvalidSVRHyperparametersWhenConstructingThenThrowsMeaningfulException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> new SVR<>(new LinearKernel(), 0.0, 1.0, 1E-3));
        assertThrows(IllegalArgumentException.class, () -> new SVR<>(new LinearKernel(), 0.1, -1.0, 1E-3));
        assertThrows(IllegalArgumentException.class, () -> new SVR<>(new LinearKernel(), 0.1, 1.0, 0.0));
    }

    @Test
    public void testGivenEmptySVRTrainingDataWhenFittingThenThrowsMeaningfulException() {
        // Given
        SVR<double[]> svr = new SVR<>(new LinearKernel(), 0.1, 1.0, 1E-3);

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> svr.fit(new double[0][], new double[0]));

        // Then
        assertTrue(exception.getMessage().contains("Empty training data"));
    }

    @Test
    public void testGivenMismatchedSVRTrainingDataWhenFittingThenThrowsMeaningfulException() {
        // Given
        SVR<double[]> svr = new SVR<>(new LinearKernel(), 0.1, 1.0, 1E-3);

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> svr.fit(new double[][] {{1.0}}, new double[] {1.0, 2.0}));

        // Then
        assertTrue(exception.getMessage().contains("don't match"));
    }

    @Test
    public void testGivenInvalidOCSVMHyperparametersWhenConstructingThenThrowsMeaningfulException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> new OCSVM<>(new LinearKernel(), 0.0, 1E-3));
        assertThrows(IllegalArgumentException.class, () -> new OCSVM<>(new LinearKernel(), 1.1, 1E-3));
        assertThrows(IllegalArgumentException.class, () -> new OCSVM<>(new LinearKernel(), 0.5, 0.0));
    }

    @Test
    public void testGivenEmptyOCSVMTrainingDataWhenFittingThenThrowsMeaningfulException() {
        // Given
        OCSVM<double[]> ocsvm = new OCSVM<>(new LinearKernel(), 0.5, 1E-3);

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ocsvm.fit(new double[0][]));

        // Then
        assertTrue(exception.getMessage().contains("Empty training data"));
    }
}

