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
package smile.taxonomy;

import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Taxonomy}, {@link Concept}, and {@link TaxonomicDistance}.
 *
 * The taxonomy used throughout the tests:
 *
 * <pre>
 *          root
 *         /    \
 *       anon    E
 *       /  \
 *      D    A
 *          / \
 *         B   C
 *             |
 *             F
 * </pre>
 *
 * @author Haifeng Li
 */
public class TaxonomyTest {

    Taxonomy taxonomy;
    Concept anon, a, b, c, d, e, f;

    /**
     * Rebuild the taxonomy before each test so tests are independent.
     */
    @BeforeEach
    public void setUp() {
        taxonomy = new Taxonomy();
        Concept root = taxonomy.getRoot();
        // Create a truly anonymous intermediate node (no keywords registered).
        anon = new Concept();
        anon.taxonomy = taxonomy;
        anon.parent   = root;
        if (root.children == null) root.children = new java.util.ArrayList<>();
        root.children.add(anon);
        e    = root.addChild("E");
        d    = anon.addChild("D");
        a    = anon.addChild("A");
        b    = a.addChild("B");
        c    = a.addChild("C");
        f    = c.addChild("F");
    }

    // -----------------------------------------------------------------------
    // Taxonomy construction
    // -----------------------------------------------------------------------

    @Test
    public void testRootExists() {
        System.out.println("Taxonomy.getRoot");
        assertNotNull(taxonomy.getRoot());
    }

    @Test
    public void testNamedRootConcept() {
        System.out.println("Taxonomy with named root");
        Taxonomy t = new Taxonomy("universe");
        assertNotNull(t.getConcept("universe"));
        assertEquals(t.getRoot(), t.getConcept("universe"));
    }

    @Test
    public void testGetConceptByKeyword() {
        System.out.println("Taxonomy.getConcept");
        assertNotNull(taxonomy.getConcept("A"));
        assertNotNull(taxonomy.getConcept("B"));
        assertNotNull(taxonomy.getConcept("F"));
        assertNull(taxonomy.getConcept("UNKNOWN"));
    }

    @Test
    public void testGetConcepts() {
        System.out.println("Taxonomy.getConcepts");
        List<String> all = taxonomy.getConcepts();
        assertTrue(all.contains("A"));
        assertTrue(all.contains("B"));
        assertTrue(all.contains("C"));
        assertTrue(all.contains("D"));
        assertTrue(all.contains("E"));
        assertTrue(all.contains("F"));
        assertEquals(6, all.size());
    }

    @Test
    public void testSize() {
        System.out.println("Taxonomy.size");
        assertEquals(6, taxonomy.size());
    }

    @Test
    public void testDepth() {
        System.out.println("Taxonomy.depth");
        assertEquals(2, taxonomy.depth("A")); // root → anon → A
        assertEquals(1, taxonomy.depth("E")); // root → E
        assertEquals(4, taxonomy.depth("F")); // root → anon → A → C → F
        assertEquals(-1, taxonomy.depth("UNKNOWN"));
    }

    @Test
    public void testDuplicateKeyword() {
        System.out.println("Taxonomy.duplicate keyword throws");
        assertThrows(IllegalArgumentException.class, () -> taxonomy.getRoot().addChild("A"));
    }

    // -----------------------------------------------------------------------
    // Concept structure
    // -----------------------------------------------------------------------

    @Test
    public void testIsLeaf() {
        System.out.println("Concept.isLeaf");
        assertTrue(b.isLeaf());
        assertTrue(d.isLeaf());
        assertTrue(e.isLeaf());
        assertTrue(f.isLeaf());
        assertFalse(a.isLeaf());
        assertFalse(taxonomy.getRoot().isLeaf());
    }

    @Test
    public void testChildren() {
        System.out.println("Concept.children");
        List<Concept> children = a.children();
        assertNotNull(children);
        assertEquals(2, children.size());
        assertTrue(children.contains(b));
        assertTrue(children.contains(c));
    }

    @Test
    public void testKeywords() {
        System.out.println("Concept.keywords");
        assertTrue(a.keywords().contains("A"));
        assertTrue(b.keywords().contains("B"));
    }

    @Test
    public void testAddKeywords() {
        System.out.println("Concept.addKeywords");
        b.addKeywords("bee", "B2");
        assertTrue(b.keywords().contains("bee"));
        assertTrue(b.keywords().contains("B2"));
        assertSame(b, taxonomy.getConcept("bee"));
        assertSame(b, taxonomy.getConcept("B2"));
        assertEquals(8, taxonomy.size());
    }

    @Test
    public void testAddDuplicateKeyword() {
        System.out.println("Concept.addKeywords duplicate throws");
        assertThrows(IllegalArgumentException.class, () -> b.addKeywords("A")); // already used
    }

    @Test
    public void testRemoveKeyword() {
        System.out.println("Concept.removeKeyword");
        b.addKeywords("bee");
        b.removeKeyword("bee");
        assertNull(taxonomy.getConcept("bee"));
        assertFalse(b.keywords().contains("bee"));
    }

    @Test
    public void testRemoveNonExistentKeyword() {
        System.out.println("Concept.removeKeyword non-existent throws");
        assertThrows(IllegalArgumentException.class, () -> b.removeKeyword("NOPE"));
    }

    @Test
    public void testIsAncestorOf() {
        System.out.println("Concept.isAncestorOf");
        assertTrue(a.isAncestorOf(b));
        assertTrue(a.isAncestorOf(c));
        assertTrue(a.isAncestorOf(f));
        assertTrue(taxonomy.getRoot().isAncestorOf(f));
        assertFalse(b.isAncestorOf(a));
        assertFalse(b.isAncestorOf(c));
        assertFalse(a.isAncestorOf(a)); // a node is not its own ancestor
    }

    @Test
    public void testGetPathFromRoot() {
        System.out.println("Concept.getPathFromRoot");
        List<Concept> path = f.getPathFromRoot();
        assertEquals(5, path.size());
        assertSame(taxonomy.getRoot(), path.get(0));
        assertSame(anon, path.get(1));
        assertSame(a,    path.get(2));
        assertSame(c,    path.get(3));
        assertSame(f,    path.get(4));
    }

    @Test
    public void testGetPathToRoot() {
        System.out.println("Concept.getPathToRoot");
        List<Concept> path = f.getPathToRoot();
        assertEquals(5, path.size());
        assertSame(f,    path.get(0));
        assertSame(c,    path.get(1));
        assertSame(a,    path.get(2));
        assertSame(anon, path.get(3));
        assertSame(taxonomy.getRoot(), path.get(4));
    }

    @Test
    public void testPathFromRootIsReverseOfPathToRoot() {
        System.out.println("Concept.path symmetry");
        List<Concept> fromRoot = f.getPathFromRoot();
        List<Concept> toRoot   = f.getPathToRoot();
        assertEquals(fromRoot.size(), toRoot.size());
        for (int i = 0; i < fromRoot.size(); i++) {
            assertSame(fromRoot.get(i), toRoot.get(fromRoot.size() - 1 - i));
        }
    }

    @Test
    public void testRootPathLength() {
        System.out.println("Concept.root path has length 1");
        assertEquals(1, taxonomy.getRoot().getPathFromRoot().size());
    }

    @Test
    public void testAddChildConcept() {
        System.out.println("Concept.addChild(Concept)");
        // Create an unattached concept then attach it
        Concept g = new Concept(f, "G");
        assertTrue(f.children().contains(g));
        assertSame(f, g.getPathToRoot().get(1));
        assertSame(taxonomy.getConcept("G"), g);
    }

    @Test
    public void testRemoveChild() {
        System.out.println("Concept.removeChild");
        assertTrue(a.removeChild(b));
        assertNull(b.parent);
        assertNull(taxonomy.getConcept("B")); // keywords removed from taxonomy
        assertFalse(a.children().contains(b));
        assertEquals(5, taxonomy.size());
    }

    @Test
    public void testRemoveChildRecursive() {
        System.out.println("Concept.removeChild removes sub-tree keywords");
        // Removing C should also remove F from taxonomy
        assertTrue(a.removeChild(c));
        assertNull(taxonomy.getConcept("C"));
        assertNull(taxonomy.getConcept("F"));
        assertEquals(4, taxonomy.size()); // A, B, D, E remain
    }

    @Test
    public void testRemoveNonChildThrows() {
        System.out.println("Concept.removeChild non-child throws");
        assertThrows(IllegalArgumentException.class, () -> a.removeChild(e));
    }

    @Test
    public void testToStringNamed() {
        System.out.println("Concept.toString named");
        String s = a.toString();
        assertTrue(s.contains("A"));
        assertTrue(s.startsWith("Concept [") || s.startsWith("Concept"));
        // Opening bracket must match closing bracket
        assertTrue(s.contains("[") && s.contains("]"));
    }

    @Test
    public void testToStringAnonymous() {
        System.out.println("Concept.toString anonymous");
        // The anonymous intermediate node has an empty string keyword
        // but our test setUp calls addChild("") which registers "" as a keyword.
        // A truly anonymous root concept has no synset.
        Taxonomy t = new Taxonomy();
        String s = t.getRoot().toString();
        assertTrue(s.contains("anonymous"));
    }

    // -----------------------------------------------------------------------
    // Lowest Common Ancestor
    // -----------------------------------------------------------------------

    @Test
    public void testLCASelf() {
        System.out.println("Taxonomy.lowestCommonAncestor same concept");
        // LCA of a node with itself should be the node
        Concept lca = taxonomy.lowestCommonAncestor("A", "A");
        assertSame(a, lca);
    }

    @Test
    public void testLCAParentChild() {
        System.out.println("Taxonomy.lowestCommonAncestor parent-child");
        Concept lca = taxonomy.lowestCommonAncestor("A", "B");
        assertSame(a, lca);
    }

    @Test
    public void testLCASiblings() {
        System.out.println("Taxonomy.lowestCommonAncestor siblings");
        Concept lca = taxonomy.lowestCommonAncestor("B", "C");
        assertSame(a, lca);
    }

    @Test
    public void testLCAAcrossBranches() {
        System.out.println("Taxonomy.lowestCommonAncestor across branches");
        Concept lca = taxonomy.lowestCommonAncestor("E", "B");
        assertSame(taxonomy.getRoot(), lca);
    }

    @Test
    public void testLCADeepNode() {
        System.out.println("Taxonomy.lowestCommonAncestor deep node");
        Concept lca = taxonomy.lowestCommonAncestor("D", "F");
        assertSame(anon, lca);
    }

    @Test
    public void testLCAUnknownKeywordThrows() {
        System.out.println("Taxonomy.lowestCommonAncestor unknown keyword throws");
        assertThrows(IllegalArgumentException.class,
                () -> taxonomy.lowestCommonAncestor("A", "UNKNOWN"));
        assertThrows(IllegalArgumentException.class,
                () -> taxonomy.lowestCommonAncestor("UNKNOWN", "A"));
    }

    // -----------------------------------------------------------------------
    // TaxonomicDistance
    // -----------------------------------------------------------------------

    @Test
    public void testDistanceSelf() {
        System.out.println("TaxonomicDistance.d self");
        TaxonomicDistance td = new TaxonomicDistance(taxonomy);
        assertEquals(0.0, td.d("A", "A"), 1E-9);
        assertEquals(0.0, td.d("F", "F"), 1E-9);
    }

    @Test
    public void testDistanceParentChild() {
        System.out.println("TaxonomicDistance.d parent-child");
        TaxonomicDistance td = new TaxonomicDistance(taxonomy);
        // A is 1 hop from B (A→B)
        assertEquals(1.0, td.d("A", "B"), 1E-9);
        assertEquals(1.0, td.d("B", "A"), 1E-9); // symmetric
    }

    @Test
    public void testDistanceSiblings() {
        System.out.println("TaxonomicDistance.d siblings");
        TaxonomicDistance td = new TaxonomicDistance(taxonomy);
        // B and C share parent A: B→A→C = 2 hops
        assertEquals(2.0, td.d("B", "C"), 1E-9);
    }

    @Test
    public void testDistanceAcrossBranches() {
        System.out.println("TaxonomicDistance.d across branches");
        TaxonomicDistance td = new TaxonomicDistance(taxonomy);
        // A→F: A is ancestor of F via A→C→F, distance = 2
        assertEquals(2.0, td.d("A", "F"), 1E-9);
        // E→F: E→root→anon→A→C→F = 5 hops
        assertEquals(5.0, td.d("E", "F"), 1E-9);
    }

    @Test
    public void testDistanceSymmetric() {
        System.out.println("TaxonomicDistance.d symmetric");
        TaxonomicDistance td = new TaxonomicDistance(taxonomy);
        assertEquals(td.d("E", "F"), td.d("F", "E"), 1E-9);
        assertEquals(td.d("B", "D"), td.d("D", "B"), 1E-9);
    }

    @Test
    public void testDistanceByConcept() {
        System.out.println("TaxonomicDistance.d by Concept objects");
        TaxonomicDistance td = new TaxonomicDistance(taxonomy);
        assertEquals(2.0, td.d(a, f), 1E-9);
    }

    @Test
    public void testDistanceCrossExistingTaxonomyThrows() {
        System.out.println("TaxonomicDistance.d cross-taxonomy throws");
        Taxonomy other = new Taxonomy("X");
        TaxonomicDistance td = new TaxonomicDistance(taxonomy);
        assertThrows(IllegalArgumentException.class,
                () -> td.d(a, other.getConcept("X")));
    }

    @Test
    public void testToString() {
        System.out.println("TaxonomicDistance.toString");
        TaxonomicDistance td = new TaxonomicDistance(taxonomy);
        assertNotNull(td.toString());
        assertFalse(td.toString().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Concept.depth()
    // -----------------------------------------------------------------------

    @Test
    public void testConceptDepth() {
        System.out.println("Concept.depth");
        assertEquals(0, taxonomy.getRoot().depth());
        assertEquals(1, e.depth());     // root → E
        assertEquals(1, anon.depth());  // root → anon
        assertEquals(2, a.depth());     // root → anon → A
        assertEquals(3, b.depth());     // root → anon → A → B
        assertEquals(4, f.depth());     // root → anon → A → C → F
    }

    // -----------------------------------------------------------------------
    // Concept.height()
    // -----------------------------------------------------------------------

    @Test
    public void testConceptHeight() {
        System.out.println("Concept.height");
        assertEquals(0, b.height());   // leaf
        assertEquals(0, d.height());   // leaf
        assertEquals(0, e.height());   // leaf
        assertEquals(0, f.height());   // leaf
        assertEquals(1, c.height());   // C → F
        assertEquals(2, a.height());   // A → C → F
        assertEquals(3, anon.height()); // anon → A → C → F
        assertEquals(4, taxonomy.getRoot().height());
    }

    // -----------------------------------------------------------------------
    // Concept.subtreeSize()
    // -----------------------------------------------------------------------

    @Test
    public void testConceptSubtreeSize() {
        System.out.println("Concept.subtreeSize");
        assertEquals(1, b.subtreeSize());     // just B
        assertEquals(1, f.subtreeSize());     // just F
        assertEquals(2, c.subtreeSize());     // C, F
        assertEquals(4, a.subtreeSize());     // A, B, C, F
        assertEquals(5, anon.subtreeSize());  // D, A, B, C, F
        assertEquals(6, taxonomy.getRoot().subtreeSize()); // all 6
    }

    // -----------------------------------------------------------------------
    // Concept.isDescendantOf()
    // -----------------------------------------------------------------------

    @Test
    public void testIsDescendantOf() {
        System.out.println("Concept.isDescendantOf");
        assertTrue(b.isDescendantOf(a));
        assertTrue(f.isDescendantOf(a));
        assertTrue(f.isDescendantOf(taxonomy.getRoot()));
        assertFalse(a.isDescendantOf(b));
        assertFalse(a.isDescendantOf(a)); // not its own descendant
        assertFalse(d.isDescendantOf(a)); // d is sibling branch
    }

    // -----------------------------------------------------------------------
    // Concept.siblings()
    // -----------------------------------------------------------------------

    @Test
    public void testSiblings() {
        System.out.println("Concept.siblings");
        // B and C are siblings under A
        List<Concept> bSiblings = b.siblings();
        assertEquals(1, bSiblings.size());
        assertTrue(bSiblings.contains(c));

        List<Concept> cSiblings = c.siblings();
        assertEquals(1, cSiblings.size());
        assertTrue(cSiblings.contains(b));

        // D and A are siblings under anon
        List<Concept> dSiblings = d.siblings();
        assertEquals(1, dSiblings.size());
        assertTrue(dSiblings.contains(a));

        // Root has no parent → no siblings
        List<Concept> rootSiblings = taxonomy.getRoot().siblings();
        assertTrue(rootSiblings.isEmpty());

        // F is the only child of C → no siblings
        List<Concept> fSiblings = f.siblings();
        assertTrue(fSiblings.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Taxonomy.height()
    // -----------------------------------------------------------------------

    @Test
    public void testTaxonomyHeight() {
        System.out.println("Taxonomy.height");
        // root → anon → A → C → F  is the longest path (4 edges)
        assertEquals(4, taxonomy.height());
    }

    @Test
    public void testTaxonomyHeightSingleNode() {
        System.out.println("Taxonomy.height single node");
        Taxonomy t = new Taxonomy("only");
        assertEquals(0, t.height());
    }

    // -----------------------------------------------------------------------
    // Taxonomy.isAncestor() / isDescendant()
    // -----------------------------------------------------------------------

    @Test
    public void testIsAncestorByKeyword() {
        System.out.println("Taxonomy.isAncestor");
        assertTrue(taxonomy.isAncestor("A", "B"));
        assertTrue(taxonomy.isAncestor("A", "F"));
        assertFalse(taxonomy.isAncestor("B", "A"));
        assertFalse(taxonomy.isAncestor("B", "C"));
        assertFalse(taxonomy.isAncestor("A", "A")); // not its own ancestor
    }

    @Test
    public void testIsDescendantByKeyword() {
        System.out.println("Taxonomy.isDescendant");
        assertTrue(taxonomy.isDescendant("F", "A"));
        assertTrue(taxonomy.isDescendant("B", "A"));
        assertFalse(taxonomy.isDescendant("A", "B"));
        assertFalse(taxonomy.isDescendant("D", "A"));
    }

    @Test
    public void testIsAncestorUnknownThrows() {
        System.out.println("Taxonomy.isAncestor unknown throws");
        assertThrows(IllegalArgumentException.class,
                () -> taxonomy.isAncestor("A", "UNKNOWN"));
        assertThrows(IllegalArgumentException.class,
                () -> taxonomy.isAncestor("UNKNOWN", "A"));
    }

    // -----------------------------------------------------------------------
    // Taxonomy.shortestPath()
    // -----------------------------------------------------------------------

    @Test
    public void testShortestPathSelf() {
        System.out.println("Taxonomy.shortestPath self");
        List<Concept> path = taxonomy.shortestPath("A", "A");
        assertEquals(1, path.size());
        assertSame(a, path.get(0));
    }

    @Test
    public void testShortestPathParentChild() {
        System.out.println("Taxonomy.shortestPath parent→child");
        // A → B: direct edge
        List<Concept> path = taxonomy.shortestPath("A", "B");
        assertEquals(2, path.size());
        assertSame(a, path.get(0));
        assertSame(b, path.get(1));
    }

    @Test
    public void testShortestPathChildToParent() {
        System.out.println("Taxonomy.shortestPath child→parent");
        // B → A: direct edge reversed
        List<Concept> path = taxonomy.shortestPath("B", "A");
        assertEquals(2, path.size());
        assertSame(b, path.get(0));
        assertSame(a, path.get(1));
    }

    @Test
    public void testShortestPathSiblings() {
        System.out.println("Taxonomy.shortestPath siblings");
        // B → C via A: B→A→C
        List<Concept> path = taxonomy.shortestPath("B", "C");
        assertEquals(3, path.size());
        assertSame(b, path.get(0));
        assertSame(a, path.get(1));
        assertSame(c, path.get(2));
    }

    @Test
    public void testShortestPathAncestorToDeepDescendant() {
        System.out.println("Taxonomy.shortestPath ancestor→deep descendant");
        // A → F: A→C→F
        List<Concept> path = taxonomy.shortestPath("A", "F");
        assertEquals(3, path.size());
        assertSame(a, path.get(0));
        assertSame(c, path.get(1));
        assertSame(f, path.get(2));
    }

    @Test
    public void testShortestPathAcrossBranches() {
        System.out.println("Taxonomy.shortestPath across branches");
        // E → F: E→root→anon→A→C→F
        List<Concept> path = taxonomy.shortestPath("E", "F");
        assertEquals(6, path.size());
        assertSame(e,    path.get(0));
        assertSame(taxonomy.getRoot(), path.get(1));
        assertSame(anon, path.get(2));
        assertSame(a,    path.get(3));
        assertSame(c,    path.get(4));
        assertSame(f,    path.get(5));
    }

    @Test
    public void testShortestPathLengthMatchesDistance() {
        System.out.println("Taxonomy.shortestPath length == distance + 1");
        TaxonomicDistance td = new TaxonomicDistance(taxonomy);
        String[][] pairs = {{"A","F"},{"E","F"},{"B","D"},{"B","C"}};
        for (String[] pair : pairs) {
            List<Concept> path = taxonomy.shortestPath(pair[0], pair[1]);
            double dist = td.d(pair[0], pair[1]);
            assertEquals((int) dist + 1, path.size(),
                    "Path length for " + pair[0] + "→" + pair[1]);
        }
    }

    @Test
    public void testShortestPathUnknownThrows() {
        System.out.println("Taxonomy.shortestPath unknown throws");
        assertThrows(IllegalArgumentException.class,
                () -> taxonomy.shortestPath("A", "UNKNOWN"));
    }

    // -----------------------------------------------------------------------
    // Taxonomy.subtree()
    // -----------------------------------------------------------------------

    @Test
    public void testSubtree() {
        System.out.println("Taxonomy.subtree");
        List<String> sub = taxonomy.subtree("A");
        assertTrue(sub.contains("A"));
        assertTrue(sub.contains("B"));
        assertTrue(sub.contains("C"));
        assertTrue(sub.contains("F"));
        assertFalse(sub.contains("D")); // different branch
        assertFalse(sub.contains("E")); // different branch
        assertEquals(4, sub.size());
    }

    @Test
    public void testSubtreeLeaf() {
        System.out.println("Taxonomy.subtree leaf");
        List<String> sub = taxonomy.subtree("B");
        assertEquals(1, sub.size());
        assertTrue(sub.contains("B"));
    }

    @Test
    public void testSubtreeUnknownThrows() {
        System.out.println("Taxonomy.subtree unknown throws");
        assertThrows(IllegalArgumentException.class,
                () -> taxonomy.subtree("UNKNOWN"));
    }

    // -----------------------------------------------------------------------
    // Taxonomy.bfs()
    // -----------------------------------------------------------------------

    @Test
    public void testBfs() {
        System.out.println("Taxonomy.bfs");
        List<Concept> order = taxonomy.bfs();
        // Root must be first
        assertSame(taxonomy.getRoot(), order.get(0));
        // All 8 nodes present (root, anon, E, D, A, B, C, F)
        assertEquals(8, order.size());
        // Each node must appear after its parent
        for (Concept node : order) {
            if (node.parent != null) {
                assertTrue(order.indexOf(node.parent) < order.indexOf(node),
                        "Parent of " + node + " must precede it in BFS");
            }
        }
    }

    @Test
    public void testBfsDepthOrder() {
        System.out.println("Taxonomy.bfs depth order");
        List<Concept> order = taxonomy.bfs();
        // Verify depths are non-decreasing
        int prevDepth = 0;
        for (Concept node : order) {
            int d = node.depth();
            assertTrue(d >= prevDepth,
                    "BFS depth should be non-decreasing, got " + d + " after " + prevDepth);
            prevDepth = d;
        }
    }

    // -----------------------------------------------------------------------
    // Taxonomy.leaves()
    // -----------------------------------------------------------------------

    @Test
    public void testLeaves() {
        System.out.println("Taxonomy.leaves");
        List<Concept> leavesFound = taxonomy.leaves();
        assertEquals(4, leavesFound.size());
        assertTrue(leavesFound.contains(b));
        assertTrue(leavesFound.contains(d));
        assertTrue(leavesFound.contains(e));
        assertTrue(leavesFound.contains(f));
        // No internal nodes in the list
        assertFalse(leavesFound.contains(a));
        assertFalse(leavesFound.contains(anon));
        assertFalse(leavesFound.contains(taxonomy.getRoot()));
    }

    @Test
    public void testLeavesSingleNode() {
        System.out.println("Taxonomy.leaves single node");
        Taxonomy t = new Taxonomy("only");
        List<Concept> leavesFound = t.leaves();
        assertEquals(1, leavesFound.size());
        assertSame(t.getRoot(), leavesFound.get(0));
    }
}