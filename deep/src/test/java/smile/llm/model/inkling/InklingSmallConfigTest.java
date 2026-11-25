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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inkling-2-Small's own {@code config.json} (a test resource) through {@link InklingModelArgs} and
 * {@link InklingWeightNames}: a loop-free model of 48 layers (no GPU, no checkpoint).
 *
 * @author Haifeng Li
 */
public class InklingSmallConfigTest {
    private static InklingModelArgs args() throws IOException {
        JsonNode root;
        try (InputStream in = InklingSmallConfigTest.class.getResourceAsStream("/inkling/small-config.json")) {
            root = new ObjectMapper().readTree(in);
        }
        Map<Integer, InklingModelArgs.ExpertFormat> formats = new HashMap<>();
        for (int i = 0; i < 48; i++) {
            formats.put(i, i < 40 ? InklingModelArgs.ExpertFormat.NVFP4 : InklingModelArgs.ExpertFormat.MXFP8);
        }
        return InklingModelArgs.fromConfig(root, 200006, 0, formats);
    }

    @Test
    public void testGivenSmallConfigWhenParsedThenItIsALoopFreeModelOfFortyEightLayers() throws IOException {
        InklingModelArgs a = args();
        assertEquals(4608, a.dim());
        assertEquals(48, a.numLayers());
        assertEquals(48, a.numHeads());
        assertEquals(8, a.numKvHeads());
        assertEquals(384, a.numExperts());
        assertEquals(8, a.numExpertsPerTok());
        assertEquals(2304, a.intermediateSize());
        assertEquals(48, a.numApplications());
        assertEquals(8, a.numGlobalApplications());
        assertEquals(40, a.numLocalApplications());
        assertEquals(1048576, a.maxSeqLen());
        for (int i = 0; i < 48; i++) {
            assertFalse(a.isLooped(i));
            assertEquals(i, a.layerOfApplication(i));
            assertEquals(i % 6 == 5, !a.isLocal(i), "layer " + i);
        }
    }

    @Test
    public void testGivenSmallArgsWhenNamesListedThenTheyMatchTheCheckpointsTextTensors() throws IOException {
        InklingModelArgs a = args();
        Set<String> names = InklingWeightNames.expectedNames(a);
        // 1770 tensors in the checkpoint, 422 of them the vision tower
        assertEquals(1770 - 422, names.size());
        assertFalse(names.contains("middle_cycle_cross_pass_queries"));
        assertTrue(names.contains("layers.5.attn.k_sconv.static_w"));        // a global layer: input-dependent convolutions
        assertTrue(names.contains("layers.5.q_input_norm.weight"));
        assertTrue(names.contains("layers.0.attn_norm.weight"));             // a local layer
        assertTrue(names.contains("layers.40.mlp.experts.w13_weight_scale")); // MXFP8
        assertFalse(names.contains("layers.40.mlp.experts.w13_weight_amax"));
        assertTrue(names.contains("layers.39.mlp.experts.w13_weight_amax"));  // NVFP4
        assertTrue(names.contains("attn_res.attn_queries.46"));
        assertFalse(names.contains("attn_res.attn_queries.47"));
    }

    @Test
    public void testGivenSmallArgsWhenSlicedThenTheLayerCountShrinks() throws IOException {
        InklingModelArgs s = args().withLayers(12, 1);
        assertEquals(12, s.numLayers());
        assertEquals(12, s.numApplications());
        // 4 top-level + 11 attention and 12 MLP queries + 1 output query; 10 local layers of 18 tensors and 2 global of 22;
        // 8 NVFP4 expert tensors per layer
        assertEquals(4 + 11 + 12 + 1 + 10 * 18 + 2 * 22 + 12 * 8, InklingWeightNames.expectedNames(s).size());
    }

    @Test
    public void testGivenCheckpointWithoutQuantConfigWhenIndexPresentThenExpertFormatsAreInferred(@TempDir Path dir)
            throws IOException {
        try (InputStream in = InklingSmallConfigTest.class.getResourceAsStream("/inkling/small-config.json")) {
            Files.copy(in, dir.resolve("config.json"));
        }
        StringBuilder sb = new StringBuilder("{\"weight_map\":{");
        for (int i = 0; i < 48; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("\"model.llm.layers.").append(i).append(".mlp.experts.w13_weight\":\"model-00001-of-00024.safetensors\",");
            sb.append("\"model.llm.layers.").append(i).append(".mlp.experts.w13_weight_scale\":\"model-00001-of-00024.safetensors\"");
            if (i < 40) {
                sb.append(",\"model.llm.layers.").append(i).append(".mlp.experts.w13_weight_amax\":\"model-00001-of-00024.safetensors\"");
            }
        }
        sb.append("}}");
        Files.writeString(dir.resolve("model.safetensors.index.json"), sb.toString());

        InklingModelArgs a = InklingModelArgs.fromCheckpoint(dir.toString(), 0);
        for (int i = 0; i < 48; i++) {
            InklingModelArgs.ExpertFormat expected = i < 40
                    ? InklingModelArgs.ExpertFormat.NVFP4
                    : InklingModelArgs.ExpertFormat.MXFP8;
            assertEquals(expected, a.expertFormat(i), "layer " + i);
        }
    }

    @Test
    public void testGivenCheckpointWithoutQuantConfigWhenSingleSafetensorsPresentThenExpertFormatsAreInferred(@TempDir Path dir)
            throws IOException {
        try (InputStream in = InklingSmallConfigTest.class.getResourceAsStream("/inkling/small-config.json")) {
            Files.copy(in, dir.resolve("config.json"));
        }
        StringBuilder header = new StringBuilder("{");
        for (int i = 0; i < 24; i++) {
            if (i > 0) {
                header.append(',');
            }
            header.append("\"model.llm.layers.").append(i)
                    .append(".mlp.experts.w13_weight_scale\":{\"dtype\":\"U8\",\"shape\":[1],\"data_offsets\":[0,1]}");
            if (i < 20) {
                header.append(",\"model.llm.layers.").append(i)
                        .append(".mlp.experts.w13_weight_amax\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,0]}");
            }
        }
        header.append('}');
        byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + headerBytes.length + 1).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(headerBytes.length);
        buf.put(headerBytes);
        buf.put((byte) 0);
        Files.write(dir.resolve("model-00001-of-00001.safetensors"), buf.array());

        InklingModelArgs a = InklingModelArgs.fromCheckpoint(dir.toString(), 0);
        for (int i = 0; i < 20; i++) {
            assertEquals(InklingModelArgs.ExpertFormat.NVFP4, a.expertFormat(i), "layer " + i);
        }
        for (int i = 20; i < 24; i++) {
            assertEquals(InklingModelArgs.ExpertFormat.MXFP8, a.expertFormat(i), "layer " + i);
        }
        assertEquals(InklingModelArgs.ExpertFormat.BF16, a.expertFormat(24));
    }
}
