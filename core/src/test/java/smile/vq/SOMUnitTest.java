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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SOMUnitTest {
    @Test
    void givenSimpleLattice_whenComputingUMatrix_thenUsesEuclideanDistance() {
        // Given
        double[][][] lattice = {
                { {0.0, 0.0}, {0.0, 4.0} },
                { {3.0, 0.0}, {3.0, 4.0} }
        };
        SOM som = new SOM(lattice, TimeFunction.constant(0.1), Neighborhood.bubble(1));

        // When
        double[][] umatrix = som.umatrix();

        // Then
        assertEquals(4.0, umatrix[0][0], 1E-12);
        assertEquals(4.0, umatrix[0][1], 1E-12);
        assertEquals(4.0, umatrix[1][0], 1E-12);
        assertEquals(4.0, umatrix[1][1], 1E-12);
    }

    @Test
    void givenInvalidLattice_whenConstructingSom_thenThrowsIllegalArgumentException() {
        // Given
        double[][][] lattice = {
                { {0.0, 1.0}, {1.0} }
        };

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> new SOM(lattice, TimeFunction.constant(0.1), Neighborhood.bubble(1)));
    }

    @Test
    void givenSingleRowLattice_whenComputingUMatrix_thenHandlesWithoutException() {
        // Given: 1×3 lattice — previously caused ArrayIndexOutOfBoundsException
        double[][][] lattice = {
                { {0.0}, {3.0}, {7.0} }
        };
        SOM som = new SOM(lattice, TimeFunction.constant(0.1), Neighborhood.bubble(1));

        // When
        double[][] umatrix = som.umatrix();

        // Then: distances between adjacent neurons fill correctly
        assertEquals(3.0, umatrix[0][0], 1E-12); // dist(0→3)
        assertEquals(4.0, umatrix[0][1], 1E-12); // max(dist(0→3), dist(3→7)) = max(3,4)
        assertEquals(4.0, umatrix[0][2], 1E-12); // dist(3→7)
    }

    @Test
    void givenSingleColumnLattice_whenComputingUMatrix_thenHandlesWithoutException() {
        // Given: 3×1 lattice — previously caused ArrayIndexOutOfBoundsException
        double[][][] lattice = {
                { {0.0} },
                { {3.0} },
                { {7.0} }
        };
        SOM som = new SOM(lattice, TimeFunction.constant(0.1), Neighborhood.bubble(1));

        // When
        double[][] umatrix = som.umatrix();

        // Then: distances between adjacent neurons fill correctly
        assertEquals(3.0, umatrix[0][0], 1E-12); // dist(0→3)
        assertEquals(4.0, umatrix[1][0], 1E-12); // max(dist(0→3), dist(3→7)) = max(3,4)
        assertEquals(4.0, umatrix[2][0], 1E-12); // dist(3→7)
    }

    @Test
    void givenSingleNeuronLattice_whenComputingUMatrix_thenReturnsZero() {
        // Given: 1×1 lattice — no neighbors, so no distances
        double[][][] lattice = {
                { {1.0, 2.0} }
        };
        SOM som = new SOM(lattice, TimeFunction.constant(0.1), Neighborhood.bubble(1));

        // When
        double[][] umatrix = som.umatrix();

        // Then
        assertEquals(0.0, umatrix[0][0], 1E-12);
    }

    @Test
    void givenOneStepUpdate_whenQuantizing_thenReturnsBestMatchingUnit() {
        // Given
        double[][][] lattice = {
                { {0.0, 0.0}, {10.0, 10.0} }
        };
        SOM som = new SOM(lattice, TimeFunction.constant(0.5), Neighborhood.bubble(1));

        // When
        som.update(new double[] {0.0, 2.0});
        double[] quantized = som.quantize(new double[] {0.0, 1.0});

        // Then
        assertArrayEquals(new double[] {0.0, 1.0}, quantized, 1E-12);
    }
}

