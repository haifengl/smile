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

package smile.feature.selection;

import java.util.Arrays;
import smile.classification.LDA;
import smile.test.data.Iris;
import smile.test.data.USPS;
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
    public void testIris() {
        System.out.println("Iris");
        SumSquaresRatio[] ssr = SumSquaresRatio.fit(Iris.data, "class");
        assertEquals(4, ssr.length);
        assertEquals( 1.6226463, ssr[0].ssr, 1E-6);
        assertEquals( 0.6444144, ssr[1].ssr, 1E-6);
        assertEquals(16.0412833, ssr[2].ssr, 1E-6);
        assertEquals(13.0520327, ssr[3].ssr, 1E-6);
    }

    @Test
    public void tesUSPS() {
        System.out.println("USPS");

        SumSquaresRatio[] score = SumSquaresRatio.fit(USPS.train, "class");
        Arrays.sort(score);
        String[] columns = Arrays.stream(score).limit(121).map(s -> s.feature).toArray(String[]::new);

        double[][] train = USPS.formula.x(USPS.train.drop(columns)).toArray();
        LDA lda = LDA.fit(train, USPS.y);

        double[][] test = USPS.formula.x(USPS.test.drop(columns)).toArray();
        int[] prediction = lda.predict(test);

        double accuracy = new Accuracy().score(USPS.testy, prediction);
        System.out.format("SSR %.2f%%%n", 100 * accuracy);
        assertEquals(0.86, accuracy, 1E-2);
    }
}
