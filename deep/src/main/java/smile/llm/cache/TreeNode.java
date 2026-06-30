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
package smile.llm.cache;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import smile.deep.tensor.Tensor;

/**
 * A node in the radix tree used for KV cache management.
 * <p>
 * Each node represents an edge labeled with a span of token IDs ({@link #key})
 * and the corresponding KV cache indices ({@link #value}). The tree is a
 * compressed prefix tree: common token prefixes are shared, so a single node
 * can store many tokens at once.
 * <p>
 * Nodes are reference-counted via {@link #lockRef}: a node with
 * {@code lockRef > 0} is <em>protected</em> and cannot be evicted. When all
 * references are released the node becomes <em>evictable</em> and may be
 * reclaimed by the LRU eviction policy.
 *
 * @author Haifeng Li
 */
public class TreeNode {
    private static final AtomicLong COUNTER = new AtomicLong(0);

    /** Unique, monotonically increasing node identifier. */
    public final long id;

    /** Child nodes keyed by the first {@code pageSize} tokens on each outgoing edge. */
    final HashMap<ChildKey, TreeNode> children = new HashMap<>();

    /** Parent node in the tree; {@code null} only for the root sentinel. */
    TreeNode parent;

    /**
     * Token IDs stored on the edge leading into this node.
     * Always a multiple of {@code pageSize} in length for non-root nodes.
     */
    int[] key;

    /**
     * KV cache indices (dtype {@code int64}) — one slot index per token in {@link #key}.
     * The tensor is owned by this node and must be closed when the node is evicted or split.
     * Set to {@code null} when the node has been evicted.
     */
    Tensor value;

    /**
     * Optional namespace tag for the tokens in this node (e.g., a LoRA adapter ID).
     * All nodes that share a prefix path must agree on this value.
     */
    String extraKey;

    /**
     * Reference count. The node is protected from eviction while this is positive.
     * Incremented by {@link RadixCache#incLockRef} and decremented by
     * {@link RadixCache#decLockRef}.
     */
    int lockRef;

    /**
     * Monotonic timestamp (seconds) of the last access to this node.
     * Updated on every {@link RadixCache#matchPrefix} and {@link RadixCache#insert} traversal.
     */
    double lastAccessTime;

    /** Monotonic timestamp (seconds) when this node was created. */
    double creationTime;

    /** Number of cache hits recorded against this node during insert operations. */
    int hitCount;

    /**
     * Priority for priority-aware eviction. Higher values indicate more important
     * content that should be evicted last. The root node starts at
     * {@link Integer#MIN_VALUE}.
     */
    int priority;

    /**
     * Constructor.
     * @param priority initial eviction priority.
     */
    TreeNode(int priority) {
        this.id = COUNTER.getAndIncrement();
        this.priority = priority;
        double now = System.nanoTime() * 1e-9;
        this.lastAccessTime = now;
        this.creationTime = now;
    }

    /** Constructor with default priority of 0. */
    TreeNode() {
        this(0);
    }

    /**
     * Returns {@code true} if this node's KV cache entries have been freed.
     * A node is evicted when {@link #value} is set to {@code null}.
     * @return {@code true} if evicted.
     */
    public boolean isEvicted() {
        return value == null;
    }

    @Override
    public String toString() {
        return "TreeNode{id=" + id +
               ", keyLen=" + (key != null ? key.length : 0) +
               ", lockRef=" + lockRef +
               ", evicted=" + isEvicted() + "}";
    }
}
