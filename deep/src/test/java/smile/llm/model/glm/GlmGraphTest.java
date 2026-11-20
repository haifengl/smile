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
 * CUDA-graph decode of the 4-layer slice: replaying the captured step for two requests (alone in a bucket of 2, and padded
 * to a bucket of 4) must give the logits of the eager batched step, over several steps (the caches grow, the
 * pool-completing step is included). Opt-in: {@code GLM_CUDA=true}.
 */
public class GlmGraphTest {
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

    @Test
    public void testGivenGraphThenReplayEqualsEagerBatchedDecode() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Path dir = Path.of(System.getenv().getOrDefault("GLM_DIR", "/raid/haili/glm/raw"));
        Path fx = Path.of(System.getenv().getOrDefault("GLM_FIXTURES", "/raid/haili/glm/fixtures"));
        Assumptions.assumeTrue(Files.exists(fx.resolve("slice4f_meta.json")), "no fixtures");
        JsonNode meta = new ObjectMapper().readTree(fx.resolve("slice4f_meta.json").toFile());
        int[] promptA = ints(meta.get("short").get("ids"), 1000);
        int[] promptB = ints(meta.get("long").get("ids"), 70);
        int steps = 9;                                   // crosses several pool boundaries
        int[] tokA = new int[steps];
        int[] tokB = new int[steps];
        for (int i = 0; i < steps; i++) {
            tokA[i] = 1000 + 37 * i;
            tokB[i] = 4000 + 91 * i;
        }
        GlmModelArgs args = GlmModelArgs.load(new File(dir.toFile(), "config.json")).slice(4);
        try (GlmModel model = new GlmModel(dir, args, Device.CUDA((byte) 0), null, 0, 1, 4)) {
            Tensor[] eager = new Tensor[steps];
            try (GlmModel.Session a = model.newSession(); GlmModel.Session b = model.newSession()) {
                model.forward(promptA, a, null);
                model.forward(promptB, b, null);
                for (int s = 0; s < steps; s++) {
                    eager[s] = Aten.one("aten::clone", model.logits(model.forwardBatch(new int[]{tokA[s], tokB[s]},
                            new GlmModel.Session[]{a, b})));
                }
            }
            for (int bucket : new int[]{2, 4}) {
                GlmDecodeGraph graph = GlmDecodeGraph.capture(model, bucket, 512, 0, null);
                try (GlmModel.Session a = model.newSession(); GlmModel.Session b = model.newSession()) {
                    model.forward(promptA, a, null);
                    model.forward(promptB, b, null);
                    double worst = 0;
                    for (int s = 0; s < steps; s++) {
                        Tensor got = graph.replay(new int[]{tokA[s], tokB[s]}, new GlmModel.Session[]{a, b});
                        double e = rel(got, eager[s]);
                        worst = Math.max(worst, e);
                        assertTrue(e < 5e-3, "bucket " + bucket + " step " + s + " logits rel err " + e);
                    }
                    System.out.printf("graph bucket %d: worst logits rel err vs eager %.2e over %d steps%n", bucket, worst, steps);
                    assertEquals(promptA.length + steps, a.length());
                    assertEquals(promptB.length + steps, b.length());
                }
            }
        }
    }
}
