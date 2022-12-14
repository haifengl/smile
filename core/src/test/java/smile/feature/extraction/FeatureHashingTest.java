/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.feature.extraction;

import java.io.IOException;
import java.util.function.Function;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.util.SparseArray;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class FeatureHashingTest {
    private static final Function<String, String[]> tokenizer = s -> s.split("\\s+");

    public FeatureHashingTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFeature() throws IOException {
        System.out.println("feature");
        String[][] text = smile.util.Paths.getTestDataLines("text/movie.txt")
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.split("\\s+", 2))
                .toArray(String[][]::new);

        FeatureHashing hashing = new FeatureHashing(tokenizer, 1000);

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
