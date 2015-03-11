/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.classification;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class MaxentTest {

    class Dataset {
        int[][] x;
        int[] y;
        int p;
    }

    @SuppressWarnings("unused")
    Dataset load(String resource) {
        int p = 0;
        ArrayList<int[]> x = new ArrayList<int[]>();
        ArrayList<Integer> y = new ArrayList<Integer>();

        BufferedReader input = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(resource)));
        try {
            String[] words = input.readLine().split(" ");
            int nseq = Integer.valueOf(words[0]);
            int k = Integer.valueOf(words[1]);
            p = Integer.valueOf(words[2]);

            String line = null;
            while ((line = input.readLine()) != null) {
                words = line.split(" ");
                int seqid = Integer.valueOf(words[0]);
                int pos = Integer.valueOf(words[1]);
                int len = Integer.valueOf(words[2]);

                int[] feature = new int[len];
                for (int i = 0; i < len; i++) {
                    feature[i] = Integer.valueOf(words[i+3]);
                }

                x.add(feature);
                y.add(Integer.valueOf(words[len+3]));
            }
        } catch (IOException ex) {
            System.err.println(ex);
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                System.err.println(e);
            }
        }

        Dataset dataset = new Dataset();
        dataset.p = p;
        dataset.x = new int[x.size()][];
        dataset.y = new int[y.size()];
        for (int i = 0; i < dataset.x.length; i++) {
            dataset.x[i] = x.get(i);
            dataset.y[i] = y.get(i);
        }
        
        return dataset;
    }

    public MaxentTest() {
        
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
     * Test of learn method, of class Maxent.
     */
    @Test
    public void testLearnProtein() {
        System.out.println("learn protein");
        Dataset train = load("/smile/data/sequence/sparse.protein.11.train");
        Dataset test = load("/smile/data/sequence/sparse.protein.11.test");

        Maxent maxent = new Maxent(train.p, train.x, train.y, 0.1, 1E-5, 500);
        
        int error = 0;
        for (int i = 0; i < test.x.length; i++) {
            if (test.y[i] != maxent.predict(test.x[i])) {
                error++;
            }
        }

        System.out.format("Protein error is %d of %d\n", error, test.x.length);
        System.out.format("Protein error rate = %.2f%%\n", 100.0 * error / test.x.length);
        assertEquals(1338, error);
    }

    /**
     * Test of learn method, of class Maxent.
     */
    @Test
    public void testLearnHyphen() {
        System.out.println("learn hyphen");
        Dataset train = load("/smile/data/sequence/sparse.hyphen.6.train");
        Dataset test = load("/smile/data/sequence/sparse.hyphen.6.test");

        Maxent maxent = new Maxent(train.p, train.x, train.y, 0.1, 1E-5, 500);

        int error = 0;
        for (int i = 0; i < test.x.length; i++) {
            if (test.y[i] != maxent.predict(test.x[i])) {
                error++;
            }
        }

        System.out.format("Protein error is %d of %d\n", error, test.x.length);
        System.out.format("Hyphen error rate = %.2f%%\n", 100.0 * error / test.x.length);
        assertEquals(765, error);
    }
}