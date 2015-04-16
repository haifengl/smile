package smile.neighbor;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import smile.hash.SimHash;
import smile.math.distance.HammingDistance;
import smile.util.MaxHeap;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * @author Qiyang Zuo
 * @since 15/3/31
 * test data set: http://research.microsoft.com/en-us/downloads/607d14d9-20cd-47e3-85bc-a2f65cd28042/
 */
public class SNLSHTest {
    private String[] texts = {
            "This is a test case",
            "This is another test case",
            "This is another test case too",
            "I want to be far from other cases"
    };

    private List<List<String>> testData;
    private List<List<String>> trainData;
    private List<List<String>> toyData;
    private Map<List<String>, Long> signCache; //tokens<->sign

    @Before
    public void before() throws IOException {
        trainData = loadData("/smile/data/msrp/msr_paraphrase_train.txt");
        testData = loadData("/smile/data/msrp/msr_paraphrase_test.txt");
        signCache = new HashMap<List<String>, Long>();
        for (List<String> tokens : trainData) {
            long sign = SimHash.simhash64(tokens);
            signCache.put(tokens, sign);
        }
        toyData = new ArrayList<List<String>>();
        for (String text : texts) {
            toyData.add(tokenize(text, " "));
        }
    }

    private List<List<String>> loadData(String path) throws IOException {
        List<List<String>> data = new ArrayList<List<String>>();
        List<String> lines = IOUtils.readLines(this.getClass().getResourceAsStream(path));
        for (String line : lines) {
            List<String> s = tokenize(line, "\t");
            data.add(tokenize(s.get(s.size() - 1), " "));
            data.add(tokenize(s.get(s.size() - 2), " "));
        }
        return data.subList(2, data.size());
    }

    private Neighbor<List<String>, List<String>>[] linearKNN(List<String> q, int k) {
        @SuppressWarnings("unchecked")
        Neighbor<List<String>, List<String>>[] neighbors = (Neighbor<List<String>, List<String>>[])Array.newInstance(Neighbor.class, k);
        MaxHeap<Neighbor<List<String>, List<String>>> heap = new MaxHeap<Neighbor<List<String>, List<String>>>(neighbors);
        long sign1 = SimHash.simhash64(q);
        for (List<String> t : trainData) {
            if(t.equals(q)) {
                continue;
            }
            long sign2 = signCache.get(t);
            double distance = HammingDistance.d(sign1, sign2);
            heap.add(new Neighbor<List<String>, List<String>>(t, t, 0, distance));
        }
        return heap.toSortedArray();
    }

    private Neighbor<List<String>, List<String>> linearNearest(List<String> q) {
        long sign1 = SimHash.simhash64(q);
        double minDist = Double.MAX_VALUE;
        List<String> minKey = null;
        for (List<String> t : trainData) {
            if (t.equals(q)) {
                continue;
            }
            long sign2 = signCache.get(t);
            double distance = HammingDistance.d(sign1, sign2);
            if (distance < minDist) {
                minDist = distance;
                minKey = t;
            }
        }
        return new Neighbor<List<String>, List<String>>(minKey, minKey, 0, minDist);
    }

    private void linearRange(List<String> q, double d, List<Neighbor<List<String>,List<String>>> neighbors) {
        long sign1 = SimHash.simhash64(q);
        for (List<String> t : trainData) {
            if (t.equals(q)) {
                continue;
            }
            long sign2 = signCache.get(t);
            double distance = HammingDistance.d(sign1, sign2);
            if (distance <= d) {
                neighbors.add(new Neighbor<List<String>, List<String>>(t, t, 0, distance));
            }
        }
    }

    @Test
    public void testKNN() {
        SNLSH<List<String>> lsh = createLSH(toyData);
        List<String> tokens = tokenize(texts[0]," ");
        Neighbor<List<String>, List<String>>[] ns = lsh.knn(tokens, 10);

        System.out.println("-----test knn: ------");
        for (int i = 0; i < ns.length; i++) {
            System.out.println("neighbor" + i + " : " + ns[i].key + " distance: " + ns[i].distance);
        }
        System.out.println("------test knn end------");
    }

    @Test
    public void testKNNRecall() {
        SNLSH<List<String>> lsh = createLSH(trainData);
        double recall = 0.0;
        for (List<String> q : testData) {
            int k = 3;
            Neighbor<List<String>, List<String>>[] n1 = lsh.knn(q, k);
            Neighbor<List<String>, List<String>>[] n2 = linearKNN(q, k);
            int hit = 0;
            for (int m = 0; m < n1.length && n1[m] != null; m++) {
                for (int n = 0; n < n2.length && n2[n] != null; n++) {
                    if (n1[m].value.equals(n2[n].value)) {
                        hit++;
                        break;
                    }
                }
            }
            recall += 1.0 * hit / k;
        }
        recall /= testData.size();
        System.out.println("SNLSH KNN recall is " + recall);
    }

    @Test
    public void testNearest() {
        SNLSH<List<String>> lsh = createLSH(toyData);
        System.out.println("----------test nearest start:-------");
        Neighbor<List<String>, List<String>> n = lsh.nearest(tokenize(texts[0]," "));
        System.out.println("neighbor" + " : " + n.key + " distance: " + n.distance);
        System.out.println("----------test nearest end-------");
    }

    @Test
    public void testNearestRecall() {
        SNLSH<List<String>> lsh = createLSH(trainData);
        double recall = 0.0;
        for (List<String> q : testData) {
            Neighbor<List<String>, List<String>> n1 = lsh.nearest(q);
            Neighbor<List<String>, List<String>> n2 = linearNearest(q);
            if (n1.value.equals(n2.value)) {
                recall++;
            }
        }
        recall /= testData.size();
        System.out.println("SNLSH Nearest recall is " + recall);
    }

    @Test
    public void testRange() {
        SNLSH<List<String>> lsh = createLSH(toyData);
        List<Neighbor<List<String>, List<String>>> ns = new ArrayList<Neighbor<List<String>, List<String>>>();
        lsh.range(tokenize(texts[0], " "), 10, ns);
        System.out.println("-------test range begin-------");
        for (Neighbor<List<String>, List<String>> n : ns) {
            System.out.println(n.key + "  distance: " + n.distance);
        }
        System.out.println("-----test range end ----------");
    }


    @Test
    public void testRangeRecall() {
        SNLSH<List<String>> lsh = createLSH(trainData);
        double dist = 15.0;
        double recall = 0.0;
        for (List<String> q : testData) {
            List<Neighbor<List<String>, List<String>>> n1 = new ArrayList<Neighbor<List<String>, List<String>>>();
            lsh.range(q, dist, n1);
            List<Neighbor<List<String>, List<String>>> n2 = new ArrayList<Neighbor<List<String>, List<String>>>();
            linearRange(q, dist, n2);
            int hit = 0;
            for (int m = 0; m < n1.size(); m++) {
                for (int n = 0; n < n2.size(); n++) {
                    if (n1.get(m).value.equals(n2.get(n).value)) {
                        hit++;
                        break;
                    }
                }
            }
            if (n2.size() > 0) {
                recall += 1.0 * hit / n2.size();
            }
        }
        recall /= testData.size();
        System.out.println("SNLSH range recall is " + recall);
    }

    private SNLSH<List<String>> createLSH(List<List<String>> data) {
        SNLSH<List<String>> lsh = new SNLSH<List<String>>(8);
        for (List<String> tokens : data) {
            lsh.put(tokens, tokens);
        }
        return lsh;
    }

    private List<String> tokenize(String line, String regex) {
        List<String> tokens = new LinkedList<String>();
        if (line == null || line.length() == 0) {
            throw new IllegalArgumentException("Line should not be blank!");
        }
        String[] ss = line.split(regex);
        for (String s : ss) {
            if (s == null || s.length() == 0) {
                continue;
            }
            tokens.add(s);
        }
        return tokens;
    }
}
