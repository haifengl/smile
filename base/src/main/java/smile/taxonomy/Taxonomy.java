/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.taxonomy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

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
     * @param v a concept.
     * @param w the other concept.
     * @return the lowest common ancestor.
     */
    public Concept lowestCommonAncestor(String v, String w) {
        Concept vnode = getConcept(v);
        Concept wnode = getConcept(w);

        return lowestCommonAncestor(vnode, wnode);
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
}
