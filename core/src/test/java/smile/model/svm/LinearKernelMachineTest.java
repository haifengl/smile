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
import smile.math.kernel.BinarySparseLinearKernel;
import smile.math.kernel.LinearKernel;
import smile.math.kernel.SparseLinearKernel;
import smile.util.SparseArray;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link LinearKernelMachine}.
 */
public class LinearKernelMachineTest {
    @Test
    public void testGivenDenseLinearKernelMachineWhenConvertedThenWeightsAndScoresArePreserved() {
        // Given
        KernelMachine<double[]> kernelMachine = new KernelMachine<>(
                new LinearKernel(),
                new double[][] {
                        {1.0, 2.0},
                        {-1.0, 1.0}
                },
                new double[] {2.0, -0.5},
                1.5
        );
        double[] x = {3.0, -2.0};

        // When
        LinearKernelMachine linear = LinearKernelMachine.of(kernelMachine);

        // Then
        assertArrayEquals(new double[] {2.5, 3.5}, linear.weights(), 1E-12);
        assertEquals(kernelMachine.score(x), linear.f(x), 1E-12);
        assertEquals(1.5, linear.intercept(), 1E-12);
    }

    @Test
    public void testGivenBinarySparseKernelMachineWhenConvertedThenWeightsAndScoresArePreserved() {
        // Given
        KernelMachine<int[]> kernelMachine = new KernelMachine<>(
                new BinarySparseLinearKernel(),
                new int[][] {
                        {0, 2},
                        {1}
                },
                new double[] {2.0, -1.0},
                0.5
        );
        int[] x = {0, 1, 2};

        // When
        LinearKernelMachine linear = LinearKernelMachine.binary(3, kernelMachine);

        // Then
        assertArrayEquals(new double[] {2.0, -1.0, 2.0}, linear.weights(), 1E-12);
        assertEquals(kernelMachine.score(x), linear.f(x), 1E-12);
        assertEquals(0.5, linear.intercept(), 1E-12);
    }

    @Test
    public void testGivenSparseKernelMachineWhenConvertedThenWeightsAndScoresArePreserved() {
        // Given
        SparseArray first = new SparseArray();
        first.set(0, 1.5);
        first.set(2, -2.0);

        SparseArray second = new SparseArray();
        second.set(1, 3.0);

        KernelMachine<SparseArray> kernelMachine = new KernelMachine<>(
                new SparseLinearKernel(),
                new SparseArray[] {first, second},
                new double[] {2.0, -0.5},
                -1.0
        );

        SparseArray x = new SparseArray();
        x.set(0, 2.0);
        x.set(1, 1.0);
        x.set(2, -1.0);

        // When
        LinearKernelMachine linear = LinearKernelMachine.sparse(3, kernelMachine);

        // Then
        assertArrayEquals(new double[] {3.0, -1.5, -4.0}, linear.weights(), 1E-12);
        assertEquals(kernelMachine.score(x), linear.f(x), 1E-12);
        assertEquals(-1.0, linear.intercept(), 1E-12);
    }
}

