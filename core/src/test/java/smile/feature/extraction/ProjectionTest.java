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
import smile.tensor.DenseMatrix;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct tests for {@link Projection}.
 */
public class ProjectionTest {
    private static final double TOLERANCE = 1E-12;

    @Test
    public void testGivenProjectionWhenApplyingArraysTuplesAndFramesThenSelectedColumnsAndSchemaAreStable() {
        // Given
        Projection projection = new Projection(
                DenseMatrix.of(new double[][] {
                        {1.0, -1.0, 0.5},
                        {0.0, 2.0, -1.0}
                }),
                "Proj",
                "a", "c", "b"
        );
        StructType schema = new StructType(
                new StructField("a", DataTypes.DoubleType),
                new StructField("b", DataTypes.DoubleType),
                new StructField("c", DataTypes.DoubleType),
                new StructField("tag", DataTypes.StringType)
        );
        Tuple tuple = Tuple.of(schema, new Object[] {2.0, 5.0, 3.0, "x"});
        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[] {2.0, 5.0, 3.0, "x"}),
                Tuple.of(schema, new Object[] {4.0, 1.0, -2.0, "y"})
        ));

        // When
        double[] vector = projection.apply(new double[] {2.0, 3.0, 5.0});
        Tuple transformedTuple = projection.apply(tuple);
        DataFrame transformedFrame = projection.apply(data);

        // Then
        assertEquals("Proj1", projection.schema.field(0).name());
        assertEquals("Proj2", projection.schema.field(1).name());

        assertEquals(1.5, vector[0], TOLERANCE);
        assertEquals(1.0, vector[1], TOLERANCE);

        assertEquals(1.5, transformedTuple.getDouble(0), TOLERANCE);
        assertEquals(1.0, transformedTuple.getDouble(1), TOLERANCE);
        assertEquals("Proj1", transformedTuple.schema().field(0).name());
        assertEquals("Proj2", transformedTuple.schema().field(1).name());

        assertEquals(1.5, transformedFrame.getDouble(0, 0), TOLERANCE);
        assertEquals(1.0, transformedFrame.getDouble(0, 1), TOLERANCE);
        assertEquals(6.5, transformedFrame.getDouble(1, 0), TOLERANCE);
        assertEquals(-5.0, transformedFrame.getDouble(1, 1), TOLERANCE);
        assertEquals(2, transformedFrame.ncol());
        assertEquals(2, transformedFrame.nrow());
    }
}

