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
package smile.llm.model.minimax;

import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in GPU check that batches of any size (padded to a power of two for CUDA graphs) decode like the same rows
 * decoded one by one. Environment: {@code MINIMAX_MODEL_DIR} (a Marlin-loadable MXFP8 checkpoint),
 * {@code MINIMAX_DEVICES}.
 *
 * @author Haifeng Li
 */
public class MiniMaxGraphBatchTest {
    @Test
    public void testGivenOddBatchSizesWhenDecodedTogetherThenMatchesSingleRowDecode() throws Exception {
        String dir = System.getenv("MINIMAX_MODEL_DIR");
        String devList = System.getenv("MINIMAX_DEVICES");
        assumeTrue(dir != null && devList != null, "MINIMAX_* not set");
        String[] parts = devList.split(",");
        byte[] devices = new byte[parts.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = Byte.parseByte(parts[i].trim());
        }
        try (MiniMax m = MiniMax.build(dir, 8, 4096, devices, 0.6, 1)) {
            for (int n : new int[]{3, 5, 6, 7}) {
                int[][] prompts = new int[n][];
                int[] ids = new int[n];
                int[] pos = new int[n];
                int[] tok = new int[n];
                for (int i = 0; i < n; i++) {
                    prompts[i] = new int[20 + 37 * i];
                    for (int j = 0; j < prompts[i].length; j++) {
                        prompts[i][j] = 100 + (i * 31 + j * 7) % 900;
                    }
                    ids[i] = m.bind(prompts[i], 512);
                    m.prefill(ids[i], prompts[i], 0).close();
                    pos[i] = prompts[i].length;
                    tok[i] = 5 + i;
                }
                try {
                    for (int step = 0; step < 3; step++) {
                        float[][] together = rows(m.decodeStep(ids, tok, pos));
                        for (int i = 0; i < n; i++) {
                            float[] alone = rows(m.decodeStep(new int[]{ids[i]}, new int[]{tok[i]},
                                    new int[]{pos[i]}))[0];
                            double worst = 0;
                            int argA = 0;
                            int argT = 0;
                            for (int v = 0; v < alone.length; v++) {
                                worst = Math.max(worst, Math.abs(alone[v] - together[i][v]));
                                if (alone[v] > alone[argA]) argA = v;
                                if (together[i][v] > together[i][argT]) argT = v;
                            }
                            assertTrue(worst < 0.25, "batch " + n + " row " + i + " step " + step + " differs by " + worst);
                            assertEquals(argA, argT, "batch " + n + " row " + i + " step " + step);
                            tok[i] = argA;
                            pos[i]++;
                        }
                    }
                } finally {
                    for (int id : ids) {
                        m.evict(id);
                    }
                }
            }
        }
    }

    private static float[][] rows(Tensor logits) {
        try (logits; Tensor cpu = logits.to(Device.CPU())) {
            long[] shape = cpu.shape();
            float[] flat = cpu.floatArray();
            int rows = (int) shape[0];
            int width = flat.length / rows;
            float[][] out = new float[rows][width];
            for (int i = 0; i < rows; i++) {
                System.arraycopy(flat, i * width, out[i], 0, width);
            }
            return out;
        }
    }
}
