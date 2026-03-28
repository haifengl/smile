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
package smile.feature.selection;

import java.util.Arrays;
import smile.classification.LDA;
import smile.datasets.Iris;
import smile.datasets.USPS;
import smile.validation.metric.Accuracy;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SumSquaresRatioTest {
    
    public SumSquaresRatioTest() {
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
    public void testIris() throws Exception {
        System.out.println("Iris");
        var iris = new Iris();
        SumSquaresRatio[] ssr = SumSquaresRatio.fit(iris.data(), "class");
        assertEquals(4, ssr.length);
        assertEquals( 1.6226463, ssr[0].ratio(), 1E-6);
        assertEquals( 0.6444144, ssr[1].ratio(), 1E-6);
        assertEquals(16.0412833, ssr[2].ratio(), 1E-6);
        assertEquals(13.0520327, ssr[3].ratio(), 1E-6);
    }

    @Test
    public void tesUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        SumSquaresRatio[] score = SumSquaresRatio.fit(usps.train(), "class");
        Arrays.sort(score);
        String[] columns = Arrays.stream(score).limit(121).map(SumSquaresRatio::feature).toArray(String[]::new);

        double[][] train = usps.formula().x(usps.train().drop(columns)).toArray();
        LDA lda = LDA.fit(train, usps.y());

        double[][] test = usps.formula().x(usps.test().drop(columns)).toArray();
        int[] prediction = lda.predict(test);

        double accuracy = new Accuracy().score(usps.testy(), prediction);
        System.out.format("SSR %.2f%%%n", 100 * accuracy);
        assertEquals(0.86, accuracy, 1E-2);
    }
}
