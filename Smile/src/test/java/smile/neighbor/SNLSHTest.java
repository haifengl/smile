package smile.neighbor;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import smile.hash.SimHash;
import smile.math.distance.HammingDistance;
import smile.util.MaxHeap;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private List<String> testData;
    private List<String> trainData;
    private Map<String, Long> signCache;

    @Before
    public void before() throws IOException {
        trainData = loadData("/smile/data/msrp/msr_paraphrase_train.txt");
        testData = loadData("/smile/data/msrp/msr_paraphrase_test.txt");
        signCache = new HashMap<String, Long>();
        for (String q : trainData) {
            long sign = SimHash.simhash64(q);
            signCache.put(q, sign);
        }
    }

    private List<String> loadData(String path) throws IOException {
        List<String> data = Lists.newArrayList();
        List<String> lines = IOUtils.readLines(this.getClass().getResourceAsStream(path));
        for (String line : lines) {
            List<String> s = Splitter.on("\t").omitEmptyStrings().splitToList(line);
            data.add(s.get(s.size() - 1));
            data.add(s.get(s.size() - 2));
        }
        return data.subList(2, data.size());
    }

    private Neighbor<String, String>[] linearKNN(String q, int k) {
        @SuppressWarnings("unchecked")
        Neighbor<String, String>[] neighbors = (Neighbor<String, String>[])Array.newInstance(Neighbor.class, k);
        MaxHeap<Neighbor<String, String>> heap = new MaxHeap<Neighbor<String, String>>(neighbors);
        long sign1 = SimHash.simhash64(q);
        for (String t : trainData) {
            long sign2 = signCache.get(t);
            double distance = HammingDistance.d(sign1, sign2);
            heap.add(new Neighbor<String, String>(t, t, 0, distance));
        }
        return heap.toSortedArray();
    }

    @Test
    public void testKNN() {
        SNLSH<String> lsh = new SNLSH<String>(8);
        for (String t : texts) {
            lsh.put(t, t);
        }
        Neighbor<String, String>[] ns = lsh.knn(texts[0], 10);

        System.out.println("-----test knn: ------");
        for (int i = 0; i < ns.length; i++) {
            System.out.println("neighbor" + i + " : " + ns[i].key + " distance: " + ns[i].distance);
        }
        System.out.println("------test knn end------");
    }

    @Test
    public void testKNNRecall() {
        SNLSH<String> lsh = new SNLSH<String>(8);
        for(String t : trainData) {
            lsh.put(t, t);
        }
        double recall = 0.0;
        for (String q : testData) {
            int k = 3;
            Neighbor<String, String>[] n1 = lsh.knn(q, k);
            Neighbor<String, String>[] n2 = linearKNN(q, k);
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
        System.out.println("recall is " + recall);
    }

    @Test
    public void testNearest() {
        SNLSH<String> lsh = new SNLSH<String>(8);
        for (String t : texts) {
            lsh.put(t, t);
        }
        System.out.println("----------test nearest start:-------");
        Neighbor<String, String> n = lsh.nearest(texts[0]);
        System.out.println("neighbor" + " : " + n.key + " distance: " + n.distance);
        System.out.println("----------test nearest end-------");
    }

    @Test
    public void testRange() {
        SNLSH<String> lsh = new SNLSH<String>(8);
        for (String t : texts) {
            lsh.put(t, t);
        }
        List<Neighbor<String, String>> ns = Lists.newArrayList();
        lsh.range(texts[0], 10, ns);
        System.out.println("-------test range begin-------");
        for (Neighbor<String, String> n : ns) {
            System.out.println(n.key + "  distance: " + n.distance);
        }
        System.out.println("-----test range end ----------");
    }


}
