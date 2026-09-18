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

import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.attention.FlashInferWorkspace;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SMILE_VERIFY_CUDA_GRAPH Stage 1 go/no-go: the new graph-capturable multi-token
 * (S&gt;1) paged-attention kernel ({@code smile_flashinfer_paged_attention_verify_cuda},
 * via {@link Native#flashInferAttentionVerifyCapturable}) must produce numerically
 * identical (bf16-rounding-level) output to the existing, proven eager gather+SDPA
 * path ({@code run_batch_prefill_sdpa}, via {@link Native#flashInferAttentionPagedRaw})
 * on the same raw K/V pages and CSR — in eager mode, no CUDA graph capture involved
 * yet. This is deliberately isolated from {@code QwenModel}/{@code Qwen}: it builds
 * K/V pages and CSR tensors by hand rather than through a real {@code KvCachePool}.
 *
 * <p>Per the staged plan, do not proceed to Stage 2 (capture/replay) until every
 * case here passes on real hardware — this environment has no CUDA, so these
 * tests skip themselves via {@link org.junit.jupiter.api.Assumptions} rather than
 * fail; they must actually be run (not just compiled) in the target CUDA
 * environment before trusting Stage 1.
 *
 * @author Haifeng Li
 */
public class VerifyCudaGraphStage1KernelTest {

    /** Max abs diff tolerance for bf16 compute (both paths run the same dtype). */
    private static final double TOLERANCE = 5e-2;

    private static boolean cudaAvailable() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0;
    }

    /**
     * Max absolute difference between two same-shape tensors, computed by
     * reading both back to Java arrays rather than {@code Tensor.max()} +
     * {@code doubleValue()}. {@code smile_tensor_max} is not a true global
     * reduction in the current native binary — it returns a same-shape (not
     * scalar) result, and the native {@code item_*} call behind
     * {@code doubleValue()} does not catch its own exception on a non-scalar
     * tensor, so it aborts the whole JVM (SIGABRT) instead of throwing a
     * catchable Java exception. Reading arrays and reducing in Java sidesteps
     * this entirely, regardless of {@code Tensor.max()}'s actual semantics.
     */
    private static double maxAbsDiff(Tensor a, Tensor b) {
        try (Tensor diff = a.to(ScalarType.Float).sub(b.to(ScalarType.Float)).abs();
             Tensor cpu = diff.to(Device.CPU())) {
            double max = 0.0;
            for (float v : cpu.floatArray()) {
                double av = Math.abs((double) v);
                if (av > max) {
                    max = av;
                }
            }
            return max;
        }
    }

    /**
     * @param pageSize      tokens per KV page.
     * @param numSlots      total slots allocated (must be a multiple of pageSize).
     * @param existingLen   cached length before the verify window (row 0 only, B=1).
     * @param windowLen     S, the verify window length (n+1 for MTP).
     * @param numQoHeads    query heads.
     * @param numKvHeads    KV heads (numQoHeads % numKvHeads == 0 for GQA).
     */
    private record Config(int pageSize, int numSlots, int existingLen, int windowLen,
                          int numQoHeads, int numKvHeads) {
        int headDim() {
            return 64;
        }

        int totalLen() {
            return existingLen + windowLen;
        }

        int numPages() {
            return (totalLen() + pageSize - 1) / pageSize;
        }

        int lastPageLen() {
            int full = numPages() - 1;
            return totalLen() - full * pageSize;
        }
    }

    private void runAndCompare(Config cfg) {
        assumeTrue(cudaAvailable(), "CUDA not available in this environment");
        Device device = Device.CUDA();

        try (FlashInferWorkspace ws = FlashInferWorkspace.create(0, 0)) {
            assumeTrue(ws != null, "FlashInfer not compiled into libsmile_torch");

            int[] kvIndicesArr = new int[cfg.numPages()];
            for (int p = 0; p < cfg.numPages(); p++) {
                kvIndicesArr[p] = p; // physical page id == logical page id (contiguous pool)
            }
            int[] kvIndptrArr = {0, cfg.numPages()};
            int[] kvLastPageLenArr = {cfg.lastPageLen()};
            int[] qoIndptrArr = {0, cfg.windowLen()};

            try (Tensor query = Tensor.randn(1, cfg.numQoHeads(), cfg.windowLen(), cfg.headDim())
                            .to(device, ScalarType.BFloat16);
                 Tensor kCache = Tensor.randn(cfg.numSlots(), cfg.numKvHeads(), cfg.headDim())
                            .to(device, ScalarType.BFloat16);
                 Tensor vCache = Tensor.randn(cfg.numSlots(), cfg.numKvHeads(), cfg.headDim())
                            .to(device, ScalarType.BFloat16);
                 Tensor kvIndptr = Tensor.of(kvIndptrArr).to(device);
                 Tensor kvIndices = Tensor.of(kvIndicesArr).to(device);
                 Tensor kvLastPageLen = Tensor.of(kvLastPageLenArr).to(device);
                 Tensor qoIndptr = Tensor.of(qoIndptrArr).to(device)) {

                Tensor eager = Native.flashInferAttentionPagedRaw(
                        query, kCache, vCache, kvIndptr, kvIndices, kvLastPageLen,
                        cfg.pageSize(), cfg.numKvHeads(), cfg.headDim(), cfg.totalLen(),
                        /*scale=*/-1.0, /*isCausal=*/true, ws.handle());
                Tensor capturable;
                try {
                    capturable = Native.flashInferAttentionVerifyCapturable(
                            query, kCache, vCache, qoIndptr, kvIndptr, kvIndices, kvLastPageLen,
                            cfg.pageSize(), cfg.numKvHeads(), cfg.headDim(), cfg.windowLen(),
                            /*scale=*/-1.0, /*kScale=*/1.0f, /*vScale=*/1.0f, ws.handle());
                } catch (RuntimeException e) {
                    eager.close();
                    throw e;
                }

                try (eager; capturable) {
                    double d = maxAbsDiff(eager, capturable);
                    assertTrue(d < TOLERANCE,
                            "capturable-kernel vs eager-SDPA max abs diff=" + d
                                    + " (cfg=" + cfg + ")");
                }
            }
        }
    }

    @Test
    public void testGivenSamePageMhaWhenComparedToEagerThenMatches() {
        // page 0 has room for the whole [existingLen, existingLen+S) window: no
        // page-boundary crossing, MHA (no GQA), exercises the common case.
        runAndCompare(new Config(8, 8, 4, 2, 2, 2));
    }

    @Test
    public void testGivenCrossPageBoundaryMhaWhenComparedToEagerThenMatches() {
        // existingLen=3 (page 0 partially filled) + S=3 spans into page 1 —
        // exercises multi-page paged_kv_t / CSR construction.
        runAndCompare(new Config(4, 8, 3, 3, 2, 2));
    }

    @Test
    public void testGivenGqaSamePageWhenComparedToEagerThenMatches() {
        // group_size=2 (numQoHeads=4, numKvHeads=2) — matches real Qwen GQA shapes.
        runAndCompare(new Config(8, 8, 5, 2, 4, 2));
    }

    @Test
    public void testGivenLargerWindowWhenComparedToEagerThenMatches() {
        // S=4 (n=3 drafts), still within one page — the largest window this
        // plan's Stage 5 default range (n up to ~4) is expected to exercise.
        runAndCompare(new Config(8, 8, 3, 4, 2, 2));
    }
}
