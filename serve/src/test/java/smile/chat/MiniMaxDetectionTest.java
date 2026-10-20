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
}
