/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.taxonomy;

import java.util.*;

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
        synset.addAll(Arrays.asList(keywords));

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
     * @return true if a node is a leaf.
     */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    /**
     * Returns the concept synonym set.
     *
     * @return concept synomym set.
     */
    public Set<String> keywords() {
        return synset;
    }

    /**
     * Adds a list of synomym to the concept synset.
     * @param keywords the synomyms.
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

        synset.addAll(Arrays.asList(keywords));
    }

    /**
     * Removes a keyword from the concept synset.
     * @param keyword the keyword.
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
     * Gets all children concepts.
     *
     * @return a vector of children concepts.
     */
    public List<Concept> children() {
        return children;
    }

    /**
     * Adds a child to this node.
     * @param concept the concept.
     * @return the child node.
     */
    public Concept addChild(String concept) {
        return new Concept(this, concept);
    }

    /**
     * Adds a child to this node.
     * @param concept the concept.
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
     * Removes a child to this node.
     * @param concept the concept.
     * @return true if the given concept is a child.
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
     * @param concept the concept.
     * @return true if this concept is an ancestor of the given concept.
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
     * Returns the path from root to this node.
     * @return the path from root to this node.
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
     * Returns the path from this node to the root.
     * @return the path from this node to the root.
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
