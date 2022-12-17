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

package smile.ica;

import org.apache.commons.csv.CSVFormat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.CategoricalEncoder;
import smile.io.CSV;
import smile.math.MathEx;
import smile.test.data.SwissRoll;
import smile.util.Paths;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class ICATest {

    public ICATest() {
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
    public void test() throws Exception {
        System.out.println("ICA");

        try {
            CSVFormat format = CSVFormat.Builder.create().build();
            CSV csv = new CSV(format);
            double[][] data = csv.read(Paths.getTestData("ica/ica.csv")).toArray(false, CategoricalEncoder.DUMMY);

            ICA ica = ICA.fit(MathEx.transpose(data), 2);
            assertEquals(2, ica.components.length);
            assertEquals(data.length, ica.components[0].length);
            System.out.println(Arrays.toString(ica.components[0]));
            System.out.println(Arrays.toString(ica.components[1]));
        } catch (Exception ex) {
            System.err.println("Failed to load 'ica.csv': " + ex);
            System.exit(-1);
        }
    }
}
