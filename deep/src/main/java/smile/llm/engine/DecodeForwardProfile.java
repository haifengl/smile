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

    /**
     * Returns whether detailed decode phase timing is enabled.
     *
     * @return {@code true} when detailed decode phase timing is enabled.
     */
    public static boolean enabled() {
        return ENABLED;
    }

    /** Per-thread / per-rank nanosecond accumulators. */
    public static final class Snapshot {
        /** Embedding phase nanoseconds. */
        public long embedNs;
        /** Full-attention mixer nanoseconds. */
        public long fullAttnNs;
        /** Linear-attention / DeltaNet mixer nanoseconds. */
        public long linearAttnNs;
        /** MLP nanoseconds. */
        public long mlpNs;
        /** NCCL / TP all-reduce nanoseconds. */
        public long ncclNs;
        /** Final norm + lm_head nanoseconds. */
        public long lmHeadNs;
        /** DeltaNet sub-phases (sum ≈ {@link #linearAttnNs} when profiled). */
        public long deltaProjNs;
        /** DeltaNet causal conv nanoseconds. */
        public long deltaConvNs;
        /** DeltaNet gate nanoseconds. */
        public long deltaGateNs;
        /** DeltaNet recurrent nanoseconds. */
        public long deltaRecurrentNs;
        /** DeltaNet output projection nanoseconds. */
        public long deltaOutNs;

        /** Creates an empty timing/snapshot holder. */
        public Snapshot() {}

        /**
         * Element-wise max into {@code this}.
         *
         * @param other other snapshot to merge (ignored if {@code null}).
         */
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
            deltaProjNs = Math.max(deltaProjNs, other.deltaProjNs);
            deltaConvNs = Math.max(deltaConvNs, other.deltaConvNs);
            deltaGateNs = Math.max(deltaGateNs, other.deltaGateNs);
            deltaRecurrentNs = Math.max(deltaRecurrentNs, other.deltaRecurrentNs);
            deltaOutNs = Math.max(deltaOutNs, other.deltaOutNs);
        }

        /** @return {@link #embedNs} in milliseconds. */
        public long embedMs() {
            return embedNs / 1_000_000L;
        }

        /** @return {@link #fullAttnNs} in milliseconds. */
        public long fullAttnMs() {
            return fullAttnNs / 1_000_000L;
        }

        /** @return {@link #linearAttnNs} in milliseconds. */
        public long linearAttnMs() {
            return linearAttnNs / 1_000_000L;
        }

        /** @return {@link #mlpNs} in milliseconds. */
        public long mlpMs() {
            return mlpNs / 1_000_000L;
        }

        /** @return {@link #ncclNs} in milliseconds. */
        public long ncclMs() {
            return ncclNs / 1_000_000L;
        }

        /** @return {@link #lmHeadNs} in milliseconds. */
        public long lmHeadMs() {
            return lmHeadNs / 1_000_000L;
        }

        /** @return {@link #deltaProjNs} in milliseconds. */
        public long deltaProjMs() {
            return deltaProjNs / 1_000_000L;
        }

        /** @return {@link #deltaConvNs} in milliseconds. */
        public long deltaConvMs() {
            return deltaConvNs / 1_000_000L;
        }

        /** @return {@link #deltaGateNs} in milliseconds. */
        public long deltaGateMs() {
            return deltaGateNs / 1_000_000L;
        }

        /** @return {@link #deltaRecurrentNs} in milliseconds. */
        public long deltaRecurrentMs() {
            return deltaRecurrentNs / 1_000_000L;
        }

        /** @return {@link #deltaOutNs} in milliseconds. */
        public long deltaOutMs() {
            return deltaOutNs / 1_000_000L;
        }
    }

    private static final ThreadLocal<Snapshot> LOCAL = ThreadLocal.withInitial(Snapshot::new);

    /**
     * Adds embedding time on the current TP worker thread.
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addEmbed(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().embedNs += ns;
        }
    }

    /**
     * Adds full-attention mixer time (excluding the trailing all-reduce).
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addFullAttn(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().fullAttnNs += ns;
        }
    }

    /**
     * Adds DeltaNet / linear-attention mixer time (excluding all-reduce).
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addLinearAttn(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().linearAttnNs += ns;
        }
    }

    /**
     * Adds MLP time (excluding the trailing all-reduce).
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addMlp(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().mlpNs += ns;
        }
    }

    /**
     * Adds NCCL / TP all-reduce wall time.
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addNccl(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().ncclNs += ns;
        }
    }

    /**
     * Adds final norm + lm_head time.
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addLmHead(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().lmHeadNs += ns;
        }
    }

    /**
     * DeltaNet: input projections (qkv/z/b/a).
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addDeltaProj(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().deltaProjNs += ns;
        }
    }

    /**
     * DeltaNet: causal conv1d update / prefill.
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addDeltaConv(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().deltaConvNs += ns;
        }
    }

    /**
     * DeltaNet: beta/sigmoid + softplus gate {@code g}.
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addDeltaGate(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().deltaGateNs += ns;
        }
    }

    /**
     * DeltaNet: recurrent gated-delta rule (native or Java).
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addDeltaRecurrent(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().deltaRecurrentNs += ns;
        }
    }

    /**
     * DeltaNet: RMSNorm-gated + out projection.
     *
     * @param ns elapsed nanoseconds to add.
     */
    public static void addDeltaOut(long ns) {
        if (ENABLED && ns > 0) {
            LOCAL.get().deltaOutNs += ns;
        }
    }

    /**
     * Returns the current thread's accumulators and resets them for the next
     * forward. Call from the TP worker after {@code forward} returns.
     *
     * @return snapshot of accumulators, or {@code null} when profiling is off.
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
