/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.feature;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.DataFrame;
import smile.data.WeatherNominal;
import smile.data.formula.Formula;
import static smile.data.formula.Terms.onehot;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class OneHotEncoderTest {
    
    public OneHotEncoderTest() {
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

        Formula formula = Formula.rhs(onehot());
        DataFrame df = formula.apply(WeatherNominal.data);
        System.out.println(df);

        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result[i].length; j++) {
                assertEquals(result[i][j], df.getByte(i, j));
            }
        }
    }
}
