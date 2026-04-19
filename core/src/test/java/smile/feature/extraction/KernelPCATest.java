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
package smile.feature.extraction;

import java.util.List;
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.manifold.KPCA;
import smile.math.kernel.LinearKernel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Wrapper tests for {@link KernelPCA}.
 */
public class KernelPCATest {
    private static final double TOLERANCE = 1E-10;

    @Test
    public void testGivenSelectedColumnsWhenApplyingKernelPCAThenWrapperMatchesUnderlyingKpcaCoordinates() {
        // Given
        StructType schema = new StructType(
                new StructField("noise", DataTypes.DoubleType),
                new StructField("x1", DataTypes.DoubleType),
                new StructField("x2", DataTypes.DoubleType)
        );
        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[] {9.0, 1.0, 2.0}),
                Tuple.of(schema, new Object[] {8.0, 2.0, 1.0}),
                Tuple.of(schema, new Object[] {7.0, -1.0, -2.0}),
                Tuple.of(schema, new Object[] {6.0, -2.0, -1.0})
        ));
        KernelPCA projection = KernelPCA.fit(data, new LinearKernel(), new KPCA.Options(2), "x1", "x2");

        // When
        double[][] expected = projection.kpca.coordinates();
        DataFrame transformed = projection.apply(data);
        Tuple firstTuple = projection.apply(data.get(0));

        // Then
        assertEquals(2, transformed.ncol());
        assertEquals(4, transformed.nrow());
        assertEquals("KPCA1", transformed.schema().field(0).name());
        assertEquals("KPCA2", transformed.schema().field(1).name());

        for (int i = 0; i < expected.length; i++) {
            for (int j = 0; j < expected[i].length; j++) {
                assertEquals(expected[i][j], transformed.getDouble(i, j), TOLERANCE);
            }
        }

        assertEquals(expected[0][0], firstTuple.getDouble(0), TOLERANCE);
        assertEquals(expected[0][1], firstTuple.getDouble(1), TOLERANCE);
    }

    @Test
    public void testGivenProjectionDimensionLargerThanSampleCountWhenFittingKernelPcaThenInputIsRejected() {
        // Given
        DataFrame data = DataFrame.of(new double[][] {
                {1.0, 2.0},
                {2.0, 1.0},
                {-1.0, -2.0},
                {-2.0, -1.0}
        }, "x1", "x2");

        // When / Then
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> KernelPCA.fit(data, new LinearKernel(), new KPCA.Options(5), "x1", "x2"));
        assertEquals("Invalid dimension of feature space: 5", error.getMessage());
    }
}

