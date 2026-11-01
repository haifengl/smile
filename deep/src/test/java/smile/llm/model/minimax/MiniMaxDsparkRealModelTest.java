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

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the DSpark draft of MiniMax-M3.1-Flash on a single GPU against the states vLLM produced.
 *
 * <p>Opt-in. Environment: {@code MINIMAX_CHECKPOINT_DIR} (the Flash checkpoint with its {@code dspark/} folder, only
 * its embedding and output head are read besides the draft) and {@code MINIMAX_DSPARK_CASES} (a directory with
 * {@code cases.json} and {@code case<k>_aux.f32} / {@code case<k>_hidden.f32}: the target taps vLLM fed the draft and
 * the draft's final hidden states, dumped from a vLLM run with the hooks in {@code DSPARK_PLAN.md}). The check is
 * statistical, not bit-exact: bf16 draft numerics differ slightly, so the greedy block may diverge after a near tie.
 *
 * @author Haifeng Li
 */
public class MiniMaxDsparkRealModelTest {
    private static float[] readFloats(Path path) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
        float[] f = new float[bb.remaining() / 4];
        bb.asFloatBuffer().get(f);
        return f;
    }

    @Test
    public void testGivenVllmTapsWhenDraftedThenBlocksAndHiddenMatch() throws Exception {
        String dir = System.getenv("MINIMAX_CHECKPOINT_DIR");
        String casesDir = System.getenv("MINIMAX_DSPARK_CASES");
        assumeTrue(dir != null && casesDir != null && new File(dir, "dspark/config.json").exists(),
                "MINIMAX_CHECKPOINT_DIR / MINIMAX_DSPARK_CASES not set");
        assumeTrue(Device.preferredDevice().isCUDA(), "needs CUDA");
        Device device = Device.CUDA((byte) 0);
        ObjectMapper json = new ObjectMapper();
        String shard = json.readTree(new File(dir, "model.safetensors.index.json"))
                .get("weight_map").get("language_model.model.embed_tokens.weight").asString();
        Tensor embedding;
        Tensor head;
        try (var noGrad = Tensor.noGradGuard()) {
            SafeTensors st = SafeTensors.read(Path.of(dir, shard).toString(), Device.CPU(),
                    List.of("language_model.model.embed_tokens.weight", "language_model.lm_head.weight"));
            embedding = st.tensors().get("language_model.model.embed_tokens.weight").to(device);
            head = st.tensors().get("language_model.lm_head.weight").to(device);
        }
        MiniMaxDspark.TargetHeads heads = new MiniMaxDspark.TargetHeads() {
            @Override
            public Tensor embed(Tensor ids) {
                return embedding.get(ids);
            }

            @Override
            public Tensor logits(Tensor hidden) {
                try (Tensor t = head.transpose(0, 1)) {
                    return hidden.matmul(t).to(ScalarType.Float);
                }
            }
        };
        JsonNode meta = json.readTree(new File(casesDir, "cases.json"));
        int[][] sequences = {{1, 2, 3}, {4, 5}};  // rounds of one request each, in order
        int matched = 0;
        int total = 0;
        try (MiniMaxDspark draft = MiniMaxDspark.load(dir + "/dspark", device, MiniMaxWeightMode.MARLIN,
                ScalarType.BFloat16, heads, 8192)) {
            int request = 0;
            for (int[] sequence : sequences) {
                draft.bind(++request, 4096);
                for (int k : sequence) {
                    JsonNode m = meta.get(k - 1);
                    int n = m.get("n").asInt();
                    int valid = m.get("valid").asInt();
                    float[] aux = readFloats(Path.of(casesDir, "case" + k + "_aux.f32"));
                    float[] ref = readFloats(Path.of(casesDir, "case" + k + "_hidden.f32"));
                    try (Tensor all = Tensor.of(aux, n, draft.args().contextWidth()).to(device).to(ScalarType.BFloat16);
                         var rows = Index.slice(0, valid);
                         Tensor context = all.get(rows, Index.Colon)) {
                        draft.absorb(request, context, m.get("start").asInt());
                    }
                    MiniMaxDspark.Draft d = draft.draft(request, m.get("anchor").asInt(), m.get("anchor_pos").asInt(), true);
                    int block = draft.args().blockSize();
                    assertEquals(block, d.tokens().length);
                    assertEquals(block, d.confidence().length);
                    float[] got = d.hidden().to(Device.CPU()).copy().floatArray();
                    d.hidden().close();
                    int dim = draft.args().dim();
                    for (int r = 0; r < block; r++) {
                        double err = 0;
                        double norm = 0;
                        for (int c = 0; c < dim; c++) {
                            double x = got[r * dim + c] - ref[r * dim + c];
                            err += x * x;
                            norm += (double) ref[r * dim + c] * ref[r * dim + c];
                        }
                        assertTrue(Math.sqrt(err / norm) < 0.08, "case " + k + " row " + r + " hidden error");
                        if (d.tokens()[r] == m.get("tokens").get(r).asInt()) {
                            matched++;
                        }
                        total++;
                        assertTrue(d.confidence()[r] >= 0f && d.confidence()[r] <= 1f);
                    }
                }
                draft.release(request);
            }
        } finally {
            embedding.close();
            head.close();
        }
        // 32 of 35 tokens agree on the recorded run; the misses sit behind a near tie of a degenerate block.
        assertTrue(matched >= total - 5, matched + " of " + total + " drafted tokens match vLLM");
    }
}
