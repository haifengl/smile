package smile.neighbor;

import org.junit.Test;

/**
 * @author Qiyang Zuo
 * @since 15/3/31
 */
public class SNLSHTest {
    private String[] texts = {
            "This is a test case",
            "This is another test case",
            "This is another test case too",
            "I want to be far from other cases"
    };

    @Test
    public void testKNN() {
        SNLSH<String> lsh = new SNLSH(8, 2);
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
    public void testNN() {
        SNLSH<String> lsh = new SNLSH(8, 2);
        for (String t : texts) {
            lsh.put(t, t);
        }
        System.out.println("----------test nearest start:-------");
        Neighbor<String, String> n = lsh.nearest(texts[0]);
        System.out.println("neighbor" + " : " + n.key + " distance: " + n.distance);
        System.out.println("----------test nearest end-------");
    }


}
