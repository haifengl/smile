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
import java.util.List;
import smile.math.distance.EditDistance;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case and additional unit tests for {@link BKTree}.
 *
 * @author Haifeng Li
 */
public class BKTreeEdgeTest {

    @Test
    public void testEmptyTreeSize() {
        BKTree<String, String> tree = new BKTree<>(new EditDistance(10, true));
        assertEquals(0, tree.size());
        assertTrue(tree.isEmpty());
    }

    @Test
    public void testSearchOnEmptyTree() {
        BKTree<String, String> tree = new BKTree<>(new EditDistance(10, true));
        List<Neighbor<String, String>> results = new ArrayList<>();
        // Should not throw
        tree.search("hello", 1, results);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testSizeAfterAdd() {
        BKTree<String, String> tree = new BKTree<>(new EditDistance(10, true));
        tree.add("cat", "cat");
        assertEquals(1, tree.size());
        assertFalse(tree.isEmpty());
        tree.add("car", "car");
        assertEquals(2, tree.size());
        tree.add("bar", "bar");
        assertEquals(3, tree.size());
    }

    @Test
    public void testExactMatch() {
        BKTree<String, String> tree = new BKTree<>(new EditDistance(10, true));
        tree.add("cat", "cat");
        tree.add("car", "car");
        tree.add("bar", "bar");

        List<Neighbor<String, String>> results = new ArrayList<>();
        // Use a new String object (not interned) so reference check does not exclude it
        tree.search(new String("cat"), 0, results);
        // distance 0 means exact match; the item should be found
        assertEquals(1, results.size());
        assertEquals("cat", results.getFirst().value());
    }

    @Test
    public void testRadius1() {
        BKTree<String, String> tree = new BKTree<>(new EditDistance(10, true));
        tree.add("cat", "cat");
        tree.add("car", "car");
        tree.add("bar", "bar");
        tree.add("bat", "bat");

        List<Neighbor<String, String>> results = new ArrayList<>();
        // Use a new String so "cat" itself is not excluded by reference equality.
        // Within edit distance 1: "cat"(0), "car"(1), "bat"(1). "bar" has distance 2.
        tree.search(new String("cat"), 1, results);
        assertEquals(3, results.size());
    }

    @Test
    public void testInvalidRadius() {
        BKTree<String, String> tree = new BKTree<>(new EditDistance(10, true));
        tree.add("cat", "cat");
        List<Neighbor<String, String>> results = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> tree.search("cat", 0.5, results));
        assertThrows(IllegalArgumentException.class, () -> tree.search("cat", -1.0, results));
        assertThrows(IllegalArgumentException.class, () -> tree.search("cat",  0.0, results));
    }

    @Test
    public void testDuplicateKeys() {
        // Duplicate keys (distance 0) should be ignored by BK-tree's add
        BKTree<String, String> tree = new BKTree<>(new EditDistance(10, true));
        tree.add("cat", "cat");
        tree.add("cat", "cat");  // duplicate - should be silently dropped
        // Only 1 node should have been inserted
        assertEquals(1, tree.size());
    }

    @Test
    public void testOfStaticFactory() {
        String[] words = {"cat", "car", "bar", "bat", "hat"};
        BKTree<String, String> tree = BKTree.of(words, new EditDistance(10, true));
        assertEquals(5, tree.size());
    }

    @Test
    public void testIntegerRadius() {
        BKTree<String, String> tree = new BKTree<>(new EditDistance(10, true));
        tree.add("cat", "cat");
        tree.add("car", "car");
        tree.add("bat", "bat");

        List<Neighbor<String, String>> r1 = new ArrayList<>();
        List<Neighbor<String, String>> r2 = new ArrayList<>();
        tree.search("cat", 1, r1);       // int overload
        tree.search("cat", 1.0, r2);     // double overload

        // Both should return the same set
        assertEquals(r1.size(), r2.size());
    }

    @Test
    public void testSearchVsLinear() {
        String[] words = {"cat", "car", "bar", "bat", "hat", "sat", "fat", "mat", "rat", "pat"};
        EditDistance dist = new EditDistance(10, true);
        BKTree<String, String> tree = BKTree.of(words, dist);
        LinearSearch<String, String> linear = LinearSearch.of(words, dist);

        // Use a non-interned query so reference equality doesn't exclude "cat" in either structure
        String query = new String("cat");
        List<Neighbor<String, String>> bkResults = new ArrayList<>();
        List<Neighbor<String, String>> naiveResults = new ArrayList<>();
        tree.search(query, 1, bkResults);
        linear.search(query, 1.0, naiveResults);

        assertEquals(naiveResults.size(), bkResults.size());
    }
}

