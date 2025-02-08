/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.ica;

import org.apache.commons.csv.CSVFormat;
import smile.data.CategoricalEncoder;
import smile.io.CSV;
import smile.math.MathEx;
import smile.io.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ICATest {

    public ICATest() {

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
        System.out.println("ICA");
        MathEx.setSeed(19650218); // to get repeatable results.

        CSVFormat format = CSVFormat.Builder.create().get();
        CSV csv = new CSV(format);
        double[][] data = csv.read(Paths.getTestData("ica/ica.csv")).toArray(false, CategoricalEncoder.DUMMY);

        ICA ica = ICA.fit(MathEx.transpose(data), 2);
        var components = ica.components();
        assertEquals(2, components.length);
        assertEquals(data.length, components[0].length);
        assertEquals( 0.02003, components[0][0], 1E-5);
        assertEquals(-0.03275, components[1][0], 1E-5);
        assertEquals(-0.01140, components[0][1], 1E-5);
        assertEquals(-0.01084, components[1][1], 1E-5);
    }
}
