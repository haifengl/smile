/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.feature;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.CategoricalEncoder;
import smile.data.WeatherNominal;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class DummyVariableTest {
    
    public DummyVariableTest() {
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
    public void testWeather() {
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

        double[][] data = WeatherNominal.data.toArray(false, CategoricalEncoder.ONE_HOT);
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result[i].length; j++) {
                assertEquals(result[i][j], data[i][j], 1E-10);
            }
        }
    }
}
