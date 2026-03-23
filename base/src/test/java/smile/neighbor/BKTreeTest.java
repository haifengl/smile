/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.neighbor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import smile.math.distance.EditDistance;
import smile.datasets.WordNet;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BKTreeTest {
    String[] words;
    BKTree<String, String> bktree;
    LinearSearch<String, String> naive;

    public BKTreeTest() throws Exception {
        var wordnet = new WordNet();
        words = Arrays.copyOf(wordnet.words(), 10000);

        long start = System.currentTimeMillis();
        bktree = BKTree.of(words, new EditDistance(50, true));
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building BK-tree: %.2fs%n", time);

        naive = LinearSearch.of(words, new EditDistance(true));
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testRange() {
        System.out.println("range");
        List<Neighbor<String, String>> n1 = new ArrayList<>();
        List<Neighbor<String, String>> n2 = new ArrayList<>();
        for (int i = 1000; i < 1100; i++) {
            bktree.search(words[i], 1, n1);
            naive.search(words[i], 1, n2);
            /*
            System.out.println(i+" "+words[i]);
            java.util.Collections.sort(n1);
            java.util.Collections.sort(n2);
            System.out.println(n1.stream().map(Objects::toString).collect(Collectors.joining(", ")));
            System.out.println(n2.stream().map(Objects::toString).collect(Collectors.joining(", ")));
             */
            assertEquals(n1.size(), n2.size());
            String[] s1 = new String[n1.size()];
            String[] s2 = new String[n2.size()];
            for (int j = 0; j < s1.length; j++) {
                s1[j] = n1.get(j).value();
                s2[j] = n2.get(j).value();
            }
            Arrays.sort(s1);
            Arrays.sort(s2);
            for (int j = 0; j < s1.length; j++) {
                assertEquals(s1[j], s2[j]);
            }
            n1.clear();
            n2.clear();
        }
    }

    @Test
    public void testSpeed() {
        System.out.println("speed");
        long start = System.currentTimeMillis();
        List<Neighbor<String, String>> neighbors = new ArrayList<>();
        for (int i = 1000; i < 1100; i++) {
            bktree.search(words[i], 1, neighbors);
            neighbors.clear();
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("BK-tree range 1 search: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 1000; i < 1100; i++) {
            bktree.search(words[i], 2, neighbors);
            neighbors.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("BK-tree range 2 search: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 1000; i < 1100; i++) {
            bktree.search(words[i], 3, neighbors);
            neighbors.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("BK-tree range 3 search: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 1000; i < 1100; i++) {
            bktree.search(words[i], 4, neighbors);
            neighbors.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("BK-tree range 4 search: %.2fs%n", time);
    }
}