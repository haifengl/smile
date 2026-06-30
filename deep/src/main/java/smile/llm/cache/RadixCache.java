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

import java.util.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.tensor.Tensor;

/**
 * Radix tree-based KV cache for LLM inference serving.
 *
 * <p>Organizes KV cache activations in a compressed prefix tree (radix tree)
 * so that requests sharing a common token prefix — such as a shared system
 * prompt — can reuse already-computed KV entries without re-computation.
 *
 * <p>Each edge in the tree stores a contiguous span of token IDs together with
 * the corresponding GPU KV cache slot indices (held as an {@code int64}
 * {@link Tensor}). When a new request arrives the longest matching prefix is
 * found in {@code O(|key|)} time, and only the remaining suffix needs to be
 * computed.
 *
 * <h2>Page alignment</h2>
 * When {@code pageSize > 1} all key lengths are rounded down to the nearest
 * multiple of {@code pageSize} before matching or inserting, aligning them to
 * hardware KV cache block boundaries.
 *
 * <h2>Namespace isolation</h2>
 * An optional {@code extraKey} string (e.g., a LoRA adapter ID or cache salt)
 * can be attached to each insert/match call. Entries with different
 * {@code extraKey} values are kept in disjoint subtrees and never share prefix
 * nodes.
 *
 * <h2>Reference counting and eviction</h2>
 * Every node carries a {@link RadixTreeNode#lockRef lock reference count}.
 * Calling {@link #incLockRef} prevents a node and all its ancestors from being
 * evicted; {@link #decLockRef} releases the protection. Eviction follows an
 * LRU policy applied to <em>evictable leaves</em> — nodes whose
 * {@code lockRef} is zero and that have no live (non-evicted) children.
 *
 * <h2>Tensor ownership</h2>
 * Each {@link RadixTreeNode} <em>owns</em> its {@link RadixTreeNode#value} tensor and is
 * responsible for closing it when the node is evicted or split. The
 * {@link MatchResult} returned by {@link #matchPrefix} is {@link AutoCloseable}:
 * use try-with-resources to release the underlying tensor automatically.
 *
 * @author Haifeng Li
 * @see RadixTreeNode
 * @see MatchResult
 * @see InsertResult
 */
public class RadixCache {
    private static final Logger logger = LoggerFactory.getLogger(RadixCache.class);

    /**
     * Page granularity for KV cache alignment.
     * All key lengths are truncated to a multiple of this value.
     */
    final int pageSize;

    /** Sentinel root node. Always has {@code lockRef = 1} so it is never evicted. */
    RadixTreeNode root;

    /** Total token count across all evictable (lockRef == 0) nodes. */
    int evictableSize;

    /** Total token count across all protected (lockRef > 0) nodes. */
    int protectedSize;

    /**
     * The set of leaf nodes that are eligible for LRU eviction.
     * A node qualifies when {@code lockRef == 0} and every child (if any)
     * has already been evicted.
     */
    final Set<RadixTreeNode> evictableLeaves = new HashSet<>();

    /**
     * Constructor.
     * @param pageSize the number of tokens per KV cache page (must be &ge; 1).
     * @throws IllegalArgumentException if {@code pageSize < 1}.
     */
    public RadixCache(int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be >= 1");
        this.pageSize = pageSize;
        reset();
    }

    /** Constructs a cache with the default page size of 1. */
    public RadixCache() {
        this(1);
    }

    // ===== Public API =====

    /**
     * Resets the cache, discarding all stored KV data and reference counts.
     * Every live {@link RadixTreeNode#value} tensor is closed before the tree is cleared.
     * The root sentinel is re-created with an empty key.
     */
    public void reset() {
        if (root != null) {
            closeTensors(root);
        }
        root = new RadixTreeNode(Integer.MIN_VALUE);
        root.key = new int[0];
        root.value = Tensor.of(new long[0]);
        root.lockRef = 1;
        evictableSize = 0;
        protectedSize = 0;
        evictableLeaves.clear();
    }

    /**
     * Finds the longest cached prefix of the given token sequence.
     * Equivalent to calling {@link #matchPrefix(int[], String)} with a
     * {@code null} extra key.
     *
     * @param tokenIds the token ID sequence to look up.
     * @return the match result. Use try-with-resources to release the enclosed
     *         tensor automatically, or call {@link MatchResult#close()} explicitly.
     */
    public MatchResult matchPrefix(int[] tokenIds) {
        return matchPrefix(tokenIds, null);
    }

    /**
     * Finds the longest cached prefix of the given token sequence within a namespace.
     *
     * <p>The search is limited to nodes whose {@code extraKey} matches the supplied
     * value, so entries from different namespaces are never confused.
     *
     * <p>If the match ends inside a stored node (partial match), the node is
     * automatically split at the boundary, improving future lookups. The evictable
     * size is unchanged because the total number of tokens is conserved.
     *
     * <p>This call refreshes the {@link RadixTreeNode#lastAccessTime} of every visited
     * node, feeding the LRU eviction policy.
     *
     * @param tokenIds the token ID sequence to look up.
     * @param extraKey optional namespace tag, or {@code null} for the default namespace.
     * @return the match result. Use try-with-resources to release the enclosed
     *         tensor automatically, or call {@link MatchResult#close()} explicitly.
     */
    public MatchResult matchPrefix(int[] tokenIds, String extraKey) {
        var alignedLen = (tokenIds.length / pageSize) * pageSize;
        if (alignedLen == 0) {
            return new MatchResult(emptyTensor(), root);
        }

        var accessTime = System.nanoTime() * 1e-9;
        root.lastAccessTime = accessTime;

        var chunks = new ArrayList<long[]>();
        var node = root;
        var offset = 0;
        var remaining = alignedLen;

        while (remaining > 0) {
            var childKey = childKey(extraKey, tokenIds, offset);
            var child = node.children.get(childKey);
            if (child == null) break;

            child.lastAccessTime = accessTime;
            var prefixLen = matchTokens(child.key, tokenIds, offset);

            if (prefixLen < child.key.length) {
                // Partial match: split child so future lookups land on a precise boundary.
                var newNode = splitNode(child, prefixLen);
                chunks.add(newNode.value.longArray());
                node = newNode;
                break;
            } else {
                // Full match of this edge: consume it and continue.
                chunks.add(child.value.longArray());
                node = child;
                offset += prefixLen;
                remaining -= prefixLen;
            }
        }

        return new MatchResult(tensorOf(chunks), node);
    }

    /**
     * Inserts a token sequence and its KV cache slot indices into the radix tree.
     * Equivalent to calling {@link #insert(int[], Tensor, String, int)} with a
     * {@code null} extra key and priority 0.
     *
     * @param tokenIds  the token ID sequence.
     * @param kvIndices a 1-D {@code int64} tensor of KV cache slot indices,
     *                  one per token ({@code kvIndices.length() >= tokenIds.length}).
     * @return the insert result.
     */
    public InsertResult insert(int[] tokenIds, Tensor kvIndices) {
        return insert(tokenIds, kvIndices, null, 0);
    }

    /**
     * Inserts a token sequence and its KV cache slot indices into the radix tree.
     *
     * <p>The insert walks the tree consuming as many tokens as match existing
     * edges. Existing nodes that partially overlap with the new key are split.
     * The page-aligned portion of the key not yet in the tree is stored as a
     * new leaf node backed by a freshly-allocated {@code int64} tensor.
     *
     * <p>The returned {@link InsertResult#prefixLen()} indicates how many
     * leading tokens were already cached. The caller can free the duplicate KV
     * slot entries for those positions (indices {@code [0, prefixLen)}).
     *
     * @param tokenIds  the token ID sequence to cache.
     * @param kvIndices a 1-D {@code int64} tensor of KV cache slot indices
     *                  ({@code kvIndices.length() >= tokenIds.length}).
     * @param extraKey  optional namespace tag, or {@code null} for the default namespace.
     * @param priority  eviction priority; higher values delay eviction.
     * @return the insert result.
     */
    public InsertResult insert(int[] tokenIds, Tensor kvIndices, String extraKey, int priority) {
        var alignedLen = (tokenIds.length / pageSize) * pageSize;
        if (alignedLen == 0) {
            return new InsertResult(0, root);
        }

        // Extract all indices once; sliced copies are made below without repeated
        // native-to-Java round-trips for each page.
        var allKvIndices = kvIndices.longArray();

        var accessTime = System.nanoTime() * 1e-9;
        root.lastAccessTime = accessTime;
        root.priority = Math.max(root.priority, priority);

        var offset = 0;
        var remaining = alignedLen;
        var totalPrefixLen = 0;
        var node = root;

        while (remaining > 0) {
            var childKey = childKey(extraKey, tokenIds, offset);
            var child = node.children.get(childKey);
            if (child == null) break;

            child.lastAccessTime = accessTime;
            var prefixLen = matchTokens(child.key, tokenIds, offset);
            totalPrefixLen += prefixLen;
            offset += prefixLen;
            remaining -= prefixLen;

            if (prefixLen < child.key.length) {
                // Divergence in the middle of an existing edge: split it.
                var newNode = splitNode(child, prefixLen);
                newNode.priority = Math.max(newNode.priority, priority);
                newNode.hitCount++;
                node = newNode;
            } else {
                // Full match of this edge.
                child.priority = Math.max(child.priority, priority);
                child.hitCount++;
                node = child;
            }
        }

        if (remaining > 0) {
            // Append a new leaf for the tokens not yet in the tree.
            var newNode = new RadixTreeNode(priority);
            newNode.extraKey = extraKey;
            newNode.parent = node;
            newNode.key = Arrays.copyOfRange(tokenIds, offset, offset + remaining);
            newNode.value = Tensor.of(Arrays.copyOfRange(allKvIndices, offset, offset + remaining));
            newNode.hitCount = 1;

            node.children.put(childKey(extraKey, tokenIds, offset), newNode);
            evictableSize += remaining;

            updateLeafStatus(node);
            updateLeafStatus(newNode);
            node = newNode;
        }

        return new InsertResult(totalPrefixLen, node);
    }

    /**
     * Evicts LRU leaf nodes until at least {@code numTokens} KV cache token
     * slots have been freed, calling {@code freeCallback} for each evicted
     * node's {@link Tensor}.
     *
     * <p>Eviction always starts from the least-recently-used evictable leaf.
     * When a leaf is evicted its parent may itself become a new evictable leaf
     * (if all other children were already evicted) and is then eligible for
     * further eviction in the same call.
     *
     * <p>The {@code freeCallback} receives <em>ownership</em> of the tensor and
     * is responsible for releasing it (e.g., by calling
     * {@link Tensor#close()}). If {@code freeCallback} is {@code null} the
     * tensor is closed immediately.
     *
     * @param numTokens    the minimum number of token slots to reclaim.
     * @param freeCallback invoked with the {@code int64} KV index tensor of each
     *                     evicted node. May be {@code null}.
     * @return the actual number of token slots freed (may exceed {@code numTokens}).
     */
    public int evict(int numTokens, Consumer<Tensor> freeCallback) {
        // Build an LRU min-heap from the current evictable leaves.
        var heap = new PriorityQueue<RadixTreeNode>(Comparator.comparingDouble(n -> n.lastAccessTime));
        heap.addAll(evictableLeaves);

        var numEvicted = 0;
        while (numEvicted < numTokens && !heap.isEmpty()) {
            var x = heap.poll();
            // Skip nodes that were already evicted in this round.
            if (x.isEvicted()) continue;

            var tokenCount = x.key.length;
            var parent = x.parent;
            var value = deleteLeaf(x);

            if (freeCallback != null) {
                freeCallback.accept(value);
            } else {
                value.close();
            }
            numEvicted += tokenCount;

            // If the parent now has no live children and is itself unlocked,
            // it becomes a new evictable leaf and is eligible for removal.
            if (parent != null && parent != root
                    && parent.children.isEmpty()
                    && parent.lockRef == 0
                    && !parent.isEvicted()) {
                heap.offer(parent);
            }
        }

        return numEvicted;
    }

    /**
     * Increments the lock reference count on {@code node} and all its ancestors
     * up to (but not including) the root, protecting them from eviction.
     *
     * <p>Nodes transition from the evictable pool to the protected pool as
     * their lock count becomes positive for the first time.
     *
     * @param node the node to protect (may be {@code null}, which is a no-op).
     * @return the change in {@link #evictableSize} (always &le; 0).
     */
    public int incLockRef(RadixTreeNode node) {
        if (node == null) return 0;
        var delta = 0;
        while (node != root) {
            if (node.lockRef == 0) {
                evictableSize -= node.key.length;
                protectedSize += node.key.length;
                delta -= node.key.length;
            }
            node.lockRef++;
            updateLeafStatus(node);
            node = node.parent;
        }
        return delta;
    }

    /**
     * Decrements the lock reference count on {@code node} and all its ancestors
     * up to (but not including) the root. When a node's count reaches zero it
     * re-enters the evictable pool and becomes eligible for LRU eviction.
     *
     * @param node the node to release (may be {@code null}, which is a no-op).
     * @return the change in {@link #evictableSize} (always &ge; 0).
     */
    public int decLockRef(RadixTreeNode node) {
        if (node == null) return 0;
        var delta = 0;
        while (node != root) {
            if (node.lockRef == 1) {
                evictableSize += node.key.length;
                protectedSize -= node.key.length;
                delta += node.key.length;
            }
            if (node.lockRef > 0) {
                node.lockRef--;
            } else {
                logger.warn("Attempted to decrement lock reference count below 0 for node {}", node.id);
            }
            updateLeafStatus(node);
            node = node.parent;
        }
        return delta;
    }

    /**
     * Returns the total number of evictable (unlocked) tokens currently cached.
     * @return evictable token count.
     */
    public int evictableSize() {
        return evictableSize;
    }

    /**
     * Returns the total number of protected (locked) tokens currently cached.
     * @return protected token count.
     */
    public int protectedSize() {
        return protectedSize;
    }

    /**
     * Returns the total number of tokens in the cache (evictable + protected).
     * Traverses the live (non-evicted) subtree iteratively.
     * @return total cached token count.
     */
    public int totalSize() {
        var total = 0;
        var stack = new ArrayDeque<RadixTreeNode>();
        stack.push(root);
        while (!stack.isEmpty()) {
            var node = stack.pop();
            if (node.value != null) {
                total += (int) node.value.length();
            }
            for (var child : node.children.values()) {
                if (!child.isEvicted()) {
                    stack.push(child);
                }
            }
        }
        return total;
    }

    /**
     * Prints the radix tree structure to stdout for debugging.
     */
    public void prettyPrint() {
        printHelper(root, 0);
        System.out.println("#tokens: " + totalSize());
    }

    // ===== Internal helpers =====

    /**
     * Builds the {@link RadixKey} used to look up a child node whose edge
     * starts at {@code tokens[offset]}.
     */
    private RadixKey childKey(String extraKey, int[] tokens, int offset) {
        return new RadixKey(extraKey, Arrays.copyOfRange(tokens, offset, offset + pageSize));
    }

    /**
     * Counts the number of tokens in {@code nodeKey} that match
     * {@code tokens[offset..]}, rounded down to a multiple of {@code pageSize}.
     * The result is always in {@code [pageSize, nodeKey.length]} because a
     * child is only visited after its first {@code pageSize} tokens already matched.
     */
    private int matchTokens(int[] nodeKey, int[] tokens, int offset) {
        var maxMatch = Math.min(nodeKey.length, tokens.length - offset);
        var matched = 0;
        while (matched < maxMatch && nodeKey[matched] == tokens[offset + matched]) {
            matched++;
        }
        return (matched / pageSize) * pageSize;
    }

    /**
     * Splits {@code child} at {@code splitLen} tokens, inserting a new
     * intermediate node that holds the prefix {@code child.key[0..splitLen)}.
     *
     * <pre>
     *   before:  parent ──(oldKey)──▶ child ──▶ ...
     *   after:   parent ──(prefix)──▶ newNode ──(suffix)──▶ child ──▶ ...
     * </pre>
     *
     * The parent's children map is updated in-place (same lookup key, different
     * target). The evictable and protected sizes are unchanged because the total
     * number of tokens is conserved. The original {@link RadixTreeNode#value} tensor
     * is closed and replaced by two independent slices.
     *
     * @param child    the node to split.
     * @param splitLen number of tokens in the new prefix node (multiple of {@code pageSize}).
     * @return the newly created prefix node.
     */
    private RadixTreeNode splitNode(RadixTreeNode child, int splitLen) {
        var newNode = new RadixTreeNode(child.priority);
        newNode.hitCount = child.hitCount;
        newNode.extraKey = child.extraKey;
        newNode.parent = child.parent;
        newNode.lockRef = child.lockRef;
        newNode.key = Arrays.copyOf(child.key, splitLen);

        // Compute both lookup keys from the original child.key before truncating it.
        var suffixLookupKey = new RadixKey(child.extraKey,
                Arrays.copyOfRange(child.key, splitLen, splitLen + pageSize));
        var parentLookupKey = new RadixKey(child.extraKey,
                Arrays.copyOf(child.key, pageSize));

        // Split the KV index tensor into two independent tensors, then close the original.
        var origIndices = child.value.longArray();
        newNode.value = Tensor.of(Arrays.copyOf(origIndices, splitLen));
        var childNewValue = Tensor.of(Arrays.copyOfRange(origIndices, splitLen, origIndices.length));
        child.value.close();
        child.value = childNewValue;

        // newNode's only child is the (now suffix-holding) child.
        newNode.children.put(suffixLookupKey, child);

        // Replace parent's pointer: the lookup key in the parent does not change
        // because both newNode and original child start with the same first pageSize tokens.
        newNode.parent.children.put(parentLookupKey, newNode);

        // Trim child to its suffix.
        child.parent = newNode;
        child.key = Arrays.copyOfRange(child.key, splitLen, child.key.length);

        return newNode;
    }

    /**
     * Removes {@code node} from its parent and from {@link #evictableLeaves},
     * then re-evaluates the parent's leaf status.
     * Sets {@code node.value = null} to mark it as evicted and returns the
     * previous value tensor (now owned by the caller).
     *
     * @param node the leaf node to delete.
     * @return the detached KV index tensor (caller must close it).
     */
    private Tensor deleteLeaf(RadixTreeNode node) {
        node.parent.children.remove(new RadixKey(node.extraKey, Arrays.copyOf(node.key, pageSize)));
        evictableSize -= node.key.length;
        evictableLeaves.remove(node);
        var value = node.value;
        node.value = null;
        updateLeafStatus(node.parent);
        return value;
    }

    /**
     * Adds or removes {@code node} from {@link #evictableLeaves} based on its
     * current state. A node is an evictable leaf when:
     * <ol>
     *   <li>it has not been evicted ({@code value != null}),</li>
     *   <li>its lock reference count is zero, and</li>
     *   <li>every child (if any) has already been evicted.</li>
     * </ol>
     */
    private void updateLeafStatus(RadixTreeNode node) {
        if (node.isEvicted() || node.lockRef > 0) {
            evictableLeaves.remove(node);
            return;
        }
        var hasLiveChild = node.children.values().stream().anyMatch(c -> !c.isEvicted());
        if (hasLiveChild) {
            evictableLeaves.remove(node);
        } else {
            evictableLeaves.add(node);
        }
    }

    /**
     * Concatenates a list of {@code long[]} chunks into a single 1-D
     * {@code int64} {@link Tensor}. Returns an empty tensor when the list is empty.
     */
    private Tensor tensorOf(List<long[]> chunks) {
        if (chunks.isEmpty()) return emptyTensor();
        var total = chunks.stream().mapToInt(c -> c.length).sum();
        var result = new long[total];
        var pos = 0;
        for (var chunk : chunks) {
            System.arraycopy(chunk, 0, result, pos, chunk.length);
            pos += chunk.length;
        }
        return Tensor.of(result);
    }

    /** Returns a fresh empty 1-D {@code int64} tensor of length 0. */
    private static Tensor emptyTensor() {
        return Tensor.of(new long[0]);
    }

    /**
     * Closes all live {@link RadixTreeNode#value} tensors in the subtree
     * rooted at {@code node}. Used during {@link #reset()}.
     */
    private void closeTensors(RadixTreeNode node) {
        var stack = new ArrayDeque<RadixTreeNode>();
        stack.push(node);
        while (!stack.isEmpty()) {
            var current = stack.pop();
            if (current.value != null) {
                current.value.close();
            }
            stack.addAll(current.children.values());
        }
    }

    /** Prints the subtree rooted at {@code node} with indentation for debugging. */
    private void printHelper(RadixTreeNode node, int indent) {
        var keyLen = node.key != null ? node.key.length : 0;
        var preview = node.key != null
                ? Arrays.copyOf(node.key, Math.min(keyLen, 10))
                : new int[0];
        System.out.println("%s[len=%d key=%s lockRef=%d]".formatted(
                " ".repeat(indent), keyLen, Arrays.toString(preview), node.lockRef));
        for (var child : node.children.values()) {
            printHelper(child, indent + 2);
        }
    }
}
