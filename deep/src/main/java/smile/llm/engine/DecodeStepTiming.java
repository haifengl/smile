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
 * Per-thread decode step timing filled by {@link smile.llm.model.qwen.Qwen}
 * and read by {@link InferenceEngine}.
 *
 * @author Haifeng Li
 */
public final class DecodeStepTiming {
    private static final ThreadLocal<DecodeStepTiming> CURRENT = new ThreadLocal<>();

    /** KV activate + host tensor prep before TP forward. */
    public long prepNs;
    /** Wall time for TP forward ({@code max(rank)} when tensor-parallel). */
    public long forwardNs;
    /** Slowest TP rank forward (GPU work on critical path). */
    public long slowestRankNs;
    /** Thread-pool sync overhead ({@code forwardNs - slowestRankNs}). */
    public long tpBarrierNs;
    /** Logits row extraction / copy after forward. */
    public long logitsNs;
    /** Greedy / sampled token selection after forward. */
    public long sampleNs;
    /**
     * Max-across-ranks phase breakdown when {@code SMILE_DECODE_PROFILE=1};
     * otherwise {@code null}.
     */
    public DecodeForwardProfile.Snapshot profile;

    /** @return timing holder for the current decode worker thread. */
    public static DecodeStepTiming current() {
        return CURRENT.get();
    }

    /** Clears the thread-local holder (call after logging). */
    public static void clear() {
        CURRENT.remove();
    }

    static DecodeStepTiming begin() {
        DecodeStepTiming t = new DecodeStepTiming();
        CURRENT.set(t);
        return t;
    }

    long prepMs() {
        return prepNs / 1_000_000L;
    }

    long forwardMs() {
        return forwardNs / 1_000_000L;
    }

    long tpBarrierMs() {
        return tpBarrierNs / 1_000_000L;
    }

    long slowestRankMs() {
        return slowestRankNs / 1_000_000L;
    }

    long logitsMs() {
        return logitsNs / 1_000_000L;
    }

    long sampleMs() {
        return sampleNs / 1_000_000L;
    }

    long totalMs() {
        return (prepNs + forwardNs + logitsNs + sampleNs) / 1_000_000L;
    }
}
