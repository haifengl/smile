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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.DataFrame;
import smile.test.data.Weather;
import smile.util.SparseArray;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class SparseEncoderTest {

    public SparseEncoderTest() {
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
    public void test() {
        System.out.println("Sparse Encoder");

        DataFrame data = Weather.data;
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
}
