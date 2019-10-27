/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.taxonomy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Concept is a set of synonyms, i.e. group of words that are roughly
 * synonymous in a given context.
 * 
 * @author Haifeng Li
 */
public class Concept {

    /**
     * The taxonomy that the concept belongs to.
     */
    Taxonomy taxonomy = null;
    /**
     * synonym sets.
     */
    Set<String> synset;
    /**
     * The parent node in the taxonomy tree.
     */
    Concept parent;
    /**
     * The children nodes in the taxonomy tree.
     */
    List<Concept> children;

    /**
     * Constructor for root node.
     */
    Concept() {
    }

    /**
     * Constructor.
     *
     * @param keywords a list of keywords of this concept
     * @param parent the parent concept
     */
    public Concept(Concept parent, String... keywords) {
        if (parent == null) {
            throw new NullPointerException("Parent concept cannot be null.");
        }

        this.parent = parent;

        synset = new TreeSet<>();
        for (String keyword : keywords) {
            synset.add(keyword);
        }

        if (parent.children == null) {
            parent.children = new ArrayList<>();
        }

        parent.children.add(this);

        taxonomy = parent.taxonomy;
        for (String keyword : keywords) {
            if (taxonomy.concepts.containsKey(keyword)) {
                throw new IllegalArgumentException(String.format("Concept %s already exists.", keyword));
            }
        }

        for (String keyword : keywords) {
            taxonomy.concepts.put(keyword, this);
        }
    }

    /**
     * Check if a node is a leaf in the taxonomy tree.
     */
    public boolean isLeaf() {
        return children == null ? true : children.isEmpty();
    }

    /**
     * Returns the concept synonym set.
     *
     * @return concept synomym set.
     */
    public Set<String> getKeywords() {
        return synset;
    }

    /**
     * Add a list of synomym to the concept synset.
     */
    public void addKeywords(String... keywords) {
        for (String keyword : keywords) {
            if (taxonomy.concepts.containsKey(keyword)) {
                throw new IllegalArgumentException(String.format("Concept %s already exists.", keyword));
            }
        }

        for (String keyword : keywords) {
            taxonomy.concepts.put(keyword, this);
        }

        if (synset == null) {
            synset = new TreeSet<>();
        }

        for (String keyword : keywords) {
            synset.add(keyword);
        }
    }

    /**
     * Remove a keyword from the concept synset.
     */
    public void removeKeyword(String keyword) {
        if (!taxonomy.concepts.containsKey(keyword)) {
            throw new IllegalArgumentException(String.format("Concept %s does not exist.", keyword));
        }

        taxonomy.concepts.remove(keyword);
        if (synset != null) {
            synset.remove(keyword);
        }
    }

    /**
     * Get all children concepts.
     *
     * @return a vector of children concepts.
     */
    public List<Concept> getChildren() {
        return children;
    }

    /**
     * Add a child to this node
     */
    public Concept addChild(String concept) {
        Concept c = new Concept(this, concept);
        return c;
    }

    /**
     * Add a child to this node
     */
    public void addChild(Concept concept) {
        if (taxonomy != concept.taxonomy) {
            throw new IllegalArgumentException("Concepts are not from the same taxonomy.");
        }

        if (children == null) {
            children = new ArrayList<>();
        }
        
        children.add(concept);
        concept.parent = this;
    }

    /**
     * Remove a child to this node
     */
    public boolean removeChild(Concept concept) {
        if (concept.parent != this) {
            throw new IllegalArgumentException("Concept to remove is not a child");
        }

        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == concept) {
                children.remove(i);
                concept.parent = null;
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if this concept is an ancestor of the given concept.
     */
    public boolean isAncestorOf(Concept concept) {
        Concept p = concept.parent;

        while (p != null) {
            if (p == this) {
                return true;
            } else {
                p = p.parent;
            }
        }

        return false;
    }

    /**
     * Returns the path from root to the given node.
     */
    public List<Concept> getPathFromRoot() {
        LinkedList<Concept> path = new LinkedList<>();

        Concept node = this;
        while (node != null) {
            path.addFirst(node);
            node = node.parent;
        }

        return path;
    }

    /**
     * Returns the path from the given node to the root.
     */
    public List<Concept> getPathToRoot() {
        LinkedList<Concept> path = new LinkedList<>();

        Concept node = this;
        while (node != null) {
            path.add(node);
            node = node.parent;
        }

        return path;
    }

    @Override
    public String toString() {
        String displayName = "anonymous";
        if (synset != null && !synset.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            builder.append('(');
            Iterator<String> iter = synset.iterator();
            builder.append(iter.next());
            while (iter.hasNext()) {
                builder.append(", ");
                builder.append(iter.next());
            }
            builder.append(']');
            displayName = builder.toString();
        }

        return String.format("Concept %s", displayName);
    }
}
