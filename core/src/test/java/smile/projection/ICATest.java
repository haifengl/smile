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

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import smile.data.AttributeDataset;
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
    public void testFastICA() {
        System.out.println("learn FastICA...");

        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);

            ICA fastICA = new ICA(x, 2);

            double[][] p = fastICA.project(x);
            DenseMatrix ica = fastICA.getProjection();
            double[][] icap = ica.array();
            for (int i = 0; i < icap.length; i++) {
                System.out.print("independent components:[");
                for (int j = 0; j < icap[0].length; j++) {
                    if (j > 0) {
                        System.out.print(",");
                    }
                    System.out.print(icap[i][j]);
                }
                System.out.print("]");
                System.out.println();
            }

            for (int i = 0; i < p.length; i++) {
                System.out.print("projected points No." + (i + 1) + ":[");
                for (int j = 0; j < p[0].length; j++) {
                    if (j > 0) {
                        System.out.print(",");
                    }
                    System.out.print(p[i][j]);
                }
                System.out.print("]");
                System.out.println();
            }

            char space = ' ';
            char point = 'x';
            double[] colmax = smile.math.Math.colMax(p);
            double[] colmin = smile.math.Math.colMin(p);
            double range1 = colmax[0] - colmin[0];
            double bucket1 = range1 / 10;
            System.out.println("First Independent dimension scatter:");
            for (int j = 0; j < p.length; j++) {
                int bs = (int) ((p[j][0] - colmin[0]) / bucket1);
                StringBuilder sp = new StringBuilder();
                for (int i = 0; i < bs; i++) {
                    sp.append(space);
                }
                System.out.print(sp.append(point).toString());
            }
            System.out.println();
            System.out.println("Second Independent dimension scatter:");
            double range2 = colmax[1] - colmin[1];
            double bucket2 = range2 / 10;
            for (int j = 0; j < p.length; j++) {
                int bs = (int) ((p[j][1] - colmin[1]) / bucket2);
                StringBuilder sp = new StringBuilder();
                for (int i = 0; i < bs; i++) {
                    sp.append(space);
                }
                System.out.print(sp.append(point).toString());
            }
            System.out.println();
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of FastICA.
     */
    @Test
    public void testWithMixedSignal() {
        System.out.println("learn ICA with mixed signal...");

        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setDelimiter(",");
        parser.setResponseIndex(new NumericAttribute("class"), 3);
        try {
            AttributeDataset mixed = parser
                    .parse(smile.data.parser.IOUtils.getTestDataFile("projection/mixed-signal/test_ica_X.csv"));
            double[][] x = mixed.toArray(new double[mixed.size()][]);

            ICA fastICA = new ICA(x, 3);

            double[][] p = fastICA.project(x);
            int size = mixed.size();

            double[] p1 = new double[size];
            for (int i = 0; i < size; i++) {
                p1[i] = p[i][0];
            }

            double[] p2 = new double[size];
            for (int i = 0; i < size; i++) {
                p2[i] = p[i][1];
            }

            double[] p3 = new double[size];
            for (int i = 0; i < size; i++) {
                p3[i] = p[i][2];
            }

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