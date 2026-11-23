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

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks {@link InklingModelArgs} and {@link InklingWeightNames} against the real Inkling-2-Small checkpoint
 * (config, {@code hf_quant_config.json} and {@code model.safetensors.index.json} only). Skipped when
 * {@code INKLING_SMALL_CKPT} (default {@code ~/models/Inkling-2-Small}) has no checkpoint.
 *
 * @author Haifeng Li
 */
public class InklingSmallArgsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String checkpoint() {
        String dir = System.getenv("INKLING_SMALL_CKPT");
        if (dir == null || dir.isEmpty()) {
            dir = System.getProperty("user.home") + "/models/Inkling-2-Small";
        }
        Assumptions.assumeTrue(new File(dir, "config.json").exists()
                && new File(dir, "hf_quant_config.json").exists()
                && new File(dir, "model.safetensors.index.json").exists(), "no Inkling-2-Small checkpoint in " + dir);
        return dir;
    }

    @Test
    public void testGivenSmallConfigWhenParsedThenItIsALoopFreeModel() throws IOException {
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(checkpoint(), 0);
        assertEquals(4608, args.dim());
        assertEquals(48, args.numLayers());
        assertEquals(201024, args.vocabSize());
        assertEquals(200058, args.unpaddedVocabSize());
        assertEquals(48, args.numHeads());
        assertEquals(8, args.numKvHeads());
        assertEquals(128, args.headDim());
        assertEquals(384, args.numExperts());
        assertEquals(8, args.numExpertsPerTok());
        assertEquals(2304, args.intermediateSize());
        assertEquals(1048576, args.maxSeqLen());
        assertEquals(48, args.numApplications());
        assertEquals(8, args.numGlobalApplications());
        assertEquals(40, args.numLocalApplications());
        for (int global : new int[]{5, 11, 17, 23, 29, 35, 41, 47}) {
            assertFalse(args.isLocal(global), "layer " + global);
        }
        for (int a = 0; a < 48; a++) {
            assertEquals(a, args.layerOfApplication(a));
            assertEquals(0, args.passOfApplication(a));
            assertFalse(args.isLooped(a));
        }
        for (int i = 0; i < 40; i++) {
            assertEquals(InklingModelArgs.ExpertFormat.NVFP4, args.expertFormat(i), "layer " + i);
        }
        for (int i = 40; i < 48; i++) {
            assertEquals(InklingModelArgs.ExpertFormat.MXFP8, args.expertFormat(i), "layer " + i);
        }
    }

    @Test
    public void testGivenCheckpointIndexWhenNamesRemappedThenTheyCoverExactlyWhatTheTextTowerNeeds() throws IOException {
        String dir = checkpoint();
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(dir, 0);
        Set<String> expected = InklingWeightNames.expectedNames(args);
        Set<String> provided = new LinkedHashSet<>();
        int vision = 0;
        JsonNode map = MAPPER.readTree(new File(dir, "model.safetensors.index.json")).get("weight_map");
        for (var e : map.properties()) {
            String name = e.getKey();
            String mapped = InklingWeightNames.remap(name);
            if (mapped == null) {
                assertTrue(InklingWeightNames.isVision(name), "unmapped non-vision tensor " + name);
                vision++;
            } else {
                assertTrue(provided.add(mapped), "duplicate " + mapped);
            }
        }
        assertTrue(vision > 0);
        for (String name : expected) {
            assertTrue(provided.contains(name), "missing from the checkpoint: " + name);
        }
        for (String name : provided) {
            assertTrue(expected.contains(name), "checkpoint tensor the model does not expect: " + name);
        }
        assertEquals(1770 - vision, expected.size());
    }
}
