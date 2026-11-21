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
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.TextPart;
import smile.llm.engine.GenerationRequest;
import smile.llm.engine.InferenceEngine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A chat with an image through {@link Glm} and the engine on the full model: the expanded prompt must equal the one the
 * transformers processor builds (fixture {@code chat_shapes_ids.json}), and the model answers about the image. Opt-in:
 * {@code GLM_FULL}, {@code GLM_FIXTURES}; {@code GLM_FULL_OUT} receives the prompt and completion ids.
 */
public class GlmVisionChatTest {
    @Test
    public void testGivenImageThenPromptEqualsReferenceAndModelAnswers() throws Exception {
        String dir = System.getenv("GLM_FULL");
        String fx = System.getenv("GLM_FIXTURES");
        Assumptions.assumeTrue(dir != null && fx != null && Files.exists(Path.of(fx, "chat_shapes_ids.json")),
                "set GLM_FULL=<checkpoint dir> and GLM_FIXTURES=<fixture dir>");
        int devices = Integer.parseInt(System.getenv().getOrDefault("GLM_FULL_DEVICES", "8"));
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        String question = System.getenv().getOrDefault("GLM_VISION_QUESTION", "Describe the image.");
        String image = System.getenv().getOrDefault("GLM_VISION_IMAGE", "img_shapes.png");
        try (Glm model = Glm.build(dir, 0, 32768, dev, 4)) {
            assertTrue(model.isMultimodal());
            Message[] dialog = {Message.user(new ImageUrlPart(Path.of(fx, image).toString()), new TextPart(question))};
            GlmMultimodal mm = model.processMultimodal(dialog, null);
            Path refFile = Path.of(fx, "chat_shapes_ids.json");
            if (image.equals("img_shapes.png") && question.equals("Describe the image.") && Files.exists(refFile)) {
                JsonNode ref = new ObjectMapper().readTree(refFile.toFile()).get("ids");
                int[] want = new int[ref.size()];
                for (int i = 0; i < want.length; i++) {
                    want[i] = ref.get(i).asInt();
                }
                assertArrayEquals(want, mm.inputIds(), "the expanded prompt must equal the transformers processor's");
                System.out.println("prompt ids equal the reference: " + want.length + " tokens");
            }
            var engine = new InferenceEngine(model, 4, 4, 2048, 0);
            long t0 = System.nanoTime();
            int[] out = engine.submit(GenerationRequest.ofMultimodal(mm, 400, 0.0, 1.0, false, 0, null)).future()
                    .get(30, TimeUnit.MINUTES).completionTokens();
            System.out.printf("answer (%d tokens, %.1f s incl. the vision encoder):%n%s%n", out.length, (System.nanoTime() - t0) / 1e9,
                    model.tryDecodeSafe(out));
            String file = System.getenv("GLM_FULL_OUT");
            if (file != null) {
                StringBuilder sb = new StringBuilder("{\"prompt\":").append(java.util.Arrays.toString(mm.inputIds())).append(",\"ids\":")
                        .append(java.util.Arrays.toString(out)).append("}");
                Files.writeString(Path.of(file), sb.toString());
            }
            assertTrue(out.length > 0);
        }
    }
}
