/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

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
    HashMap<String, Concept> concepts = new HashMap<>();
    /**
     * The root node in the taxonomy.
     */
    Concept root;

    /**
     * Constructor.
     */
    public Taxonomy() {
        root = new Concept();
        root.taxonomy = this;
    }

    /**
     * Constructor.
     *
     * @param rootConcept the keyword of root concept.
     */
    public Taxonomy(String rootConcept) {
        root = new Concept();
        root.taxonomy = this;
        root.addKeyword(rootConcept);
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
     * Returns a concept node which synset contains the keyword.
     */
    public Concept getConcept(String keyword) {
        return concepts.get(keyword);
    }

    /**
     * Returns all named concepts from this taxonomy
     */
    public List<String> getConcepts() {
        return getConcepts(root);
    }

    /**
     * Returns all named concepts from this taxonomy
     */
    private List<String> getConcepts(Concept c) {
        List<String> keywords = new ArrayList<>();

        while (c != null) {
            if (c.synset != null) {
                keywords.addAll(c.synset);
            }

            if (c.children != null) {
                for (Concept child : c.children) {
                    keywords.addAll(getConcepts(child));
                }
            }
        }

        return keywords;
    }

    /**
     * Returns the lowest common ancestor (LCA) of concepts v and w. The lowest
     * common ancestor is defined between two nodes v and w as the lowest node
     * that has both v and w as descendants (where we allow a node to be a
     * descendant of itself).
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
