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
package smile.vq;

import org.junit.jupiter.api.Test;
import smile.util.function.TimeFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VQValidationTest {
    @Test
    void givenInvalidBirchParameters_whenConstructing_thenThrowsIllegalArgumentException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> new BIRCH(0, 5, 5, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BIRCH(2, 1, 5, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BIRCH(2, 5, 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BIRCH(2, 5, 5, 0.0));
    }

    @Test
    void givenEmptyBirchModel_whenQuantizeOrCentroids_thenThrowsIllegalStateException() {
        // Given
        BIRCH birch = new BIRCH(2, 5, 5, 1.0);

        // When / Then
        assertThrows(IllegalStateException.class, () -> birch.quantize(new double[] {0.0, 0.0}));
        assertThrows(IllegalStateException.class, birch::centroids);
    }

    @Test
    void givenInvalidNeuralGasParameters_whenConstructing_thenThrowsIllegalArgumentException() {
        // Given
        double[][] oneNeuron = {{0.0, 0.0}};
        double[][] badDimensions = {{0.0, 0.0}, {1.0}};

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralGas(oneNeuron, TimeFunction.constant(0.1), TimeFunction.constant(1.0), TimeFunction.constant(10.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralGas(badDimensions, TimeFunction.constant(0.1), TimeFunction.constant(1.0), TimeFunction.constant(10.0)));
    }

    @Test
    void givenInvalidGrowingNeuralGasParameters_whenConstructing_thenThrowsIllegalArgumentException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class,
                () -> new GrowingNeuralGas(0));
        assertThrows(IllegalArgumentException.class,
                () -> new GrowingNeuralGas(2, 0.2, 0.006, 0, 100, 0.5, 0.995));
        assertThrows(IllegalArgumentException.class,
                () -> new GrowingNeuralGas(2, 0.2, 0.006, 50, 0, 0.5, 0.995));
    }

    @Test
    void givenEmptyGrowingNeuralGasModel_whenQuantizing_thenThrowsIllegalStateException() {
        // Given
        GrowingNeuralGas gng = new GrowingNeuralGas(2);

        // When / Then
        assertThrows(IllegalStateException.class, () -> gng.quantize(new double[] {0.0, 0.0}));
    }

    @Test
    void givenInvalidNeuralMapParameters_whenConstructingOrClearing_thenThrowsIllegalArgumentException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralMap(0, 1.0, 0.01, 0.002, 50, 0.995));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralMap(2, 1.0, 0.01, 0.002, 0, 0.995));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralMap(2, 1.0, 0.01, 0.002, 50, 0.0));

        NeuralMap map = new NeuralMap(2, 1.0, 0.01, 0.002, 50, 0.995);
        assertThrows(IllegalArgumentException.class, () -> map.clear(-1E-7));
    }

    @Test
    void givenEmptyNeuralMapModel_whenQuantizing_thenThrowsIllegalStateException() {
        // Given
        NeuralMap map = new NeuralMap(2, 1.0, 0.01, 0.002, 50, 0.995);

        // When / Then
        assertThrows(IllegalStateException.class, () -> map.quantize(new double[] {0.0, 0.0}));
    }

    @Test
    void givenSimpleBirchModel_whenGettingCentroids_thenReturnsLeafCentroids() {
        // Given
        BIRCH birch = new BIRCH(2, 3, 3, 0.5);
        birch.update(new double[] {0.0, 0.0});
        birch.update(new double[] {0.1, 0.1});
        birch.update(new double[] {5.0, 5.0});

        // When
        double[][] centroids = birch.centroids();

        // Then
        assertEquals(2, centroids.length);
    }

    @Test
    void givenBirchModel_whenInputDimensionMismatch_thenThrowsIllegalArgumentException() {
        // Given
        BIRCH birch = new BIRCH(2, 3, 3, 0.5);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> birch.update(new double[] {1.0}));

        birch.update(new double[] {0.0, 0.0});
        assertThrows(IllegalArgumentException.class, () -> birch.quantize(new double[] {1.0}));
    }

    @Test
    void givenSomModel_whenInputDimensionMismatch_thenThrowsIllegalArgumentException() {
        // Given
        SOM som = new SOM(
                new double[][][] {{{0.0, 0.0}, {1.0, 1.0}}},
                TimeFunction.constant(0.1),
                Neighborhood.bubble(1)
        );

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> som.update(new double[] {1.0}));
        assertThrows(IllegalArgumentException.class, () -> som.quantize(new double[] {1.0}));
    }

    @Test
    void givenNeuralGasModel_whenInputDimensionMismatch_thenThrowsIllegalArgumentException() {
        // Given
        NeuralGas gas = new NeuralGas(
                new double[][] {{0.0, 0.0}, {1.0, 1.0}},
                TimeFunction.constant(0.1),
                TimeFunction.constant(1.0),
                TimeFunction.constant(10.0)
        );

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> gas.update(new double[] {1.0}));
        assertThrows(IllegalArgumentException.class, () -> gas.quantize(new double[] {1.0}));
    }

    @Test
    void givenGrowingNeuralGasModel_whenInputDimensionMismatch_thenThrowsIllegalArgumentException() {
        // Given
        GrowingNeuralGas gng = new GrowingNeuralGas(2);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> gng.update(new double[] {1.0}));

        gng.update(new double[] {0.0, 0.0});
        gng.update(new double[] {1.0, 1.0});
        assertThrows(IllegalArgumentException.class, () -> gng.quantize(new double[] {1.0}));
    }

    @Test
    void givenNeuralMapModel_whenInputDimensionMismatch_thenThrowsIllegalArgumentException() {
        // Given
        NeuralMap map = new NeuralMap(2, 1.0, 0.01, 0.002, 50, 0.995);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> map.update(new double[] {1.0}));

        map.update(new double[] {1.0, 1.0});
        assertThrows(IllegalArgumentException.class, () -> map.quantize(new double[] {1.0}));
    }

    @Test
    void givenVQModels_whenDimensionMismatch_thenErrorMessageUsesExpectedActualFormat() {
        // Given
        String expected = "Invalid input dimension: expected 2, actual 1";

        BIRCH birch = new BIRCH(2, 3, 3, 0.5);
        SOM som = new SOM(
                new double[][][] {{{0.0, 0.0}, {1.0, 1.0}}},
                TimeFunction.constant(0.1),
                Neighborhood.bubble(1)
        );
        NeuralGas gas = new NeuralGas(
                new double[][] {{0.0, 0.0}, {1.0, 1.0}},
                TimeFunction.constant(0.1),
                TimeFunction.constant(1.0),
                TimeFunction.constant(10.0)
        );
        GrowingNeuralGas gng = new GrowingNeuralGas(2);
        NeuralMap map = new NeuralMap(2, 1.0, 0.01, 0.002, 50, 0.995);

        // Train/initialize models that require state.
        birch.update(new double[] {0.0, 0.0});
        gng.update(new double[] {0.0, 0.0});
        gng.update(new double[] {1.0, 1.0});
        map.update(new double[] {0.0, 0.0});

        // When
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, () -> birch.quantize(new double[] {1.0}));
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> som.quantize(new double[] {1.0}));
        IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class, () -> gas.quantize(new double[] {1.0}));
        IllegalArgumentException e4 = assertThrows(IllegalArgumentException.class, () -> gng.quantize(new double[] {1.0}));
        IllegalArgumentException e5 = assertThrows(IllegalArgumentException.class, () -> map.quantize(new double[] {1.0}));

        // Then
        assertEquals(expected, e1.getMessage());
        assertEquals(expected, e2.getMessage());
        assertEquals(expected, e3.getMessage());
        assertEquals(expected, e4.getMessage());
        assertEquals(expected, e5.getMessage());
    }
}

