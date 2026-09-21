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
package smile.onnx.genai;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Allowlist matcher tests (no natives).
 *
 * @author Haifeng Li
 */
public class GenAISupportedModelsTest {

    @Test
    public void matchRepoIds() {
        assertEquals(GenAISupportedModels.Family.PHI,
                GenAISupportedModels.matchRepoId("microsoft/Phi-3.5-mini-instruct").orElseThrow());
        assertEquals(GenAISupportedModels.Family.QWEN,
                GenAISupportedModels.matchRepoId("Qwen/Qwen2.5-0.5B-Instruct").orElseThrow());
        assertEquals(GenAISupportedModels.Family.GEMMA,
                GenAISupportedModels.matchRepoId("google/gemma-2-2b-it").orElseThrow());
        assertEquals(GenAISupportedModels.Family.LLAMA,
                GenAISupportedModels.matchRepoId("meta-llama/Llama-3.1-8B-Instruct").orElseThrow());
        assertTrue(GenAISupportedModels.matchRepoId("openai/whisper-tiny").isEmpty());
        assertTrue(GenAISupportedModels.matchRepoId("unknown/totally-novel-arch").isEmpty());
    }

    @Test
    public void whisperNotChatConvertible(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.json"), """
                {"model_type":"whisper","architectures":["WhisperForConditionalGeneration"]}
                """);
        assertTrue(GenAISupportedModels.match(dir).isEmpty());
        assertFalse(GenAISupportedModels.isChatConvertible(dir, "openai/whisper-tiny"));
    }

    @Test
    public void matchConfigJson(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.json"), """
                {"model_type":"phi3","architectures":["Phi3ForCausalLM"]}
                """);
        assertEquals(GenAISupportedModels.Family.PHI,
                GenAISupportedModels.match(dir).orElseThrow());
        assertTrue(GenAISupportedModels.isChatConvertible(dir, "local"));
    }
}
