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
package smile.validation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ClassificationMetricsTest {
    @Test
    public void givenValidBinaryPosterior_whenComputingMetrics_thenReturnExpectedValues() {
        int[] truth = {0, 1, 1, 0};
        int[] prediction = {0, 1, 0, 0};
        double[][] posteriori = {
                {0.9, 0.1},
                {0.2, 0.8},
                {0.7, 0.3},
                {0.6, 0.4}
        };

        ClassificationMetrics metrics = ClassificationMetrics.of(1.0, 2.0, truth, prediction, posteriori);
        assertEquals(4, metrics.size());
        assertEquals(1, metrics.error());
        assertEquals(0.75, metrics.accuracy(), 1E-12);
        assertFalse(Double.isNaN(metrics.auc()));
    }

    @Test
    public void givenMismatchedPosteriorRows_whenComputingMetrics_thenThrowIllegalArgumentException() {
        int[] truth = {0, 1};
        int[] prediction = {0, 1};
        double[][] posteriori = {{0.4, 0.6}};

        assertThrows(IllegalArgumentException.class,
                () -> ClassificationMetrics.of(0.0, 0.0, truth, prediction, posteriori));
    }

    @Test
    public void givenEmptyPosterior_whenComputingMetrics_thenThrowIllegalArgumentException() {
        int[] truth = new int[0];
        int[] prediction = new int[0];

        assertThrows(IllegalArgumentException.class,
                () -> ClassificationMetrics.of(0.0, 0.0, truth, prediction, new double[0][]));
    }

    @Test
    public void givenSingleClassPosterior_whenComputingMetrics_thenThrowIllegalArgumentException() {
        int[] truth = {0, 0};
        int[] prediction = {0, 0};
        double[][] posteriori = {{1.0}, {1.0}};

        assertThrows(IllegalArgumentException.class,
                () -> ClassificationMetrics.of(0.0, 0.0, truth, prediction, posteriori));
    }

    @Test
    public void givenInconsistentPosteriorDimensions_whenComputingMetrics_thenThrowIllegalArgumentException() {
        int[] truth = {0, 1};
        int[] prediction = {0, 1};
        double[][] posteriori = {
                {0.4, 0.6},
                {0.2, 0.5, 0.3}
        };

        assertThrows(IllegalArgumentException.class,
                () -> ClassificationMetrics.of(0.0, 0.0, truth, prediction, posteriori));
    }
}

