/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
package smile.llm.model.glm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Prefix reuse for the hybrid GLM model. The KDA recurrent state cannot be rewound, so instead of a radix tree of KV
 * pages the cache keeps whole-request snapshots ({@link GlmModel.Snapshot}, one per rank) taken at a few positions of a
 * prompt; a new prompt resumes from the longest snapshot whose token prefix it repeats.
 *
 * <p>Bounded by a byte budget per rank (least recently used snapshots go first; snapshots pinned by an admitted
 * request stay). Thread safe.
 *
 * @author Haifeng Li
 */
final class GlmPrefixCache {
    /** One snapshot: the first {@code tokens.length} tokens of a prompt and the state after them on every rank. */
    static final class Entry {
        final int[] tokens;
        final GlmModel.Snapshot[] ranks;
        final long bytes;
        private long lastUsed;
        private int pins;

        Entry(int[] tokens, GlmModel.Snapshot[] ranks) {
            this.tokens = tokens;
            this.ranks = ranks;
            this.bytes = ranks[0].bytes;
        }

        int position() {
            return tokens.length;
        }
    }

    private final long budgetBytes;
    private final List<Entry> entries = new ArrayList<>();
    private final Consumer<Entry> disposer;
    private long used;
    private long clock;
    private long hits;
    private long lookups;
    private long hitTokens;

    /**
     * @param budgetBytes the most snapshot bytes kept per rank.
     * @param disposer    frees an evicted entry (on the rank threads).
     */
    GlmPrefixCache(long budgetBytes, Consumer<Entry> disposer) {
        this.budgetBytes = budgetBytes;
        this.disposer = disposer;
    }

    long budget() {
        return budgetBytes;
    }

    /**
     * Finds the longest snapshot that is a prefix of the prompt and leaves at least one prompt token to process, and
     * pins it.
     *
     * @param prompt the prompt tokens.
     * @return the entry, or {@code null}; release it with {@link #unpin}.
     */
    synchronized Entry acquire(int[] prompt) {
        lookups++;
        Entry best = null;
        for (Entry e : entries) {
            int n = e.tokens.length;
            if (n < prompt.length && (best == null || n > best.tokens.length)
                    && Arrays.equals(e.tokens, 0, n, prompt, 0, n)) {
                best = e;
            }
        }
        if (best != null) {
            best.pins++;
            best.lastUsed = ++clock;
            hits++;
            hitTokens += best.tokens.length;
        }
        return best;
    }

    synchronized void unpin(Entry e) {
        e.pins--;
    }

    /** Whether a snapshot of exactly the first {@code n} tokens of the prompt exists. */
    synchronized boolean has(int[] prompt, int n) {
        for (Entry e : entries) {
            if (e.tokens.length == n && Arrays.equals(e.tokens, 0, n, prompt, 0, n)) {
                e.lastUsed = ++clock;
                return true;
            }
        }
        return false;
    }

    /** Whether a snapshot of {@code bytes} per rank can ever fit. */
    boolean fits(long bytes) {
        return bytes <= budgetBytes / 2;
    }

    /**
     * Adds a snapshot, evicting least recently used unpinned ones to stay within the budget. The cache owns the entry
     * afterwards (it is disposed at once when it does not fit).
     */
    void put(Entry entry) {
        List<Entry> evicted = new ArrayList<>();
        boolean keep;
        synchronized (this) {
            entry.lastUsed = ++clock;
            while (used + entry.bytes > budgetBytes) {
                Entry victim = null;
                for (Entry e : entries) {
                    if (e.pins == 0 && (victim == null || e.lastUsed < victim.lastUsed)) {
                        victim = e;
                    }
                }
                if (victim == null) {
                    break;
                }
                entries.remove(victim);
                used -= victim.bytes;
                evicted.add(victim);
            }
            keep = used + entry.bytes <= budgetBytes;
            if (keep) {
                entries.add(entry);
                used += entry.bytes;
            }
        }
        evicted.forEach(disposer);
        if (!keep) {
            disposer.accept(entry);
        }
    }

    /** Disposes every entry (shutdown). */
    void clear() {
        List<Entry> all;
        synchronized (this) {
            all = new ArrayList<>(entries);
            entries.clear();
            used = 0;
        }
        all.forEach(disposer);
    }

    synchronized String stats() {
        return String.format("%d snapshots, %.1f MB of %.0f MB per rank, %d/%d lookups hit (%d tokens reused)",
                entries.size(), used / 1e6, budgetBytes / 1e6, hits, lookups, hitTokens);
    }
}
