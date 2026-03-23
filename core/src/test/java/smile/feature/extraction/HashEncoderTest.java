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

    public HashEncoderTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testFeature() throws IOException {
        System.out.println("feature");
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

        System.out.println(x[0]);
        assertEquals(289, x[0].size());
        System.out.println(x[1999]);
        assertEquals(345, x[1999].size());
    }
}
