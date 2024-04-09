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

import smile.classification.DecisionTree;
import smile.test.data.Abalone;
import smile.test.data.Segment;
import smile.gap.BitString;
import smile.regression.RegressionTree;
import smile.math.MathEx;
import smile.validation.metric.Accuracy;
import smile.validation.metric.RMSE;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GAFETest {
    
    public GAFETest() {
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
/* GAFE with LDA is too slow on Windows as OpenBlas is 4X slower on Windows.
    @Test
    public void testLDA() {
        System.out.println("LDA");
        MathEx.setSeed(19650218); // to get repeatable results.

        GAFE selection = new GAFE();
        BitString[] result = selection.apply(50, 10, 256,
                GAFE.fitness(USPS.x, USPS.y, USPS.testx, USPS.testy, new Accuracy(), LDA::fit));
            
        for (BitString bits : result) {
            System.out.format("%.2f%% %s%n", 100*bits.fitness(), bits);
        }

        assertEquals(0.8789, result[result.length-1].fitness(), 1E-4);
    }
*/
    @Test
    public void testDecisionTree() {
        System.out.println("DecisionTree");
        MathEx.setSeed(19650218); // to get repeatable results.

        GAFE selection = new GAFE();
        BitString[] result = selection.apply(50, 10, Segment.train.ncol()-1,
                GAFE.fitness("class", Segment.train, Segment.test, new Accuracy(), DecisionTree::fit));

        for (BitString bits : result) {
            System.out.format("%.2f%% %s%n", 100*bits.fitness(), bits);
        }

        assertEquals(0.9654, result[result.length-1].fitness(), 1E-4);
    }

    @Test
    public void testRegressionTree() {
        System.out.println("RegressionTree");
        MathEx.setSeed(19650218); // to get repeatable results.

        GAFE selection = new GAFE();
        BitString[] result = selection.apply(50, 10, Abalone.train.ncol()-1,
                GAFE.fitness("rings", Abalone.train, Abalone.test, new RMSE(), RegressionTree::fit));

        for (BitString bits : result) {
            System.out.format("%.4f %s%n", -bits.fitness(), bits);
        }

        assertEquals(2.3840, -result[result.length-1].fitness(), 1E-4);
    }
}
