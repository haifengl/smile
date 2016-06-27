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

package smile.nlp;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * A trie, also called digital tree or prefix tree, is an ordered tree data
 * structure that is used to store a dynamic set or associative array where
 * the keys are usually strings. Unlike a binary search tree, no node in the
 * tree stores the key associated with that node; instead, its position in
 * the tree defines the key with which it is associated. All the descendants
 * of a node have a common prefix of the string associated with that node,
 * and the root is associated with the empty string. Values are normally
 * not associated with every node, only with leaves and some inner node
 * s that correspond to keys of interest.
 *  
 * @author Haifeng Li
 */
public class Trie<K, V> {

    /**
     * The root node is specialized as a hash map for 
     * quick search. In case of Named Entities, the root
     * node will contains a lot of children. A plain list
     * will be slow for search.
     */
    private HashMap<K, Node> root;
    /**
     * The number of entries.
     */
    private int size;

    public class Node {

        private K key;
        private V value;
        private LinkedList<Node> children;

        public Node(K key) {
            this.key = key;
            this.value = null;
            this.children = new LinkedList<>();
        }
        
        public K getKey() {
            return key;
        }
        
        public V getValue() {
            return value;
        }

        public V getChild(K[] key, int index) {
            if (index >= key.length) {
                return value;
            }

            for (Node child : children) {
                if (child.key.equals(key[index])) {
                    return child.getChild(key, index + 1);
                }
            }

            return null;
        }

        public Node getChild(K key) {
            for (Node child : children) {
                if (child.key.equals(key)) {
                    return child;
                }
            }

            return null;
        }

        public void addChild(K[] key, V value, int index) {
            if (index >= key.length) {
                if (this.value == null) {
                    size++;
                }
                this.value = value;
                return;
            }

            for (Node child : children) {
                if (child.key.equals(key[index])) {
                    child.addChild(key, value, index + 1);
                    return;
                }
            }

            Node child = new Node(key[index]);
            children.addFirst(child);
            child.addChild(key, value, index + 1);
        }

    }

    /**
     * Constructor.
     */
    public Trie() {
        root = new HashMap<>();
    }

    /**
     * Constructor.
     * @param initialCapacity the initial capacity of root node.
     */
    public Trie(int initialCapacity) {
        root = new HashMap<>(initialCapacity);
    }

    /**
     * Add a key with associated value to the trie.
     * @param key the key.
     * @param value the value.
     */
    public void put(K[] key, V value) {
        Node child = root.get(key[0]);
        if (child == null) {
            child = new Node(key[0]);
            root.put(key[0], child);
        }
        child.addChild(key, value, 1);
    }

    /**
     * Returns the associated value of a given key.
     * Returns null if the key doesn't exist in the trie.
     * @param key the key.
     * @return the associated value or null.
     */
    public V get(K[] key) {
        Node child = root.get(key[0]);
        if (child != null) {
            return child.getChild(key, 1);
        }
        return null;
    }
    
    /**
     * Returns the node of a given key.
     * Returns null if the key doesn't exist in the trie.
     * @param key the key.
     * @return the node of the given key or null.
     */
    public Node get(K key) {
        return root.get(key);
    }
    
    /**
     * Returns the number of entries.
     * @return the number of entries.
     */
    public int size() {
        return size;
    }
}