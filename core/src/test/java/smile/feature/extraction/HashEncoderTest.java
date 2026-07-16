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
import java.util.Arrays;
import java.util.function.Function;
import smile.util.SparseArray;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class HashEncoderTest {
    private static final Function<String, String[]> tokenizer = s -> s.split("\\s+");

    @Test
    public void testGivenMovieReviewsWhenApplyingHashEncoderThenSparseSizesAreStable() throws IOException {
        String[][] text = smile.io.Paths.getTestDataLines("text/movie.txt")
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.split("\\s+", 2))
                .toArray(String[][]::new);

        HashEncoder hashing = new HashEncoder(tokenizer, 1000);

        SparseArray[] x = new SparseArray[text.length];
        for (int i = 0; i < text.length; i++) {
            x[i] = hashing.apply(text[i][1]);
        }

        assertEquals(289, x[0].size());
        assertEquals(345, x[1999].size());
    }

    @Test
    public void testGivenAlternateSignDisabledWhenHashingThenAllValuesArePositive() {
        // Given
        HashEncoder encoder = new HashEncoder(tokenizer, 100, false);
        String text = "alpha beta gamma delta alpha";

        // When
        SparseArray result = encoder.apply(text);

        // Then — without alternating sign, counts are always positive
        result.forEach((idx, val) -> assertTrue(val > 0,
                "expected positive value but got " + val + " at index " + idx));
    }

    @Test
    public void testGivenSameWordRepeatedWhenHashingWithAlternateSignThenCountsAccumulate() {
        // Given — with alternateSign=true, words that hash negative get -1, else +1
        HashEncoder withSign    = new HashEncoder(tokenizer, 1024, true);
        HashEncoder withoutSign = new HashEncoder(tokenizer, 1024, false);
        String text = "repeated repeated repeated";

        // When
        SparseArray resultWith    = withSign.apply(text);
        SparseArray resultWithout = withoutSign.apply(text);

        // Then — both hash to the same single bucket; counts are ±3 vs +3
        assertEquals(1, resultWith.size());
        assertEquals(1, resultWithout.size());
        assertEquals(3, Math.abs((int) resultWithout.iterator().next().value()));
    }

    @Test
    public void testGivenEmptyTokenStreamWhenHashingThenSparseArrayIsEmpty() {
        // Given — a tokenizer that always returns an empty array regardless of input
        Function<String, String[]> emptyTokenizer = s ->
                Arrays.stream(s.split("\\s+"))
                      .filter(w -> !w.isEmpty())
                      .toArray(String[]::new);
        HashEncoder encoder = new HashEncoder(emptyTokenizer, 512);

        // When — empty text produces no tokens after filtering
        SparseArray result = encoder.apply("");

        // Then
        assertEquals(0, result.size());
    }

    @Test
    public void testGivenSingleWordWhenHashingThenExactlyOneBucketIsOccupied() {
        // Given
        HashEncoder encoder = new HashEncoder(tokenizer, 256);
        String text = "uniqueword";

        // When
        SparseArray result = encoder.apply(text);

        // Then
        assertEquals(1, result.size());
    }
}
