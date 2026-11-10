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
package smile.llm.model.deepseek;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Opt-in ({@code DEEPSEEK_EXECUTOR=cpu|cuda}): batched decode of one row equals the single-sequence path. Separate class
 * so it runs in a JVM of its own: the weights of a model loaded by another test are only freed by the cleaner and two
 * models do not fit on one 40 GB card.
 *
 * @author Haifeng Li
 */
public class DeepSeekBatchLegacyTest {
    private static final int[] FORCED = {84616, 10557, 121804};

    private static float[] cpu(Tensor logits) {
        return Dsv.host(logits).floatArray();
    }

    /** A batch of one decode row is the legacy single-sequence path, bit for bit (same shapes, same kernels). */
    @Test
    public void testGivenBatchOfOneThenEqualsLegacyForward() throws Exception {
        String mode = System.getenv().getOrDefault("DEEPSEEK_EXECUTOR", "");
        Assumptions.assumeTrue(mode.equals("cpu") || mode.startsWith("cuda"), "DEEPSEEK_EXECUTOR not set");
        Path dir = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"));
        Path fx = Path.of(System.getenv().getOrDefault("DEEPSEEK_FIXTURES", "/raid/haili/dsv4/fixtures"));
        Assumptions.assumeTrue(Files.exists(fx.resolve("slice4_prefill.safetensors")), "no fixture");
        long[] idv = SafeTensors.read(fx.resolve("slice4_prefill.safetensors").toString(), Device.CPU()).tensors()
                .get("ids").longArray();
        int[] a = new int[idv.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = (int) idv[i];
        }
        var r = SafeTensors.read(fx.resolve("engram_rows_layer1.safetensors").toString(), Device.CPU()).tensors();
        DeepSeekEngram.Rows rows = new DeepSeekEngram.InMemoryRows(r.get("rows"), r.get("w"), r.get("s"));
        boolean gpu = mode.startsWith("cuda");
        Dsv.setDevice(gpu ? Device.CUDA((byte) 0) : Device.CPU());
        Dsv.marlin = gpu;
        try {
            DeepSeekModelArgs args = DeepSeekModelArgs.load(dir.resolve("config.json").toFile()).slice(4).withMaxSeqLen(256);
            try (DeepSeekModel m = new DeepSeekModel(args, dir, l -> rows)) {
                DeepSeekModel.Session s1 = m.newSession();
                DeepSeekModel.Session s2 = m.newSession();
                m.use(s1);
                m.forward(a, 0, null);
                m.use(s2);
                m.forward(a, 0, null);
                for (int i = 0; i < FORCED.length; i++) {
                    m.use(s1);
                    float[] legacy = cpu(m.forward(new int[]{FORCED[i]}, a.length + i, null));
                    float[] batched = cpu(m.forwardBatch(new int[]{FORCED[i]}, new int[]{a.length + i},
                            new DeepSeekModel.Session[]{s2}));
                    assertArrayEquals(legacy, batched, 1e-5f, "step " + (i + 1));
                }
                s1.close();
                s2.close();
            }
        } finally {
            Dsv.setDevice(Device.CPU());
            Dsv.marlin = false;
        }
    }
}
