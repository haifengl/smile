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
import static org.junit.Assert.*;
import smile.classification.LDA;
import smile.data.USPS;
import smile.gap.BitString;
import smile.validation.Accuracy;
import smile.validation.ClassificationMeasure;
import smile.math.MathEx;

/**
 *
 * @author Haifeng Li
 */
public class GAFeatureSelectionTest {
    
    public GAFeatureSelectionTest() {
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
    public void testLearn() {
        System.out.println("learn");

        MathEx.setSeed(19650218); // to get repeatable results.

        int size = 100;
        int generation = 20;
        ClassificationMeasure measure = new Accuracy();

        GAFeatureSelection gap = new GAFeatureSelection();
        BitString[] result = gap.learn(size, generation, USPS.x, USPS.y, USPS.testx, USPS.testy, measure, (x, y) -> LDA.fit(x, y));
            
        for (BitString bits : result) {
            System.out.format("%.2f%% %d ", 100*bits.fitness(), MathEx.sum(bits.bits()));
            for (int i = 0; i < USPS.x[0].length; i++) {
                System.out.print(bits.bits()[i] + " ");
            }
            System.out.println();
        }

        assertEquals(0.8859, result[result.length-1].fitness(), 1E-4);
    }
}
