/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.feature;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.classification.ClassifierTrainer;
import smile.classification.LDA;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
import smile.gap.BitString;
import smile.validation.Accuracy;
import smile.validation.ClassificationMeasure;
import smile.math.Math;

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

    /**
     * Test of learn method, of class GAFeatureSelection.
     */
    @Test
    public void testLearn() {
        System.out.println("learn");

        int size = 100;
        int generation = 20;
        ClassifierTrainer<double[]> trainer = new LDA.Trainer();
        ClassificationMeasure measure = new Accuracy();

        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);

            GAFeatureSelection instance = new GAFeatureSelection();
            BitString[] result = instance.learn(size, generation, trainer, measure, x, y, testx, testy);
            
            for (BitString bits : result) {
                System.out.format("%.2f%% %d ", 100*bits.fitness(), Math.sum(bits.bits()));
                for (int i = 0; i < x[0].length; i++) {
                    System.out.print(bits.bits()[i] + " ");
                }
                System.out.println();
            }

            assertTrue(result[result.length-1].fitness() > 0.88);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
