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
package smile.feature.selection;

import smile.data.transform.ColumnTransform;
import smile.datasets.BreastCancer;
import smile.datasets.Default;
import smile.datasets.Weather;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class InformationValueTest {

    public InformationValueTest() {
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
    public void testDefault() throws Exception {
        System.out.println("Default");

        var dataset = new Default();
        InformationValue[] iv = InformationValue.fit(dataset.data(), "default");
        System.out.println(InformationValue.toString(iv));

        assertEquals(3, iv.length);
        assertEquals(0.0364, iv[0].iv(), 1E-4);
        assertEquals(4.2638, iv[1].iv(), 1E-4);
        assertEquals(0.0664, iv[2].iv(), 1E-4);

        ColumnTransform transform = InformationValue.toTransform(iv);
        System.out.println(transform.apply(dataset.data()));
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("BreastCancer");
        var cancer = new BreastCancer();
        InformationValue[] iv = InformationValue.fit(cancer.data(), "diagnosis");
        System.out.println(InformationValue.toString(iv));

        assertEquals(30, iv.length);
        assertEquals(0.2425, iv[ 9].iv(), 1E-4);
        assertEquals(0.1002, iv[11].iv(), 1E-4);
        assertEquals(0.0817, iv[14].iv(), 1E-4);
    }

    @Test
    public void testWeather() throws Exception {
        System.out.println("Weather");
        var weather = new Weather();
        InformationValue[] iv = InformationValue.fit(weather.data(), "play");
        System.out.println(InformationValue.toString(iv));

        assertEquals(4, iv.length);
        assertEquals(0.9012, iv[0].iv(), 1E-4);
        assertEquals(0.6291, iv[1].iv(), 1E-4);
        assertEquals(0.6291, iv[2].iv(), 1E-4);
        assertEquals(0.2930, iv[3].iv(), 1E-4);

        ColumnTransform transform = InformationValue.toTransform(iv);
        System.out.println(transform.apply(weather.data()));
    }
}
