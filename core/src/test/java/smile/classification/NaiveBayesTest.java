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
package smile.classification;

import smile.validation.LOOCV;
import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.Math;
import smile.feature.Bag;
import smile.stat.distribution.Distribution;
import smile.stat.distribution.GaussianMixture;
import smile.validation.CrossValidation;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class NaiveBayesTest {

    String[] feature = {
        "outstanding", "wonderfully", "wasted", "lame", "awful", "poorly",
        "ridiculous", "waste", "worst", "bland", "unfunny", "stupid", "dull",
        "fantastic", "laughable", "mess", "pointless", "terrific", "memorable",
        "superb", "boring", "badly", "subtle", "terrible", "excellent",
        "perfectly", "masterpiece", "realistic", "flaws"
    };
    double[][] moviex;
    int[] moviey;

    public NaiveBayesTest() {
        String[][] x = new String[2000][];
        int[] y = new int[2000];

        try(BufferedReader input = smile.data.parser.IOUtils.getTestDataReader("text/movie.txt")) {
            for (int i = 0; i < x.length; i++) {
                String[] words = input.readLine().trim().split(" ");

                if (words[0].equalsIgnoreCase("pos")) {
                    y[i] = 1;
                } else if (words[0].equalsIgnoreCase("neg")) {
                    y[i] = 0;
                } else {
                    System.err.println("Invalid class label: " + words[0]);
                }

                x[i] = words;
            }
        } catch (IOException ex) {
            System.err.println(ex);
        }

        moviex = new double[x.length][];
        moviey = new int[y.length];
        Bag<String> bag = new Bag<>(feature);
        for (int i = 0; i < x.length; i++) {
            moviex[i] = bag.feature(x[i]);
            moviey[i] = y[i];
        }
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
     * Test of predict method, of class NaiveBayes.
     */
    @Test
    public void testPredict() {
        System.out.println("predict");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            int n = x.length;
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int l = 0; l < n; l++) {
                double[][] trainx = Math.slice(x, loocv.train[l]);
                int[] trainy = Math.slice(y, loocv.train[l]);

                int p = trainx[0].length;
                int k = Math.max(trainy) + 1;

                double[] priori = new double[k];
                Distribution[][] condprob = new Distribution[k][p];
                for (int i = 0; i < k; i++) {
                    priori[i] = 1.0 / k;
                    for (int j = 0; j < p; j++) {
                        ArrayList<Double> axi = new ArrayList<>();
                        for (int m = 0; m < trainx.length; m++) {
                            if (trainy[m] == i) {
                                axi.add(trainx[m][j]);
                            }
                        }

                        double[] xi = new double[axi.size()];
                        for (int m = 0; m < xi.length; m++) {
                            xi[m] = axi.get(m);
                        }

                        condprob[i][j] = new GaussianMixture(xi, 3);
                    }
                }

                NaiveBayes bayes = new NaiveBayes(priori, condprob);

                if (y[loocv.test[l]] != bayes.predict(x[loocv.test[l]]))
                    error++;
            }

            System.out.format("Iris error rate = %.2f%%%n", 100.0 * error / x.length);
            assertEquals(8, error);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class SequenceNaiveBayes.
     */
    @Test
    public void testLearnMultinomial() {
        System.out.println("batch learn Multinomial");

        double[][] x = moviex;
        int[] y = moviey;
        int n = x.length;
        int k = 10;
        CrossValidation cv = new CrossValidation(n, k);
        int error = 0;
        int total = 0;
        for (int i = 0; i < k; i++) {
            double[][] trainx = Math.slice(x, cv.train[i]);
            int[] trainy = Math.slice(y, cv.train[i]);
            NaiveBayes bayes = new NaiveBayes(NaiveBayes.Model.MULTINOMIAL, 2, feature.length);

            bayes.learn(trainx, trainy);

            double[][] testx = Math.slice(x, cv.test[i]);
            int[] testy = Math.slice(y, cv.test[i]);
            for (int j = 0; j < testx.length; j++) {
                int label = bayes.predict(testx[j]);
                if (label != -1) {
                    total++;
                    if (testy[j] != label) {
                        error++;
                    }
                }
            }
        }

        System.out.format("Multinomial error = %d of %d%n", error, total);
        assertTrue(error < 265);
    }

    /**
     * Test of learn method, of class SequenceNaiveBayes.
     */
    @Test
    public void testLearnMultinomial2() {
        System.out.println("online learn Multinomial");

        double[][] x = moviex;
        int[] y = moviey;
        int n = x.length;
        int k = 10;
        CrossValidation cv = new CrossValidation(n, k);
        int error = 0;
        int total = 0;
        for (int i = 0; i < k; i++) {
            double[][] trainx = Math.slice(x, cv.train[i]);
            int[] trainy = Math.slice(y, cv.train[i]);
            NaiveBayes bayes = new NaiveBayes(NaiveBayes.Model.MULTINOMIAL, 2, feature.length);

            for (int j = 0; j < trainx.length; j++) {
                bayes.learn(trainx[j], trainy[j]);
            }

            double[][] testx = Math.slice(x, cv.test[i]);
            int[] testy = Math.slice(y, cv.test[i]);
            for (int j = 0; j < testx.length; j++) {
                int label = bayes.predict(testx[j]);
                if (label != -1) {
                    total++;
                    if (testy[j] != label) {
                        error++;
                    }
                }
            }
        }

        System.out.format("Multinomial error = %d of %d%n", error, total);
        assertTrue(error < 265);
    }

    /**
     * Test of learn method, of class SequenceNaiveBayes.
     */
    @Test
    public void testLearnBernoulli() {
        System.out.println("batch learn Bernoulli");

        double[][] x = moviex;
        int[] y = moviey;
        int n = x.length;
        int k = 10;
        CrossValidation cv = new CrossValidation(n, k);
        int error = 0;
        int total = 0;
        for (int i = 0; i < k; i++) {
            double[][] trainx = Math.slice(x, cv.train[i]);
            int[] trainy = Math.slice(y, cv.train[i]);
            NaiveBayes bayes = new NaiveBayes(NaiveBayes.Model.BERNOULLI, 2, feature.length);

            bayes.learn(trainx, trainy);

            double[][] testx = Math.slice(x, cv.test[i]);
            int[] testy = Math.slice(y, cv.test[i]);

            for (int j = 0; j < testx.length; j++) {
                int label = bayes.predict(testx[j]);
                if (label != -1) {
                    total++;
                    if (testy[j] != label) {
                        error++;
                    }
                }
            }
        }

        System.out.format("Bernoulli error = %d of %d%n", error, total);
        assertTrue(error < 270);
    }

    /**
     * Test of learn method, of class SequenceNaiveBayes.
     */
    @Test
    public void testLearnBernoulli2() {
        System.out.println("online learn Bernoulli");

        double[][] x = moviex;
        int[] y = moviey;
        int n = x.length;
        int k = 10;
        CrossValidation cv = new CrossValidation(n, k);
        int error = 0;
        int total = 0;
        for (int i = 0; i < k; i++) {
            double[][] trainx = Math.slice(x, cv.train[i]);
            int[] trainy = Math.slice(y, cv.train[i]);
            NaiveBayes bayes = new NaiveBayes(NaiveBayes.Model.BERNOULLI, 2, feature.length);

            for (int j = 0; j < trainx.length; j++) {
                bayes.learn(trainx[j], trainy[j]);
            }

            double[][] testx = Math.slice(x, cv.test[i]);
            int[] testy = Math.slice(y, cv.test[i]);

            for (int j = 0; j < testx.length; j++) {
                int label = bayes.predict(testx[j]);
                if (label != -1) {
                    total++;
                    if (testy[j] != label) {
                        error++;
                    }
                }
            }
        }

        System.out.format("Bernoulli error = %d of %d%n", error, total);
        assertTrue(error < 270);
    }
}
