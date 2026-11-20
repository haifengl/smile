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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.llm.engine.GenerationRequest;
import smile.llm.engine.InferenceEngine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The 4-layer slice behind {@link InferenceEngine}: concurrent requests with chunked prefill and batched decode must
 * produce the oracle's greedy tokens (CPU, float32) - the engine's scheduling, the chunk-carried states and the
 * request slots are all exercised. With {@code GLM_CUDA=true} the same runs on a GPU, comparing the first token only
 * (a truncated slice is chaotic in bf16). {@code GLM_LONG=1} adds the 4500-token prompt.
 */
public class GlmExecutorTest {
    private static int[] ints(JsonNode a) {
        int[] r = new int[a.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = a.get(i).asInt();
        }
        return r;
    }

    private void run(boolean cuda) throws Exception {
        Path dir = Path.of(System.getenv().getOrDefault("GLM_DIR", "/raid/haili/glm/raw"));
        Path fx = Path.of(System.getenv().getOrDefault("GLM_FIXTURES", "/raid/haili/glm/fixtures"));
        Assumptions.assumeTrue(Files.exists(fx.resolve("slice4f_meta.json")) && Files.exists(dir.resolve("model.safetensors.index.json")), "no fixtures");
        JsonNode meta = new ObjectMapper().readTree(fx.resolve("slice4f_meta.json").toFile());
        int[] shortPrompt = ints(meta.get("short").get("ids"));
        int[] shortExpected = ints(meta.get("short").get("generated"));
        boolean withLong = System.getenv("GLM_LONG") != null;
        int[] longPrompt = withLong ? ints(meta.get("long").get("ids")) : shortPrompt;
        int[] longExpected = withLong ? ints(meta.get("long").get("generated")) : shortExpected;
        int ranks = Integer.parseInt(System.getenv().getOrDefault("GLM_EP", "1"));
        byte[] devices = null;
        if (cuda) {
            devices = new byte[Math.max(1, ranks)];
            for (int i = 0; i < devices.length; i++) {
                devices[i] = (byte) i;
            }
        }
        try (Glm model = Glm.build(dir.toString(), 4, 8192, devices, 4)) {
            // a prefill budget of 16 tokens splits the 23-token prompt into two chunks
            var engine = new InferenceEngine(model, 4, 4, withLong ? 512 : 16, 0);
            var a = engine.submit(GenerationRequest.ofTokens(shortPrompt, shortExpected.length, 0.0, 1.0, false, 0, null));
            var b = engine.submit(GenerationRequest.ofTokens(longPrompt, longExpected.length, 0.0, 1.0, false, 0, null));
            var c = engine.submit(GenerationRequest.ofTokens(shortPrompt, shortExpected.length, 0.0, 1.0, false, 0, null));
            int[] ra;
            long timeout = Long.parseLong(System.getenv().getOrDefault("GLM_TIMEOUT_S", "1800"));
            try {
                ra = a.future().get(timeout, TimeUnit.SECONDS).completionTokens();
            } catch (java.util.concurrent.TimeoutException e) {
                // a hang: show where every thread is, then fail
                for (var t : Thread.getAllStackTraces().entrySet()) {
                    if (t.getKey().getName().startsWith("glm") || t.getKey().getName().toLowerCase().contains("engine")) {
                        System.out.println("THREAD " + t.getKey().getName());
                        for (StackTraceElement f : t.getValue()) {
                            System.out.println("    at " + f);
                        }
                    }
                }
                throw e;
            }
            int[] rb = b.future().get(30, TimeUnit.MINUTES).completionTokens();
            int[] rc = c.future().get(30, TimeUnit.MINUTES).completionTokens();
            System.out.println("engine tokens: " + java.util.Arrays.toString(ra) + " " + java.util.Arrays.toString(rb)
                    + " " + java.util.Arrays.toString(rc));
            if (cuda) {
                assertEquals(shortExpected[0], ra[0]);
                assertEquals(longExpected[0], rb[0]);
                assertEquals(shortExpected.length, ra.length);
            } else {
                assertArrayEquals(shortExpected, ra);
                assertArrayEquals(longExpected, rb);
                assertArrayEquals(shortExpected, rc);
            }
            // all slots are free again
            assertEquals(4, model.maxSessions());
        }
    }

    @Test
    public void testGivenConcurrentRequestsThenEngineReproducesOracleOnCpu() throws Exception {
        run(false);
    }

    @Test
    public void testGivenConcurrentRequestsThenEngineRunsOnGpu() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        run(true);
    }
}
