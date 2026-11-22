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
package smile.llm.model.inkling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Snapshots of request states keyed by the token prefix they were computed for, with LRU eviction under a device-memory
 * budget. A request whose prompt starts with a stored prefix restores the snapshot instead of recomputing the prefix; the
 * recurrent parts of the state (convolutions, the sliding-window history) make a snapshot at an exact position
 * necessary, so only whole snapshots are matched.
 *
 * <p>Not thread-safe: used from the model's worker thread and the engine thread through {@link Inkling}'s lock.
 *
 * @author Haifeng Li
 */
final class InklingPrefixCache {
    /** A stored snapshot and the tokens it covers. */
    static final class Entry {
        final int[] tokens;
        final InklingState.Snapshot snapshot;
        int pins;

        Entry(int[] tokens, InklingState.Snapshot snapshot) {
            this.tokens = tokens;
            this.snapshot = snapshot;
        }

        int length() {
            return tokens.length;
        }
    }

    private final long budget;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final TreeSet<Integer> lengths = new TreeSet<>();
    private long used;
    private long hits;
    private long lookups;

    private record Key(int length, long hash) { }

    InklingPrefixCache(long budgetBytes) {
        this.budget = budgetBytes;
    }

    private static long hash(int[] tokens, int length) {
        long h = 1125899906842597L;
        for (int i = 0; i < length; i++) {
            h = 31 * h + tokens[i];
        }
        return h;
    }

    /** Whether a snapshot of {@code bytes} could ever be stored. */
    boolean fits(long bytes) {
        return budget > 0 && bytes <= budget;
    }

    /** Whether the prefix of {@code tokens} of that length is stored. */
    boolean has(int[] tokens, int length) {
        Entry e = entries.get(new Key(length, hash(tokens, length)));
        return e != null && equal(e.tokens, tokens, length);
    }

    private static boolean equal(int[] stored, int[] tokens, int length) {
        return stored.length == length && Arrays.equals(stored, 0, length, tokens, 0, length);
    }

    /**
     * Finds the longest stored prefix of {@code prompt} that is at most {@code limit} tokens, and pins it.
     *
     * @return the entry, or {@code null}.
     */
    Entry acquire(int[] prompt, int limit) {
        lookups++;
        // hash every candidate length in one pass over the prompt
        List<Integer> candidates = new ArrayList<>(lengths.headSet(Math.min(limit, prompt.length), true).descendingSet());
        if (candidates.isEmpty()) {
            return null;
        }
        long[] hashAt = new long[candidates.size()];
        int maxLen = candidates.getFirst();
        long h = 1125899906842597L;
        int next = candidates.size() - 1;
        for (int i = 0; i < maxLen && next >= 0; i++) {
            h = 31 * h + prompt[i];
            while (next >= 0 && candidates.get(next) == i + 1) {
                hashAt[next--] = h;
            }
        }
        for (int c = 0; c < candidates.size(); c++) {
            Entry e = entries.get(new Key(candidates.get(c), hashAt[c]));
            if (e != null && equal(e.tokens, prompt, candidates.get(c))) {
                e.pins++;
                hits++;
                return e;
            }
        }
        return null;
    }

    void release(Entry e) {
        if (e != null && e.pins > 0) {
            e.pins--;
        }
    }

    /**
     * Stores a snapshot, evicting least-recently-used unpinned entries until it fits. The cache owns it afterwards;
     * a snapshot that does not fit is closed.
     */
    void put(int[] tokens, InklingState.Snapshot snapshot) {
        Key key = new Key(tokens.length, hash(tokens, tokens.length));
        Entry old = entries.get(key);
        if (old != null && equal(old.tokens, tokens, tokens.length)) {
            snapshot.close();
            return;
        }
        while (used + snapshot.bytes() > budget) {
            Iterator<Map.Entry<Key, Entry>> it = entries.entrySet().iterator();
            boolean removed = false;
            while (it.hasNext()) {
                Map.Entry<Key, Entry> e = it.next();
                if (e.getValue().pins == 0) {
                    used -= e.getValue().snapshot.bytes();
                    e.getValue().snapshot.close();
                    it.remove();
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                snapshot.close();
                return;
            }
        }
        entries.put(key, new Entry(tokens, snapshot));
        used += snapshot.bytes();
        lengths.clear();
        for (Key k : entries.keySet()) {
            lengths.add(k.length());
        }
    }

    /** Frees every snapshot. */
    void clear() {
        for (Entry e : entries.values()) {
            e.snapshot.close();
        }
        entries.clear();
        lengths.clear();
        used = 0;
    }

    String stats() {
        return String.format("prefix cache: %d snapshots, %.0f MB of %.0f MB, %d hits / %d lookups", entries.size(),
                used / 1048576.0, budget / 1048576.0, hits, lookups);
    }
}
