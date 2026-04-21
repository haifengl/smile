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

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.Read;
import smile.io.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BagOfWordsTest {
    private static final Function<String, String[]> tokenizer = s -> s.split("\\s+");

    @Test
    public void testGivenDuplicateFeaturesWhenCreatingBagOfWordsThenInputIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> {
            String[] features = {"crane", "sparrow", "hawk", "owl", "kiwi", "kiwi"};
            new BagOfWords(tokenizer, features);
        });
        assertEquals("Duplicated word:kiwi", error.getMessage());
    }

    @Test
    public void testGivenMovieReviewsWhenApplyingBagOfWordsThenConfiguredFeatureVectorIsProduced() throws IOException {
        String[][] text = smile.io.Paths.getTestDataLines("text/movie.txt")
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.split("\\s+", 2))
                .toArray(String[][]::new);

        String[] feature = {
            "outstanding", "wonderfully", "wasted", "lame", "awful", "poorly",
            "ridiculous", "waste", "worst", "bland", "unfunny", "stupid", "dull",
            "fantastic", "laughable", "mess", "pointless", "terrific", "memorable",
            "superb", "boring", "badly", "subtle", "terrible", "excellent",
            "perfectly", "masterpiece", "realistic", "flaws"
        };

        BagOfWords bag = new BagOfWords(tokenizer, feature);
        
        int[][] x = new int[text.length][];
        for (int i = 0; i < text.length; i++) {
            x[i] = bag.apply(text[i][1]);
            assertEquals(feature.length, x[i].length);
        }
        
        assertEquals(1, x[0][15]);
    }

    @Test
    public void testGivenArffTextColumnWhenFittingBagOfWordsThenTopFeaturesAndFrameShapeAreStable() throws Exception {
        DataFrame data = Read.arff(Paths.getTestData("weka/string.arff"));
        BagOfWords bag = BagOfWords.fit(data, tokenizer, 10, "LCSH");
        DataFrame df = bag.apply(data);

        assertEquals(data.size(), df.size());
        assertEquals(10, df.ncol());
        assertEquals(10, bag.features().length);
        assertEquals("--", bag.features()[0]);
        assertEquals("Union", bag.features()[9]);
    }

    @Test
    public void testGivenBinaryModeWhenApplyingBagOfWordsThenFrequenciesAreClampedToOne() {
        // Given — "good" appears three times in text
        String[] features = {"good", "bad", "ugly"};
        BagOfWords countBag  = new BagOfWords(null, tokenizer, features, false);
        BagOfWords binaryBag = new BagOfWords(null, tokenizer, features, true);
        String text = "good good good bad";

        // When
        int[] countVec  = countBag.apply(text);
        int[] binaryVec = binaryBag.apply(text);

        // Then
        assertEquals(3, countVec[0]);
        assertEquals(1, countVec[1]);
        assertEquals(0, countVec[2]);

        assertEquals(1, binaryVec[0]);
        assertEquals(1, binaryVec[1]);
        assertEquals(0, binaryVec[2]);
    }

    @Test
    public void testGivenDataFrameColumnWhenApplyingBagOfWordsTupleOverloadThenOutputMatchesStringOverload() {
        // Given
        String[] features = {"hello", "world", "foo"};
        StructType schema = new StructType(new StructField("text", DataTypes.StringType));
        BagOfWords bag = new BagOfWords(new String[]{"text"}, tokenizer, features, false);

        Tuple tuple = Tuple.of(schema, new Object[]{"hello world hello"});

        // When
        Tuple result = bag.apply(tuple);

        // Then
        assertEquals(2, result.getInt(0));
        assertEquals(1, result.getInt(1));
        assertEquals(0, result.getInt(2));
    }

    @Test
    public void testGivenDataFrameWhenApplyingBagOfWordsThenOutputShapeMatchesInput() {
        // Given
        String[] features = {"hello", "world"};
        StructType schema = new StructType(new StructField("text", DataTypes.StringType));
        BagOfWords bag = new BagOfWords(new String[]{"text"}, tokenizer, features, false);

        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{"hello hello"}),
                Tuple.of(schema, new Object[]{"world foo"})
        ));

        // When
        DataFrame result = bag.apply(data);

        // Then
        assertEquals(2, result.nrow());
        assertEquals(2, result.ncol());
        assertEquals(2, result.getInt(0, 0));
        assertEquals(0, result.getInt(0, 1));
        assertEquals(0, result.getInt(1, 0));
        assertEquals(1, result.getInt(1, 1));
    }

    @Test
    public void testGivenNoColumnsConstructorWhenApplyingToTupleThenUnsupportedOperationIsThrown() {
        // Given — constructor without columns, intended for String-only usage
        String[] features = {"hello", "world"};
        BagOfWords bag = new BagOfWords(tokenizer, features);
        StructType schema = new StructType(new StructField("text", DataTypes.StringType));
        Tuple tuple = Tuple.of(schema, new Object[]{"hello"});

        // When / Then
        assertThrows(UnsupportedOperationException.class, () -> bag.apply(tuple));
    }
}
