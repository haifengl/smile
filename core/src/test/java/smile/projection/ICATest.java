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
package smile.projection;

import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.NumericAttribute;
import smile.data.parser.ArffParser;
import smile.data.parser.DelimitedTextParser;
import smile.math.matrix.DenseMatrix;

/**
 *
 * @author rayeaster
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

    /**
     * Test of learn method, of FastICA.
     */
    @Test
    public void testWithMixedSignal() {
        System.out.println("learn ICA with mixed signal...");

        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setDelimiter(",");
        parser.setResponseIndex(new NominalAttribute("class"), 3);
        try {
            AttributeDataset mixed = parser
                    .parse(smile.data.parser.IOUtils.getTestDataFile("projection/mixed-signal/test_ica_X.csv"));
            double[][] x = mixed.toArray(new double[mixed.size()][]);

            parser.setResponseIndex(new NominalAttribute("class"), 1);
            AttributeDataset signal1 = parser
                    .parse(smile.data.parser.IOUtils.getTestDataFile("projection/mixed-signal/test_ica_s1.csv"));
            double[][] s1 = signal1.toArray(new double[signal1.size()][]);// sinusoidal

            AttributeDataset signal2 = parser
                    .parse(smile.data.parser.IOUtils.getTestDataFile("projection/mixed-signal/test_ica_s2.csv"));
            double[][] s2 = signal2.toArray(new double[signal2.size()][]);// square

            AttributeDataset signal3 = parser
                    .parse(smile.data.parser.IOUtils.getTestDataFile("projection/mixed-signal/test_ica_s3.csv"));
            double[][] s3 = signal3.toArray(new double[signal3.size()][]);// sawtooth

            ICA fastICA = new ICA(x, 3);

            double[][] p = fastICA.project(x);
            int size = mixed.size();

            double[] p1 = new double[size];// sawtooth
            for (int i = 0; i < size; i++) {
                p1[i] = p[i][0];
            }

            double[] p2 = new double[size];// square
            for (int i = 0; i < size; i++) {
                p2[i] = p[i][1];
            }

            double[] p3 = new double[size];// sinusoidal
            for (int i = 0; i < size; i++) {
                p3[i] = p[i][2];
            }

            double[] o1 = new double[size];
            for (int i = 0; i < size; i++) {
                o1[i] = s1[i][0];
            }

            double[] o2 = new double[size];
            for (int i = 0; i < size; i++) {
                o2[i] = s2[i][0];
            }

            double[] o3 = new double[size];
            for (int i = 0; i < size; i++) {
                o3[i] = s3[i][0];
            }

            double mse1 = 0;
            for (int i = 0; i < size; i++) {
                double diff = p1[i] - o3[i];
                mse1 += diff * diff;
            }
            double rmse1 = Math.sqrt(mse1 / size);
            System.out.println("rmse for signal1 is " + rmse1);
            assertTrue(rmse1 < 1);

            double mse2 = 0;
            for (int i = 0; i < size; i++) {
                double diff = p2[i] - o2[i];
                mse2 += diff * diff;
            }
            double rmse2 = Math.sqrt(mse2 / size);
            System.out.println("rmse for signal2 is " + rmse2);
            assertTrue(rmse2 < 1);

            double mse3 = 0;
            for (int i = 0; i < size; i++) {
                double diff = p3[i] - o1[i];
                mse3 += diff * diff;
            }
            double rmse3 = Math.sqrt(mse3 / size);
            System.out.println("rmse for signal3 is " + rmse3);
            assertTrue(rmse3 < 1);

// for output un-mixing signals
            // BufferedWriter bw = new BufferedWriter(new
            // FileWriter("test_ica_reconstruct.csv"));
            // for(int i = 0;i < size;i++) {
            // StringBuilder sb = new StringBuilder();
            // sb.append(p1[i]).append(",").append(p2[i]).append(",").append(p3[i]);
            // bw.append(sb.toString());
            // bw.newLine();
            // }
            // bw.flush();
            // bw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}