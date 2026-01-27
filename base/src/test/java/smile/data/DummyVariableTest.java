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
package smile.data;

import smile.datasets.WeatherNominal;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class DummyVariableTest {
    
    public DummyVariableTest() {
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
    public void testWeather() throws Exception {
        System.out.println("weather");
        byte[][] result = {
            {1, 0, 0, 1, 0, 0, 1, 0, 0, 1},
            {1, 0, 0, 1, 0, 0, 1, 0, 1, 0},
            {0, 1, 0, 1, 0, 0, 1, 0, 0, 1},
            {0, 0, 1, 0, 1, 0, 1, 0, 0, 1},
            {0, 0, 1, 0, 0, 1, 0, 1, 0, 1},
            {0, 0, 1, 0, 0, 1, 0, 1, 1, 0},
            {0, 1, 0, 0, 0, 1, 0, 1, 1, 0},
            {1, 0, 0, 0, 1, 0, 1, 0, 0, 1},
            {1, 0, 0, 0, 0, 1, 0, 1, 0, 1},
            {0, 0, 1, 0, 1, 0, 0, 1, 0, 1},
            {1, 0, 0, 0, 1, 0, 0, 1, 1, 0},
            {0, 1, 0, 0, 1, 0, 1, 0, 1, 0},
            {0, 1, 0, 1, 0, 0, 0, 1, 0, 1},
            {0, 0, 1, 0, 1, 0, 1, 0, 1, 0}
        };

        var weather = new WeatherNominal();
        double[][] data = weather.onehot();
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result[i].length; j++) {
                assertEquals(result[i][j], data[i][j], 1E-10);
            }
        }
    }
}
