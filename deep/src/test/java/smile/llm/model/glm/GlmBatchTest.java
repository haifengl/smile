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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Batched decode of the 4-layer slice: two requests at different positions advanced together by
 * {@link GlmModel#forwardBatch} must match advancing them one at a time, and the row order must not matter (rows
 * are bound to their own state). Runs on the CPU in float32 (tight) and, with {@code GLM_CUDA=true}, on a GPU.
 */
public class GlmBatchTest {
    private static int[] ints(JsonNode a, int limit) {
        int[] r = new int[Math.min(limit, a.size())];
        for (int i = 0; i < r.length; i++) {
            r[i] = a.get(i).asInt();
        }
        return r;
    }

    private static double rel(Tensor a, Tensor b) {
        Tensor x = a.to(ScalarType.Float).to(Device.CPU());
        Tensor y = b.to(ScalarType.Float).to(Device.CPU());
        return Aten.one("aten::linalg_vector_norm", Aten.one("aten::sub.Tensor", x, y), 2.0).doubleValue()
                / Aten.one("aten::linalg_vector_norm", y, 2.0).doubleValue();
    }

    private static Tensor row(Tensor t, int i) {
        return Aten.one("aten::clone", Aten.one("aten::narrow", t, 0L, (long) i, 1L)).to(Device.CPU());
    }

    private void run(Device device, double tol) throws Exception {
        Path dir = Path.of(System.getenv().getOrDefault("GLM_DIR", "/raid/haili/glm/raw"));
        Path fx = Path.of(System.getenv().getOrDefault("GLM_FIXTURES", "/raid/haili/glm/fixtures"));
        Assumptions.assumeTrue(Files.exists(fx.resolve("slice4f_meta.json")) && Files.exists(dir.resolve("model.safetensors.index.json")), "no fixtures");
        JsonNode meta = new ObjectMapper().readTree(fx.resolve("slice4f_meta.json").toFile());
        int[] promptA = ints(meta.get("short").get("ids"), 1000);
        int[] promptB = ints(meta.get("long").get("ids"), 70);          // a different length: other positions
        int[] stepsA = {7522, 4031, 79810};
        int[] stepsB = {1234, 5678, 91011};
        GlmModelArgs args = GlmModelArgs.load(new File(dir.toFile(), "config.json")).slice(4);
        try (GlmModel model = new GlmModel(dir, args, device)) {
            // solo reference
            Tensor[] soloA = new Tensor[3];
            Tensor[] soloB = new Tensor[3];
            try (GlmModel.Session a = model.newSession(); GlmModel.Session b = model.newSession()) {
                model.forward(promptA, a, null);
                model.forward(promptB, b, null);
                for (int s = 0; s < 3; s++) {
                    soloA[s] = row(model.forwardBatch(new int[]{stepsA[s]}, new GlmModel.Session[]{a}), 0);
                    soloB[s] = row(model.forwardBatch(new int[]{stepsB[s]}, new GlmModel.Session[]{b}), 0);
                }
            }
            // batched, rows in both orders (new sessions: slots are reused after close)
            for (boolean swapped : new boolean[]{false, true}) {
                try (GlmModel.Session a = model.newSession(); GlmModel.Session b = model.newSession()) {
                    model.forward(promptA, a, null);
                    model.forward(promptB, b, null);
                    for (int s = 0; s < 3; s++) {
                        GlmModel.Session[] ss = swapped ? new GlmModel.Session[]{b, a} : new GlmModel.Session[]{a, b};
                        int[] ids = swapped ? new int[]{stepsB[s], stepsA[s]} : new int[]{stepsA[s], stepsB[s]};
                        Tensor h = model.forwardBatch(ids, ss);
                        double ea = rel(row(h, swapped ? 1 : 0), soloA[s]);
                        double eb = rel(row(h, swapped ? 0 : 1), soloB[s]);
                        System.out.printf("%s step %d: request A %.2e, request B %.2e vs solo%n", swapped ? "swapped" : "ordered", s, ea, eb);
                        assertTrue(ea < tol && eb < tol, "batched vs solo at step " + s + ": " + ea + ", " + eb);
                    }
                    assertEquals(promptA.length + 3, a.length());
                    assertEquals(promptB.length + 3, b.length());
                }
            }
            // state of a closed session must not leak into the next one using its slot
            try (GlmModel.Session a = model.newSession()) {
                model.forward(promptA, a, null);
                Tensor h = row(model.forwardBatch(new int[]{stepsA[0]}, new GlmModel.Session[]{a}), 0);
                assertTrue(rel(h, soloA[0]) < tol, "a reused slot gives the same result as a fresh one");
            }
        }
    }

    @Test
    public void testGivenTwoRequestsThenBatchedDecodeEqualsSoloOnCpu() throws Exception {
        run(Device.CPU(), 1e-4);
    }

    @Test
    public void testGivenTwoRequestsThenBatchedDecodeEqualsSoloOnGpu() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        run(Device.CUDA((byte) 0), 2e-2);
    }
}
