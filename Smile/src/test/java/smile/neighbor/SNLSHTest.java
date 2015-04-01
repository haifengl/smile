package smile.neighbor;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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
    public void testNearest() {
        SNLSH<String> lsh = new SNLSH(8, 2);
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
        SNLSH<String> lsh = new SNLSH<String>(8, 2);
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
