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
package smile.llm.engine;

/**
 * Optional decode-forward phase timers (attn / DeltaNet / MLP / NCCL).
 *
 * <p>Enable with {@code SMILE_DECODE_PROFILE=1}. Each TP rank accumulates on its
 * worker thread; {@link smile.llm.model.qwen.Qwen} merges the max across ranks
 * into {@link DecodeStepTiming} (NCCL barriers make wall times a reasonable
 * proxy for GPU work between all-reduces).
 *
 * @author Haifeng Li
 */
public final class DecodeForwardProfile {
    private static final boolean ENABLED = "1".equals(System.getenv("SMILE_DECODE_PROFILE"));

    private DecodeForwardProfile() {}

    /** @return {@code true} when detailed decode phase timing is enabled. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** Per-thread / per-rank nanosecond accumulators. */
    public static final class Snapshot {
        public long embedNs;
        public long fullAttnNs;
        public long linearAttnNs;
        public long mlpNs;
        public long ncclNs;
        public long lmHeadNs;

        /** Element-wise max into {@code this}. */
        public void maxWith(Snapshot other) {
            if (other == null) {
                return;
            }
            embedNs = Math.max(embedNs, other.embedNs);
            fullAttnNs = Math.max(fullAttnNs, other.fullAttnNs);
            linearAttnNs = Math.max(linearAttnNs, other.linearAttnNs);
            mlpNs = Math.max(mlpNs, other.mlpNs);
            ncclNs = Math.max(ncclNs, other.ncclNs);
            lmHeadNs = Math.max(lmHeadNs, other.lmHeadNs);
        }

        long embedMs() {
            return embedNs / 1_000_000L;
        }

        long fullAttnMs() {
            return fullAttnNs / 1_000_000L;
        }

        long linearAttnMs() {
            return linearAttnNs / 1_000_000L;
        }

        long mlpMs() {
            return mlpNs / 1_000_000L;
        }

        long ncclMs() {
            return ncclNs / 1_000_000L;
        }

        long lmHeadMs() {
            return lmHeadNs / 1_000_000L;
        }
    }

    private static final ThreadLocal<Snapshot> LOCAL = ThreadLocal.withInitial(Snapshot::new);

    /** Adds embedding time on the current TP worker thread. */
    public static void addEmbed(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().embedNs += ns;
        }
    }

    /** Adds full-attention mixer time (excluding the trailing all-reduce). */
    public static void addFullAttn(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().fullAttnNs += ns;
        }
    }

    /** Adds DeltaNet / linear-attention mixer time (excluding all-reduce). */
    public static void addLinearAttn(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().linearAttnNs += ns;
        }
    }

    /** Adds MLP time (excluding the trailing all-reduce). */
    public static void addMlp(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().mlpNs += ns;
        }
    }

    /** Adds NCCL / TP all-reduce wall time. */
    public static void addNccl(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().ncclNs += ns;
        }
    }

    /** Adds final norm + lm_head time. */
    public static void addLmHead(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().lmHeadNs += ns;
        }
    }

    /**
     * Returns the current thread's accumulators and resets them for the next
     * forward. Call from the TP worker after {@code forward} returns.
     */
    public static Snapshot snapshotAndReset() {
        if (!ENABLED) {
            return null;
        }
        Snapshot s = LOCAL.get();
        LOCAL.set(new Snapshot());
        return s;
    }
}
