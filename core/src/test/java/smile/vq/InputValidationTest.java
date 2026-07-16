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

class InputValidationTest {
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

