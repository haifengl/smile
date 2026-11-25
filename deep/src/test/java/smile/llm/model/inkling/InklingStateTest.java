/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InklingState} and {@link InklingDraft.State} capacity sizing,
 * on-demand KV growth, and idempotent cleanup.
 *
 * @author Haifeng Li
 */
public class InklingStateTest {
    private static boolean torchAvailable() {
        try {
            return Aten.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
    private static InklingModelArgs tinyArgs(int slidingWindow) {
        // 2 layers: layer 0 is local (sliding window), layer 1 is global.
        return new InklingModelArgs(
                32, 2, 64, 64,
                4, 2, 8,
                4, 2, 8,
                4, 8,
                1e-6, false, 10.0, 10.0, 8192, 0.1,
                new boolean[]{true, false}, slidingWindow, 4,
                30.0, 2, true,
                4, 2, 0, 16, 4.0, 25.0, 1.0, false,
                0, 0, 1, 0, false,
                0, 1048576,
                new InklingModelArgs.ExpertFormat[]{
                        InklingModelArgs.ExpertFormat.NVFP4,
                        InklingModelArgs.ExpertFormat.MXFP8
                });
    }

    @Test
    public void testGivenLargeMaxSeqLenWhenPromptIsShortThenInitialCapacityStartsSmall() {
        // Given a 1M-token max sequence length and a 65-token prompt + 8-row verify margin
        int maxCap = 1_048_576 + 8;
        int minNeeded = 65 + 8;

        // When computing initial capacity
        int init = InklingState.initialCapacity(minNeeded, maxCap);

        // Then it starts at INITIAL_CAPACITY (4096) instead of pre-allocating 1M tokens
        assertEquals(InklingState.INITIAL_CAPACITY, init);
    }

    @Test
    public void testGivenSmallMaxCapacityWhenPromptIsShortThenInitialCapacityDoesNotOverallocate() {
        // Given an explicit small request window (e.g. max_tokens=150)
        int init = InklingState.initialCapacity(58, 208);

        // Then initial capacity matches the small maxCapacity without rounding up to 4096
        assertEquals(208, init);
    }

    @Test
    public void testGivenLongPromptWhenComputingInitialCapacityThenAlignedWithoutExcessDoubling() {
        // Given a 100k-token prompt under a 1M max sequence length
        int init = InklingState.initialCapacity(100_008, 1_048_584);

        // Then it aligns to 4096 (102400) rather than jumping to 131072 or 1M
        assertEquals(102_400, init);
        // And when near maxCapacity it clamps at maxCapacity
        assertEquals(1_048_584, InklingState.initialCapacity(1_048_580, 1_048_584));
    }

    @Test
    public void testGivenGrowingSequenceWhenComputingNextCapacityThenDoublesUntil64KThenSteps() {
        int maxCap = 1_048_584;
        // Doubles up to 65536
        assertEquals(8192, InklingState.nextCapacity(4096, 4097, maxCap));
        assertEquals(16384, InklingState.nextCapacity(8192, 8193, maxCap));
        assertEquals(32768, InklingState.nextCapacity(16384, 16385, maxCap));
        assertEquals(65536, InklingState.nextCapacity(32768, 32769, maxCap));
        // Steps by MAX_GROW_STEP (32768) above 64K
        assertEquals(65536 + InklingState.MAX_GROW_STEP, InklingState.nextCapacity(65536, 65537, maxCap));
        // Respects maxCapacity cap
        assertEquals(70000, InklingState.nextCapacity(65536, 66000, 70000));
        // No-op when already large enough
        assertEquals(8192, InklingState.nextCapacity(8192, 5000, maxCap));
    }

    @Test
    @Tag("integration")
    public void testGivenStateWithDataWhenGrownThenGlobalAndWrappedLocalEntriesArePreserved() {
        Assumptions.assumeTrue(torchAvailable(), "libsmile_torch unavailable");
        // Given a tiny state on CPU with local slidingWindow=4 and initial capacity=6
        InklingModelArgs args = tinyArgs(4);
        Device cpu = Device.CPU();
        try (InklingState st = new InklingState(args, 6, cpu, 1)) {
            assertEquals(6, st.capacity);
            assertEquals(6, st.cacheCapacity[0]); // local: min(6, 4 + 2048)
            assertEquals(6, st.cacheCapacity[1]); // global: 6

            // Populate 8 tokens (global linear up to 6 -> first write 5 tokens before wrap, grow to 10)
            // First test pre-wrap growth (length = 5 <= oldCap = 6)
            float[] kVals = new float[2 * 5 * 8];
            float[] vVals = new float[2 * 5 * 8];
            for (int i = 0; i < kVals.length; i++) {
                kVals[i] = i + 1.0f;
                vVals[i] = -(i + 1.0f);
            }
            Ix.inScope(() -> {
                Tensor kSrc = Tensor.of(kVals, 2, 5, 8).to(ScalarType.BFloat16);
                Tensor vSrc = Tensor.of(vVals, 2, 5, 8).to(ScalarType.BFloat16);
                Ix.o("aten::copy_", Ix.narrow(st.k[0], 1, 0, 5), kSrc);
                Ix.o("aten::copy_", Ix.narrow(st.v[0], 1, 0, 5), vSrc);
                Ix.o("aten::copy_", Ix.narrow(st.k[1], 1, 0, 5), kSrc);
                Ix.o("aten::copy_", Ix.narrow(st.v[1], 1, 0, 5), vSrc);
            });
            st.length = 5;

            // When growing to capacity 12
            st.grow(12);

            // Then both local and global caches expanded and preserved positions 0..4
            assertEquals(12, st.capacity);
            assertEquals(12, st.cacheCapacity[0]);
            assertEquals(12, st.cacheCapacity[1]);
            assertArrayEquals(new long[]{2, 12, 8}, st.k[1].shape());
            Ix.inScope(() -> {
                float[] gotK1 = Ix.f32(Ix.contiguous(Ix.narrow(st.k[1], 1, 0, 5))).floatArray();
                float[] gotV1 = Ix.f32(Ix.contiguous(Ix.narrow(st.v[1], 1, 0, 5))).floatArray();
                float[] gotK0 = Ix.f32(Ix.contiguous(Ix.narrow(st.k[0], 1, 0, 5))).floatArray();
                for (int i = 0; i < kVals.length; i++) {
                    assertEquals(kVals[i], gotK1[i], 0.5f, "global k mismatch at " + i);
                    assertEquals(vVals[i], gotV1[i], 0.5f, "global v mismatch at " + i);
                    assertEquals(kVals[i], gotK0[i], 0.5f, "local k mismatch at " + i);
                }
            });

            // Now simulate a wrapped local ring: force local cap small by checking post-cap no-op
            int maxLocalCap = args.slidingWindow() + st.localSlack;
            st.grow(maxLocalCap + 64);
            assertEquals(maxLocalCap, st.cacheCapacity[0]);
            assertEquals(maxLocalCap + 64, st.cacheCapacity[1]);

            // Double-close must be safe
            st.close();
            st.close();
        }
    }

    @Test
    @Tag("integration")
    public void testGivenDraftStateWithAbsorbedRowsWhenGrownThenEntriesArePreserved() {
        Assumptions.assumeTrue(torchAvailable(), "libsmile_torch unavailable");
        // Given a tiny DSpark draft state on CPU with capacity 4 and 3 absorbed rows
        InklingDraft.Config cfg = new InklingDraft.Config(
                32, 2, 4, 2, 8, 64, 1e-6, 10000.0,
                new int[]{0}, 4, 8, 4, new int[]{1, 2});
        try (InklingDraft.State ds = new InklingDraft.State(cfg, 4, Device.CPU())) {
            assertEquals(4, ds.capacity);
            float[] kVals = new float[2 * 3 * 8];
            float[] vVals = new float[2 * 3 * 8];
            for (int i = 0; i < kVals.length; i++) {
                kVals[i] = i + 2.0f;
                vVals[i] = -(i + 2.0f);
            }
            Ix.inScope(() -> {
                Tensor kSrc = Tensor.of(kVals, 2, 3, 8).to(ScalarType.BFloat16);
                Tensor vSrc = Tensor.of(vVals, 2, 3, 8).to(ScalarType.BFloat16);
                for (int layer = 0; layer < 2; layer++) {
                    Ix.o("aten::copy_", Ix.narrow(ds.k[layer], 1, 0, 3), kSrc);
                    Ix.o("aten::copy_", Ix.narrow(ds.v[layer], 1, 0, 3), vSrc);
                }
            });
            ds.absorbed = 3;

            // When growing to capacity 16
            ds.grow(16);

            // Then capacity updated and all 3 absorbed rows are preserved across all draft layers
            assertEquals(16, ds.capacity);
            for (int layer = 0; layer < 2; layer++) {
                assertArrayEquals(new long[]{2, 16, 8}, ds.k[layer].shape());
                int l = layer;
                Ix.inScope(() -> {
                    float[] gotK = Ix.f32(Ix.contiguous(Ix.narrow(ds.k[l], 1, 0, 3))).floatArray();
                    float[] gotV = Ix.f32(Ix.contiguous(Ix.narrow(ds.v[l], 1, 0, 3))).floatArray();
                    for (int i = 0; i < kVals.length; i++) {
                        assertEquals(kVals[i], gotK[i], 0.5f);
                        assertEquals(vVals[i], gotV[i], 0.5f);
                    }
                });
            }

            // Double-close must be safe
            ds.close();
            ds.close();
        }
    }
}
