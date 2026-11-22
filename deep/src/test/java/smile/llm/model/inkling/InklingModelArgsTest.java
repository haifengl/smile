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
package smile.llm.model.inkling;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks {@link InklingModelArgs} and {@link InklingWeightNames} against the real Inkling-2-Mini checkpoint
 * (config and the safetensors header only). Skipped when {@code INKLING_CKPT} (default
 * {@code /raid/haili/inkling/raw}) has no checkpoint.
 *
 * @author Haifeng Li
 */
public class InklingModelArgsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Set<String> tensorNames(String file) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            byte[] len = new byte[8];
            f.readFully(len);
            long n = ByteBuffer.wrap(len).order(ByteOrder.LITTLE_ENDIAN).getLong();
            byte[] header = new byte[(int) n];
            f.readFully(header);
            JsonNode root = MAPPER.readTree(new String(header, StandardCharsets.UTF_8));
            Set<String> names = new LinkedHashSet<>();
            root.properties().forEach(e -> {
                if (!e.getKey().equals("__metadata__")) {
                    names.add(e.getKey());
                }
            });
            return names;
        }
    }

    @Test
    public void testGivenCheckpointWhenParsedThenHyperparametersMatchTheModelCard() throws IOException {
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(InklingTokenizerTest.checkpoint(), 0);
        assertEquals(2304, args.dim());
        assertEquals(24, args.numLayers());
        assertEquals(201024, args.vocabSize());
        assertEquals(200058, args.unpaddedVocabSize());
        assertEquals(24, args.numHeads());
        assertEquals(8, args.numKvHeads());
        assertEquals(128, args.headDim());
        assertEquals(1048576, args.maxSeqLen());
        assertEquals(200006, args.eosTokenId());
        assertEquals(48, args.numApplications());
        assertEquals(8, args.numGlobalApplications());   // layers 5, 23 once; 11, 17 three times each
        assertEquals(40, args.numLocalApplications());
        for (int global : new int[]{5, 11, 17, 23}) {
            assertFalse(args.isLocal(global), "layer " + global);
        }
        assertTrue(args.isLocal(0) && args.isLocal(22));
        assertTrue(args.isLooped(6) && args.isLooped(17));
        assertFalse(args.isLooped(5) || args.isLooped(18));
        assertEquals(InklingModelArgs.ExpertFormat.NVFP4, args.expertFormat(0));
        assertEquals(InklingModelArgs.ExpertFormat.NVFP4, args.expertFormat(19));
        assertEquals(InklingModelArgs.ExpertFormat.MXFP8, args.expertFormat(20));
        assertEquals(InklingModelArgs.ExpertFormat.MXFP8, args.expertFormat(23));
        assertEquals(4096, args.withMaxSeqLen(4096).maxSeqLen());
    }

    @Test
    public void testGivenApplicationsWhenMappedThenLoopedLayersRepeatPerPass() throws IOException {
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(InklingTokenizerTest.checkpoint(), 0);
        int[] expected = new int[48];
        int k = 0;
        for (int i = 0; i < 6; i++) expected[k++] = i;
        for (int pass = 0; pass < 3; pass++) for (int i = 6; i < 18; i++) expected[k++] = i;
        for (int i = 18; i < 24; i++) expected[k++] = i;
        for (int a = 0; a < 48; a++) {
            assertEquals(expected[a], args.layerOfApplication(a), "application " + a);
        }
        assertEquals(0, args.passOfApplication(5));
        assertEquals(0, args.passOfApplication(6));
        assertEquals(1, args.passOfApplication(6 + 12));
        assertEquals(2, args.passOfApplication(6 + 35));
        assertEquals(0, args.passOfApplication(47));
        assertThrows(IndexOutOfBoundsException.class, () -> args.layerOfApplication(48));
    }

    @Test
    public void testGivenCheckpointHeaderWhenNamesRemappedThenTheyCoverExactlyWhatTheTextTowerNeeds() throws IOException {
        String dir = InklingTokenizerTest.checkpoint();
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(dir, 0);
        Set<String> expected = InklingWeightNames.expectedNames(args);
        Set<String> provided = new LinkedHashSet<>();
        int vision = 0;
        for (String name : tensorNames(dir + "/model-00001-of-00001.safetensors")) {
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
        assertEquals(1244 - vision, expected.size());
    }
}
