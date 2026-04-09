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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;

/**
 * A taxonomy is a tree of terms (aka concept) where leaves
 * must be named but intermediary nodes can be anonymous.
 *
 * @author Haifeng Li
 */
public class Taxonomy {

    /**
     * All the concepts in this taxonomy.
     */
    final HashMap<String, Concept> concepts = new HashMap<>();
    /**
     * The root node in the taxonomy.
     */
    final Concept root;

    /**
     * Constructor.
     *
     * @param rootConcept the keyword of root concept.
     */
    public Taxonomy(String... rootConcept) {
        root = new Concept();
        root.taxonomy = this;
        root.addKeywords(rootConcept);
    }

    /**
     * Returns the root node of taxonomy tree.
     * 
     * @return root node.
     */
    public Concept getRoot() {
        return root;
    }

    /**
     * Returns the concept node which synset contains the keyword.
     * @param keyword the keyword.
     * @return the concept node which synset contains the keyword.
     */
    public Concept getConcept(String keyword) {
        return concepts.get(keyword);
    }

    /**
     * Returns all named concepts in the taxonomy.
     * @return all named concepts.
     */
    public List<String> getConcepts() {
        return getConcepts(root);
    }

    /**
     * Returns all named sub-concepts in the taxonomy.
     * @return all named sub-concepts.
     */
    private List<String> getConcepts(Concept c) {
        List<String> keywords = new ArrayList<>();

        if (c.synset != null) {
            keywords.addAll(c.synset);
        }

        if (c.children != null) {
            for (Concept child : c.children) {
                keywords.addAll(getConcepts(child));
            }
        }

        return keywords;
    }

    /**
     * Returns the lowest common ancestor (LCA) of concepts v and w. The lowest
     * common ancestor is defined between two nodes v and w as the lowest node
     * that has both v and w as descendants (where we allow a node to be a
     * descendant of itself).
     *
     * @param v a concept keyword.
     * @param w the other concept keyword.
     * @return the lowest common ancestor.
     * @throws IllegalArgumentException if either keyword is not in the taxonomy.
     */
    public Concept lowestCommonAncestor(String v, String w) {
        Concept vnode = getConcept(v);
        if (vnode == null) {
            throw new IllegalArgumentException("Concept not found: " + v);
        }
        Concept wnode = getConcept(w);
        if (wnode == null) {
            throw new IllegalArgumentException("Concept not found: " + w);
        }
        return lowestCommonAncestor(vnode, wnode);
    }

    /**
     * Returns the depth of a concept in the taxonomy (root has depth 0).
     * @param keyword the concept keyword.
     * @return the depth, or -1 if the keyword is not in the taxonomy.
     */
    public int depth(String keyword) {
        Concept c = getConcept(keyword);
        if (c == null) return -1;
        return c.getPathFromRoot().size() - 1;
    }

    /**
     * Returns the total number of named concepts (keywords) in the taxonomy.
     * @return the number of keywords.
     */
    public int size() {
        return concepts.size();
    }

    /**
     * Returns the lowest common ancestor (LCA) of concepts v and w. The lowest
     * common ancestor is defined between two nodes v and w as the lowest node
     * that has both v and w as descendants (where we allow a node to be a
     * descendant of itself).
     *
     * @param v a concept.
     * @param w the other concept.
     * @return the lowest common ancestor.
     */
    public Concept lowestCommonAncestor(Concept v, Concept w) {
        if (v.taxonomy != w.taxonomy) {
            throw new IllegalArgumentException("Concepts are not from the same taxonomy.");
        }

        List<Concept> vPath = v.getPathFromRoot();
        List<Concept> wPath = w.getPathFromRoot();

        Iterator<Concept> vIter = vPath.iterator();
        Iterator<Concept> wIter = wPath.iterator();

        Concept commonAncestor = null;
        while (vIter.hasNext() && wIter.hasNext()) {
            Concept vAncestor = vIter.next();
            Concept wAncestor = wIter.next();

            if (vAncestor != wAncestor) {
                return commonAncestor;
            } else {
                commonAncestor = vAncestor;
            }
        }

        return commonAncestor;
    }

    /**
     * Returns the height of the taxonomy tree, i.e. the maximum depth of
     * any concept node (root has depth 0, so a single-node tree has height 0).
     * @return the tree height.
     */
    public int height() {
        return root.height();
    }

    /**
     * Returns true if concept {@code ancestor} is an ancestor of concept
     * {@code descendant} in the taxonomy.
     * @param ancestor the potential ancestor keyword.
     * @param descendant the potential descendant keyword.
     * @return true if {@code ancestor} is an ancestor of {@code descendant}.
     * @throws IllegalArgumentException if either keyword is not in the taxonomy.
     */
    public boolean isAncestor(String ancestor, String descendant) {
        Concept a = getConcept(ancestor);
        if (a == null) throw new IllegalArgumentException("Concept not found: " + ancestor);
        Concept d = getConcept(descendant);
        if (d == null) throw new IllegalArgumentException("Concept not found: " + descendant);
        return a.isAncestorOf(d);
    }

    /**
     * Returns true if concept {@code descendant} is a descendant of concept
     * {@code ancestor} in the taxonomy.
     * @param descendant the potential descendant keyword.
     * @param ancestor the potential ancestor keyword.
     * @return true if {@code descendant} is a descendant of {@code ancestor}.
     * @throws IllegalArgumentException if either keyword is not in the taxonomy.
     */
    public boolean isDescendant(String descendant, String ancestor) {
        return isAncestor(ancestor, descendant);
    }

    /**
     * Returns the shortest path between two concepts as an ordered list of
     * concept nodes, from {@code v} to {@code w} (both endpoints inclusive).
     * The path goes up from {@code v} to their lowest common ancestor, then
     * down to {@code w}.
     * @param v a concept keyword.
     * @param w the other concept keyword.
     * @return the ordered list of concept nodes on the shortest path.
     * @throws IllegalArgumentException if either keyword is not in the taxonomy.
     */
    public List<Concept> shortestPath(String v, String w) {
        Concept vnode = getConcept(v);
        if (vnode == null) throw new IllegalArgumentException("Concept not found: " + v);
        Concept wnode = getConcept(w);
        if (wnode == null) throw new IllegalArgumentException("Concept not found: " + w);
        return shortestPath(vnode, wnode);
    }

    /**
     * Returns the shortest path between two concept nodes as an ordered list,
     * from {@code v} to {@code w} (both endpoints inclusive).
     * @param v a concept node.
     * @param w the other concept node.
     * @return the ordered list of concept nodes on the shortest path.
     */
    public List<Concept> shortestPath(Concept v, Concept w) {
        if (v.taxonomy != w.taxonomy) {
            throw new IllegalArgumentException("Concepts are not from the same taxonomy.");
        }
        if (v == w) return Collections.singletonList(v);

        List<Concept> vPath = v.getPathFromRoot();
        List<Concept> wPath = w.getPathFromRoot();

        // Find length of common prefix (path to LCA).
        int commonLen = 0;
        int minLen = Math.min(vPath.size(), wPath.size());
        while (commonLen < minLen && vPath.get(commonLen) == wPath.get(commonLen)) {
            commonLen++;
        }
        // commonLen is now the index just past the LCA.

        // v → LCA: nodes from v down to LCA (reversed path-from-root suffix)
        List<Concept> path = new ArrayList<>();
        for (int i = vPath.size() - 1; i >= commonLen - 1; i--) {
            path.add(vPath.get(i));
        }
        // LCA → w: nodes from LCA+1 down to w
        for (int i = commonLen; i < wPath.size(); i++) {
            path.add(wPath.get(i));
        }
        return path;
    }

    /**
     * Returns all keywords in the sub-tree rooted at the given concept
     * (i.e. the concept itself and all its descendants).
     * @param keyword the root of the sub-tree.
     * @return list of all keywords in the sub-tree.
     * @throws IllegalArgumentException if the keyword is not in the taxonomy.
     */
    public List<String> subtree(String keyword) {
        Concept c = getConcept(keyword);
        if (c == null) throw new IllegalArgumentException("Concept not found: " + keyword);
        return getConcepts(c);
    }

    /**
     * Returns all concept nodes in breadth-first order starting from the root.
     * @return the BFS-ordered list of all concept nodes.
     */
    public List<Concept> bfs() {
        List<Concept> result = new ArrayList<>();
        ArrayDeque<Concept> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Concept node = queue.poll();
            result.add(node);
            if (node.children != null) {
                queue.addAll(node.children);
            }
        }
        return result;
    }

    /**
     * Returns all leaf concept nodes in the taxonomy (nodes with no children).
     * @return the list of leaf concept nodes.
     */
    public List<Concept> leaves() {
        List<Concept> result = new ArrayList<>();
        collectLeaves(root, result);
        return result;
    }

    /** Recursive helper for {@link #leaves()}. */
    private void collectLeaves(Concept node, List<Concept> acc) {
        if (node.isLeaf()) {
            acc.add(node);
        } else if (node.children != null) {
            for (Concept child : node.children) {
                collectLeaves(child, acc);
            }
        }
    }
}
