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

package smile.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import smile.data.Attribute;
import smile.data.NominalAttribute;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("unused")
public class CRFTest {

    class Dataset {
        Attribute[] attributes;
        double[][][] x;
        int[][] y;
        int p;
        int k;
    }
    
    class IntDataset {
        int[][][] x;
        int[][] y;
        int p;
        int k;
    }
    
    IntDataset load(String resource) {
        int p = 0;
        int k = 0;
        IntDataset dataset = new IntDataset();
        ArrayList<int[][]> x = new ArrayList<>();
        ArrayList<int[]> y = new ArrayList<>();

        ArrayList<int[]> seq = new ArrayList<>();
        ArrayList<Integer> label = new ArrayList<>();

        int id = 1;
        try(BufferedReader input = smile.data.parser.IOUtils.getTestDataReader(resource)) {
            String[] words = input.readLine().split(" ");
            int nseq = Integer.parseInt(words[0]);
            k = Integer.parseInt(words[1]);
            p = Integer.parseInt(words[2]);

            String line = null;
            while ((line = input.readLine()) != null) {
                words = line.split(" ");
                int seqid = Integer.parseInt(words[0]);
                int pos = Integer.parseInt(words[1]);
                int len = Integer.parseInt(words[2]);
                
                int[] feature = new int[len];
                for (int i = 0; i < len; i++) {
                    try {
                        feature[i] = Integer.parseInt(words[i+3]);
                    } catch (Exception ex) {
                        System.err.println(ex);
                    }
                }

                if (seqid == id) {
                    seq.add(feature);
                    label.add(Integer.valueOf(words[len + 3]));
                } else {
                    id = seqid;

                    int[][] xx = new int[seq.size()][];
                    int[] yy = new int[seq.size()];
                    for (int i = 0; i < seq.size(); i++) {
                        xx[i] = seq.get(i);
                        yy[i] = label.get(i);
                    }
                    x.add(xx);
                    y.add(yy);

                    seq = new ArrayList<>();
                    label = new ArrayList<>();
                    seq.add(feature);
                    label.add(Integer.valueOf(words[len + 3]));
                }
            }
            
            int[][] xx = new int[seq.size()][];
            int[] yy = new int[seq.size()];
            for (int i = 0; i < seq.size(); i++) {
                xx[i] = seq.get(i);
                yy[i] = label.get(i);
            }
            x.add(xx);
            y.add(yy);
        } catch (IOException ex) {
            System.err.println(ex);
        }

        dataset.p = p;
        dataset.k = k;
        dataset.x = new int[x.size()][][];
        dataset.y = new int[y.size()][];
        for (int i = 0; i < dataset.x.length; i++) {
            dataset.x[i] = x.get(i);
            dataset.y[i] = y.get(i);
        }

        return dataset;
    }
    
    Dataset load(String resource, Attribute[] attributes) {
        int p = 0;
        int k = 0;
        Dataset dataset = new Dataset();
        dataset.attributes = attributes;
        ArrayList<double[][]> x = new ArrayList<>();
        ArrayList<int[]> y = new ArrayList<>();

        ArrayList<double[]> seq = new ArrayList<>();
        ArrayList<Integer> label = new ArrayList<>();

        int id = 1;
        try(BufferedReader input = smile.data.parser.IOUtils.getTestDataReader(resource)) {
            String[] words = input.readLine().split(" ");
            int nseq = Integer.parseInt(words[0]);
            k = Integer.parseInt(words[1]);
            p = Integer.parseInt(words[2]);

            String line = null;
            while ((line = input.readLine()) != null) {
                words = line.split(" ");
                int seqid = Integer.parseInt(words[0]);
                int pos = Integer.parseInt(words[1]);
                int len = Integer.parseInt(words[2]);
                
                if (dataset.attributes == null) {
                    dataset.attributes = new Attribute[len];
                    for (int i = 0; i < len; i++) {
                        dataset.attributes[i] = new NominalAttribute("Attr" + (i+1));
                    }
                }

                double[] feature = new double[len];
                for (int i = 0; i < len; i++) {
                    try {
                        feature[i] = dataset.attributes[i].valueOf(words[i+3]);
                    } catch (ParseException ex) {
                        System.err.println(ex);
                    }
                }

                if (seqid == id) {
                    seq.add(feature);
                    label.add(Integer.valueOf(words[len + 3]));
                } else {
                    id = seqid;

                    double[][] xx = new double[seq.size()][];
                    int[] yy = new int[seq.size()];
                    for (int i = 0; i < seq.size(); i++) {
                        xx[i] = seq.get(i);
                        yy[i] = label.get(i);
                    }
                    x.add(xx);
                    y.add(yy);

                    seq = new ArrayList<>();
                    label = new ArrayList<>();
                    seq.add(feature);
                    label.add(Integer.valueOf(words[len + 3]));
                }
            }
            
            double[][] xx = new double[seq.size()][];
            int[] yy = new int[seq.size()];
            for (int i = 0; i < seq.size(); i++) {
                xx[i] = seq.get(i);
                yy[i] = label.get(i);
            }
            x.add(xx);
            y.add(yy);
        } catch (IOException ex) {
            System.err.println(ex);
        }

        dataset.p = p;
        dataset.k = k;
        dataset.x = new double[x.size()][][];
        dataset.y = new int[y.size()][];
        for (int i = 0; i < dataset.x.length; i++) {
            dataset.x[i] = x.get(i);
            dataset.y[i] = y.get(i);
        }

        return dataset;
    }

    public CRFTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        smile.math.Math.setSeed(54217137L);
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of learn method, of class CRF.
     */
    @Test
    public void testLearnProteinSparse() {
        System.out.println("learn protein sparse");
        IntDataset train = load("sequence/sparse.protein.11.train");
        IntDataset test = load("sequence/sparse.protein.11.test");

        CRF.Trainer trainer = new CRF.Trainer(train.p, train.k);
        trainer.setLearningRate(0.3);
        trainer.setMaxNodes(100);
        trainer.setNumTrees(100);
        CRF crf = trainer.train(train.x, train.y);

        int error = 0;
        int n = 0;
        for (int i = 0; i < test.x.length; i++) {
            n += test.x[i].length;
            int[] label = crf.predict(test.x[i]);
            for (int j = 0; j < test.x[i].length; j++) {
                if (test.y[i][j] != label[j]) {
                    error++;
                }
            }
        }

        int viterbiError = 0;
        crf.setViterbi(true);
        for (int i = 0; i < test.x.length; i++) {
            n += test.x[i].length;
            int[] label = crf.predict(test.x[i]);
            for (int j = 0; j < test.x[i].length; j++) {
                if (test.y[i][j] != label[j]) {
                    viterbiError++;
                }
            }
        }

        System.out.format("Protein error (forward-backward) is %d of %d%n", error, n);
        System.out.format("Protein error (forward-backward) rate = %.2f%%%n", 100.0 * error / n);
        System.out.format("Protein error (Viterbi) is %d of %d%n", viterbiError, n);
        System.out.format("Protein error (Viterbi) rate = %.2f%%%n", 100.0 * viterbiError / n);
        assertEquals(1234, error);
        assertEquals(1318, viterbiError);
    }
    
    /**
     * Test of learn method, of class CRF.
     */
    @Test
    public void testLearnHyphenSparse() {
        System.out.println("learn hyphen sparse");
        IntDataset train = load("sequence/sparse.hyphen.6.train");
        IntDataset test = load("sequence/sparse.hyphen.6.test");

        CRF.Trainer trainer = new CRF.Trainer(train.p, train.k);
        trainer.setLearningRate(1.0);
        trainer.setMaxNodes(100);
        trainer.setNumTrees(100);
        CRF crf = trainer.train(train.x, train.y);

        int error = 0;
        int n = 0;
        for (int i = 0; i < test.x.length; i++) {
            n += test.x[i].length;
            int[] label = crf.predict(test.x[i]);
            for (int j = 0; j < test.x[i].length; j++) {
                if (test.y[i][j] != label[j]) {
                    error++;
                }
            }
        }

        int viterbiError = 0;
        crf.setViterbi(true);
        for (int i = 0; i < test.x.length; i++) {
            n += test.x[i].length;
            int[] label = crf.predict(test.x[i]);
            for (int j = 0; j < test.x[i].length; j++) {
                if (test.y[i][j] != label[j]) {
                    viterbiError++;
                }
            }
        }

        System.out.format("Hypen error (forward-backward) is %d of %d%n", error, n);
        System.out.format("Hypen error (forward-backward) rate = %.2f%%%n", 100.0 * error / n);
        System.out.format("Hypen error (Viterbi) is %d of %d%n", viterbiError, n);
        System.out.format("Hypen error (Viterbi) rate = %.2f%%%n", 100.0 * viterbiError / n);
        assertEquals(470, error);
        assertEquals(478, viterbiError);
    }

    /**
     * Test of learn method, of class CRF.
     */
    @Test
    public void testLearnProtein() {
        System.out.println("learn protein");
        Dataset train = load("sequence/sparse.protein.11.train", null);
        Dataset test = load("sequence/sparse.protein.11.test", train.attributes);

        CRF.Trainer trainer = new CRF.Trainer(train.attributes, train.k);
        trainer.setLearningRate(0.3);
        trainer.setMaxNodes(100);
        trainer.setNumTrees(100);
        CRF crf = trainer.train(train.x, train.y);

        int error = 0;
        int n = 0;
        for (int i = 0; i < test.x.length; i++) {
            n += test.x[i].length;
            int[] label = crf.predict(test.x[i]);
            for (int j = 0; j < test.x[i].length; j++) {
                if (test.y[i][j] != label[j]) {
                    error++;
                }
            }
        }

        int viterbiError = 0;
        crf.setViterbi(true);
        for (int i = 0; i < test.x.length; i++) {
            n += test.x[i].length;
            int[] label = crf.predict(test.x[i]);
            for (int j = 0; j < test.x[i].length; j++) {
                if (test.y[i][j] != label[j]) {
                    viterbiError++;
                }
            }
        }

        System.out.format("Protein error (forward-backward) is %d of %d%n", error, n);
        System.out.format("Protein error (forward-backward) rate = %.2f%%%n", 100.0 * error / n);
        System.out.format("Protein error (Viterbi) is %d of %d%n", viterbiError, n);
        System.out.format("Protein error (Viterbi) rate = %.2f%%%n", 100.0 * viterbiError / n);
        assertEquals(1270, error);
        assertEquals(1420, viterbiError);
    }
    
    /**
     * Test of learn method, of class CRF.
     */
    @Test
    public void testLearnHyphen() {
        System.out.println("learn hyphen");
        Dataset train = load("sequence/sparse.hyphen.6.train", null);
        Dataset test = load("sequence/sparse.hyphen.6.test", train.attributes);

        CRF.Trainer trainer = new CRF.Trainer(train.attributes, train.k);
        trainer.setLearningRate(1.0);
        trainer.setMaxNodes(100);
        trainer.setNumTrees(100);
        CRF crf = trainer.train(train.x, train.y);

        int error = 0;
        int n = 0;
        for (int i = 0; i < test.x.length; i++) {
            n += test.x[i].length;
            int[] label = crf.predict(test.x[i]);
            for (int j = 0; j < test.x[i].length; j++) {
                if (test.y[i][j] != label[j]) {
                    error++;
                }
            }
        }

        int viterbiError = 0;
        crf.setViterbi(true);
        for (int i = 0; i < test.x.length; i++) {
            n += test.x[i].length;
            int[] label = crf.predict(test.x[i]);
            for (int j = 0; j < test.x[i].length; j++) {
                if (test.y[i][j] != label[j]) {
                    viterbiError++;
                }
            }
        }

        System.out.format("Hypen error (forward-backward) is %d of %d%n", error, n);
        System.out.format("Hypen error (forward-backward) rate = %.2f%%%n", 100.0 * error / n);
        System.out.format("Hypen error (Viterbi) is %d of %d%n", viterbiError, n);
        System.out.format("Hypen error (Viterbi) rate = %.2f%%%n", 100.0 * viterbiError / n);
        assertEquals(473, error);
        assertEquals(478, viterbiError);
    }
}