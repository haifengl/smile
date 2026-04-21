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
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.datasets.Weather;
import smile.util.SparseArray;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SparseEncoderTest {
    @Test
    public void testGivenWeatherDataWhenApplyingSparseEncoderThenEncodedSlotsAreStable() throws Exception {
        var weather = new Weather();
        DataFrame data = weather.data();
        SparseEncoder encoder = new SparseEncoder(data.schema(), "outlook", "temperature", "humidity", "windy");
        SparseArray[] features = encoder.apply(data);

        assertEquals(data.size(), features.length);
        for (int i = 0; i < data.size(); i++) {
            assertEquals(4, features[i].size());
        }

        assertEquals( 1, features[0].get(0), 1E-7);
        assertEquals(85, features[0].get(3), 1E-7);
        assertEquals(85, features[0].get(4), 1E-7);
        assertEquals( 1, features[0].get(6), 1E-7);

        assertEquals( 1, features[13].get(2), 1E-7);
        assertEquals(71, features[13].get(3), 1E-7);
        assertEquals(91, features[13].get(4), 1E-7);
        assertEquals( 1, features[13].get(5), 1E-7);
    }

    @Test
    public void testGivenZeroNumericValueWhenEncodingThenZeroIsOmittedFromSparseArray() {
        // Given — a row where one numeric value is exactly 0.0
        StructType schema = new StructType(
                new StructField("a", DataTypes.DoubleType),
                new StructField("b", DataTypes.DoubleType)
        );
        SparseEncoder encoder = new SparseEncoder(schema, "a", "b");

        Tuple row = Tuple.of(schema, new Object[]{0.0, 3.5});

        // When
        SparseArray sparse = encoder.apply(row);

        // Then — zero value for 'a' (index 0) is omitted; 'b' (index 1) is present
        assertEquals(1, sparse.size());
        assertEquals(3.5, sparse.get(1), 1E-12);
    }

    @Test
    public void testGivenAllZeroNumericRowWhenEncodingThenSparseArrayIsEmpty() {
        // Given
        StructType schema = new StructType(
                new StructField("x", DataTypes.DoubleType),
                new StructField("y", DataTypes.DoubleType)
        );
        SparseEncoder encoder = new SparseEncoder(schema, "x", "y");
        Tuple row = Tuple.of(schema, new Object[]{0.0, 0.0});

        // When
        SparseArray sparse = encoder.apply(row);

        // Then
        assertEquals(0, sparse.size());
    }

    @Test
    public void testGivenUnsupportedColumnTypeWhenCreatingSparseEncoderThenExceptionIsThrown() {
        // Given — a schema with a non-numeric, non-categorical String column
        StructType schema = new StructType(
                new StructField("text", DataTypes.StringType)
        );

        // When / Then — constructor should reject
        assertThrows(IllegalArgumentException.class,
                () -> new SparseEncoder(schema, "text"));
    }

    @Test
    public void testGivenAutoDetectionWhenCreatingSparseEncoderThenNumericColumnsAreUsed() {
        // Given
        StructType schema = new StructType(
                new StructField("a", DataTypes.DoubleType),
                new StructField("b", DataTypes.DoubleType)
        );
        SparseEncoder encoder = new SparseEncoder(schema); // no columns arg

        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{1.0, 2.0})
        ));

        // When
        SparseArray[] results = encoder.apply(data);

        // Then
        assertEquals(1, results.length);
        assertEquals(2, results[0].size()); // both non-zero values present
    }
}
