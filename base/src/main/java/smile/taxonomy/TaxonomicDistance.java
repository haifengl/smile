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

import java.util.Iterator;
import java.util.List;
import smile.math.distance.Distance;

/**
 * The distance and semantic similarity between concepts in a taxonomy.
 *
 * <h2>Distance</h2>
 * The edge-counting distance between two concepts {@code a} and {@code b} is the
 * number of edges on the shortest path through their lowest common ancestor (LCA):
 * <pre>
 *     d(a, b) = depth(a) + depth(b) − 2 × depth(LCA(a, b))
 * </pre>
 *
 * <h2>Semantic Similarity</h2>
 * Three widely-used similarity measures from the computational linguistics
 * literature are provided, all returning values in [0, 1] where 1 means
 * identical.
 *
 * <dl>
 * <dt>Wu-Palmer (wup)</dt>
 * <dd>Based on the depth of the LCA relative to the depths of the two concepts:
 * <pre>sim(a,b) = 2 × depth(LCA) / (depth(a) + depth(b))</pre>
 * </dd>
 *
 * <dt>Leacock-Chodorow (lch)</dt>
 * <dd>Combines edge-counting distance with the overall depth of the taxonomy:
 * <pre>sim(a,b) = −log(d(a,b) / (2 × H))</pre>
 * where {@code H} is the height of the taxonomy.  The raw value is in
 * (0, log(2H)]; it is normalized to [0, 1] by dividing by log(2H).
 * </dd>
 *
 * <dt>Lin</dt>
 * <dd>An information-content-based measure. When no external corpus is
 * available, depth in the taxonomy serves as a proxy for information
 * content: {@code IC(c) = −log((depth(c) + 1) / (H + 1))} where {@code H}
 * is the tree height.
 * <pre>sim(a,b) = 2 × IC(LCA) / (IC(a) + IC(b))</pre>
 * Returns 1 when a == b.
 * </dd>
 * </dl>
 *
 * <h2>References</h2>
 * <ol>
 * <li>Z. Wu and M. Palmer. Verb semantics and lexical selection. ACL, 1994.</li>
 * <li>C. Leacock and M. Chodorow. Combining local context and WordNet similarity
 *     for word sense identification. WordNet: An Electronic Lexical Database, 1998.</li>
 * <li>D. Lin. An information-theoretic definition of similarity. ICML, 1998.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class TaxonomicDistance implements Distance<Concept> {
    /**
     * The taxonomy basis to calculate the distance.
     */
    private final Taxonomy taxonomy;

    /**
     * Constructor.
     * 
     * @param taxonomy the taxonomy that this distance is associated with.
     */
    public TaxonomicDistance(Taxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    @Override
    public String toString() {
        return String.format("Taxonomic distance on %s", taxonomy);
    }

    /**
     * Computes the edge-counting distance between two concepts identified by
     * their keywords.
     * @param x a concept keyword.
     * @param y the other concept keyword.
     * @return the edge-counting distance.
     * @throws IllegalArgumentException if either keyword is not in the taxonomy.
     */
    public double d(String x, String y) {
        Concept cx = taxonomy.getConcept(x);
        if (cx == null) throw new IllegalArgumentException("Concept not found: " + x);
        Concept cy = taxonomy.getConcept(y);
        if (cy == null) throw new IllegalArgumentException("Concept not found: " + y);
        return d(cx, cy);
    }

    /**
     * Computes the edge-counting distance between two concepts.
     * <pre>d(a,b) = depth(a) + depth(b) − 2 × depth(LCA(a,b))</pre>
     */
    @Override
    public double d(Concept x, Concept y) {
        if (x.taxonomy != y.taxonomy) {
            throw new IllegalArgumentException("Concepts are not from the same taxonomy.");
        }

        List<Concept> xPath = x.getPathFromRoot();
        List<Concept> yPath = y.getPathFromRoot();

        Iterator<Concept> xIter = xPath.iterator();
        Iterator<Concept> yIter = yPath.iterator();

        int depth = 0;
        while (xIter.hasNext() && yIter.hasNext()) {
            if (xIter.next() != yIter.next()) {
                break;
            }
            depth++;
        }

        return xPath.size() - depth + yPath.size() - depth;
    }

    /**
     * Returns the normalized edge-counting distance in [0, 1].
     * The raw distance is divided by the diameter of the taxonomy
     * (the maximum possible distance between any two concepts = 2 × height).
     * Returns 0 when the two concepts are identical, 1 when they are
     * maximally far apart.
     * @param x a concept keyword.
     * @param y the other concept keyword.
     * @return the normalized distance in [0, 1].
     */
    public double normalizedDistance(String x, String y) {
        int diameter = 2 * taxonomy.height();
        if (diameter == 0) return 0.0; // single-node taxonomy
        return d(x, y) / diameter;
    }

    /**
     * Computes the Wu-Palmer semantic similarity between two concepts.
     * <pre>sim(a,b) = 2 × depth(LCA) / (depth(a) + depth(b))</pre>
     * Returns 1 when the two concepts are the same, and approaches 0 as
     * they become more distantly related.
     * @param x a concept keyword.
     * @param y the other concept keyword.
     * @return the Wu-Palmer similarity in (0, 1].
     */
    public double wuPalmer(String x, String y) {
        Concept cx = taxonomy.getConcept(x);
        if (cx == null) throw new IllegalArgumentException("Concept not found: " + x);
        Concept cy = taxonomy.getConcept(y);
        if (cy == null) throw new IllegalArgumentException("Concept not found: " + y);
        return wuPalmer(cx, cy);
    }

    /**
     * Computes the Wu-Palmer semantic similarity between two concept nodes.
     * @param x a concept.
     * @param y the other concept.
     * @return the Wu-Palmer similarity in (0, 1].
     */
    public double wuPalmer(Concept x, Concept y) {
        if (x == y) return 1.0;
        Concept lca = taxonomy.lowestCommonAncestor(x, y);
        int lcaDepth = lca.depth();
        int dx = x.depth();
        int dy = y.depth();
        // guard: if both concepts are at the root (depth 0) and LCA is root
        if (dx + dy == 0) return 1.0;
        return (2.0 * lcaDepth) / (dx + dy);
    }

    /**
     * Computes the Leacock-Chodorow semantic similarity between two concepts,
     * normalized to [0, 1].
     * <pre>
     *   raw  = −log(d(a,b) / (2 × H))
     *   norm = raw / log(2 × H)        ∈ [0, 1]
     * </pre>
     * where {@code H} is the height of the taxonomy.
     * Returns 1 when the two concepts are identical.
     * @param x a concept keyword.
     * @param y the other concept keyword.
     * @return the Leacock-Chodorow similarity in [0, 1].
     */
    public double leacockChodorow(String x, String y) {
        Concept cx = taxonomy.getConcept(x);
        if (cx == null) throw new IllegalArgumentException("Concept not found: " + x);
        Concept cy = taxonomy.getConcept(y);
        if (cy == null) throw new IllegalArgumentException("Concept not found: " + y);
        return leacockChodorow(cx, cy);
    }

    /**
     * Computes the Leacock-Chodorow semantic similarity between two concept nodes.
     * @param x a concept.
     * @param y the other concept.
     * @return the Leacock-Chodorow similarity in [0, 1].
     */
    public double leacockChodorow(Concept x, Concept y) {
        if (x == y) return 1.0;
        int H = taxonomy.height();
        if (H == 0) return 1.0; // single-node taxonomy
        double dist = d(x, y);
        double maxScore = Math.log(2.0 * H);
        if (maxScore == 0) return 1.0;
        // dist must be at least 1 for distinct concepts
        double raw = -Math.log(dist / (2.0 * H));
        return Math.min(1.0, raw / maxScore);
    }

    /**
     * Computes the Lin semantic similarity between two concepts using
     * depth as a proxy for information content.
     * <p>
     * Information content: {@code IC(c) = −log((depth+1)/(H+1))}
     * <pre>sim(a,b) = 2 × IC(LCA) / (IC(a) + IC(b))</pre>
     * Returns 1 when the two concepts are the same, and 0 when IC(a) + IC(b) == 0
     * (both at the root with H == 0).
     * @param x a concept keyword.
     * @param y the other concept keyword.
     * @return the Lin similarity in [0, 1].
     */
    public double lin(String x, String y) {
        Concept cx = taxonomy.getConcept(x);
        if (cx == null) throw new IllegalArgumentException("Concept not found: " + x);
        Concept cy = taxonomy.getConcept(y);
        if (cy == null) throw new IllegalArgumentException("Concept not found: " + y);
        return lin(cx, cy);
    }

    /**
     * Computes the Lin semantic similarity between two concept nodes.
     * @param x a concept.
     * @param y the other concept.
     * @return the Lin similarity in [0, 1].
     */
    public double lin(Concept x, Concept y) {
        if (x == y) return 1.0;
        int H = taxonomy.height();
        // IC(c) = -log((depth+1)/(H+1))
        double icX   = ic(x.depth(), H);
        double icY   = ic(y.depth(), H);
        double icLca = ic(taxonomy.lowestCommonAncestor(x, y).depth(), H);
        double denom = icX + icY;
        if (denom == 0.0) return 0.0;
        // Clamp to 1: when LCA == one of the nodes (parent–child case),
        // 2*IC(LCA) can exceed IC(x)+IC(y) because deeper nodes have higher IC.
        return Math.min(1.0, (2.0 * icLca) / denom);
    }

    /** Returns the depth-based information content. */
    private static double ic(int depth, int treeHeight) {
        return -Math.log((depth + 1.0) / (treeHeight + 1.0));
    }
}
