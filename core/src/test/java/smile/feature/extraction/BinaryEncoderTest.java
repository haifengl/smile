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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.extraction;

import smile.data.DataFrame;
import smile.datasets.WeatherNominal;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BinaryEncoderTest {
    
    public BinaryEncoderTest() {
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
    public void test() throws Exception {
        System.out.println("Binary Encoder");
        int[][] result = {
            {0, 3, 6, 9},
            {0, 3, 6, 8},
            {1, 3, 6, 9},
            {2, 4, 6, 9},
            {2, 5, 7, 9},
            {2, 5, 7, 8},
            {1, 5, 7, 8},
            {0, 4, 6, 9},
            {0, 5, 7, 9},
            {2, 4, 7, 9},
            {0, 4, 7, 8},
            {1, 4, 6, 8},
            {1, 3, 7, 9},
            {2, 4, 6, 8}
        };

        var weather = new WeatherNominal();
        DataFrame data = weather.data();
        BinaryEncoder encoder = new BinaryEncoder(data.schema(), "outlook", "temperature", "humidity", "windy");
        int[][] onehot = encoder.apply(data);

        for (int i = 0; i < data.size(); i++) {
            for (int j = 0; j < result[i].length; j++) {
                assertEquals(result[i][j], onehot[i][j]);
            }
        }
    }
}
