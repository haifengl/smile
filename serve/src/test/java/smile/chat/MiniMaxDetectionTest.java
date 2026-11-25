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
package smile.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Model detection for MiniMax-M3 (no Quarkus bootstrap required).
 */
public class MiniMaxDetectionTest {
    @TempDir
    Path dir;

    private Path checkpoint(String name, String configJson, boolean tokenizerJson) throws IOException {
        Path d = Files.createDirectory(dir.resolve(name));
        Files.writeString(d.resolve("config.json"), configJson);
        if (tokenizerJson) {
            Files.writeString(d.resolve("tokenizer.json"), "{}");
        }
        return d;
    }

    @Test
    public void testGivenModelTypeWhenDetectedThenMiniMax() throws IOException {
        Path d = checkpoint("a", "{\"model_type\":\"minimax_m3_vl\"}", true);
        assertTrue(ChatService.isMiniMaxCheckpoint(d));
        assertTrue(ChatService.isBuiltinTorchCheckpoint(d));
        assertFalse(ChatService.isQwenCheckpoint(d));
    }

    @Test
    public void testGivenArchitectureOnlyWhenDetectedThenMiniMax() throws IOException {
        Path d = checkpoint("b", "{\"architectures\":[\"MiniMaxM3SparseForCausalLM\"]}", false);
        assertTrue(ChatService.isMiniMaxCheckpoint(d));
        assertTrue(ChatService.isBuiltinTorchCheckpoint(d));
    }

    @Test
    public void testGivenOtherModelsWhenDetectedThenNotMiniMax() throws IOException {
        assertFalse(ChatService.isMiniMaxCheckpoint(checkpoint("q", "{\"model_type\":\"qwen3_5\"}", true)));
        assertFalse(ChatService.isMiniMaxCheckpoint(checkpoint("l", "{\"model_type\":\"llama\"}", true)));
        assertFalse(ChatService.isMiniMaxCheckpoint(checkpoint("m2", "{\"model_type\":\"minimax\"}", true)));
        assertFalse(ChatService.isMiniMaxCheckpoint(Files.createDirectory(dir.resolve("empty"))));
    }

    @Test
    public void testGivenRepoIdsWhenMatchedThenMiniMaxM3Variants() {
        assertTrue(ChatService.isMiniMaxRepoId("MiniMaxAI/MiniMax-M3"));
        assertTrue(ChatService.isMiniMaxRepoId("MiniMaxAI/MiniMax-M3-MXFP8"));
        assertTrue(ChatService.isBuiltinTorchFamily("MiniMaxAI/MiniMax-M3-MXFP8"));
        assertFalse(ChatService.isMiniMaxRepoId("MiniMaxAI/MiniMax-M2"));
        assertFalse(ChatService.isMiniMaxRepoId(null));
    }

    @Test
    public void testGivenInklingCheckpointWhenDetectedThenMatchesAndRequestsQuantAndMtpFiles() throws IOException {
        Path d = checkpoint("inkling",
                "{\"model_type\":\"inkling2_loop\",\"architectures\":[\"Inkling2LoopForConditionalGeneration\"]}",
                true);
        assertTrue(ChatService.isInklingCheckpoint(d));
        assertTrue(ChatService.isBuiltinTorchCheckpoint(d));
        assertTrue(ChatService.isInklingRepoId("thinkingmachines/Inkling-2-Mini"));
        assertTrue(ChatService.isBuiltinTorchFamily("thinkingmachines/Inkling-2-Mini"));
        assertEquals(java.util.List.of("hf_quant_config.json", "mtp.safetensors"),
                ChatService.huggingFaceOptionalFiles(d));
    }

    @Test
    public void testGivenDeepSeekAndGlmCheckpointsWhenDetectedThenRequestExpectedOptionalFiles() throws IOException {
        Path ds = checkpoint("ds", "{\"model_type\":\"deepseek_v4\"}", true);
        assertTrue(ChatService.isDeepSeekCheckpoint(ds));
        assertEquals(java.util.List.of("engram_small.safetensors"),
                ChatService.huggingFaceOptionalFiles(ds));

        Path glm = checkpoint("glm", "{\"model_type\":\"glm5_next\"}", true);
        assertTrue(ChatService.isGlmCheckpoint(glm));
        assertEquals(java.util.List.of("processor_config.json"),
                ChatService.huggingFaceOptionalFiles(glm));
    }

    @Test
    public void testGivenSnapshotDirectoryWhenResolvingRevisionThenReturnsCommitSha() {
        Path snapshot = Path.of("/model/huggingface/hub/models--thinkingmachines--Inkling-2-Mini"
                + "/snapshots/7a67ae2314b44ef05c87fd1d4d311c58aaeb18f6");
        assertEquals("7a67ae2314b44ef05c87fd1d4d311c58aaeb18f6", ChatService.snapshotRevision(snapshot));
        assertEquals("main", ChatService.snapshotRevision(Path.of("/models/Inkling-2-Mini")));
        assertEquals("main", ChatService.snapshotRevision(null));
    }

    @Test
    public void testGivenCachedSingleShardSnapshotWhenResolvingShardsThenReturnsLocalShardWithoutNetwork() throws IOException {
        Path snapshot = Files.createDirectory(dir.resolve("7a67ae2314b44ef05c87fd1d4d311c58aaeb18f6"));
        Files.writeString(snapshot.resolve("model-00001-of-00001.safetensors"), "stub");
        assertEquals(java.util.Set.of("model-00001-of-00001.safetensors"),
                ChatService.resolveSafeTensorShards(snapshot, "nonexistent-org/nonexistent-model",
                        "7a67ae2314b44ef05c87fd1d4d311c58aaeb18f6"));
    }

    @Test
    public void testGivenMiniMaxDsparkIndexWhenResolvedThenPrefixesDsparkSubfolder() throws IOException {
        Path dsparkDir = Files.createDirectories(dir.resolve("m3/dspark"));
        Path index = dsparkDir.resolve("model.safetensors.index.json");
        Files.writeString(index, """
                {
                  "weight_map": {
                    "language_model.model.dspark.fc.weight": "model-dspark-00001.safetensors",
                    "language_model.model.dspark.final_norm.weight": "model-dspark-00001.safetensors",
                    "language_model.model.dspark.extra.weight": "dspark/model-dspark-00002.safetensors"
                  }
                }
                """);
        assertEquals(java.util.Set.of("dspark/model-dspark-00001.safetensors", "dspark/model-dspark-00002.safetensors"),
                ChatService.resolveMiniMaxDsparkShards(index));
    }
}
