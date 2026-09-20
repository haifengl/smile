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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import smile.llm.Message;
import smile.llm.Role;

/**
 * Native-backed GenAI tests.
 *
 * <p>Requires {@code onnxruntime-genai} natives. Model tests also need
 * {@code SMILE_ONNX_GENAI_MODEL} pointing at a GenAI model directory.
 *
 * <p>Model loads use {@link Model#open(Path)}, which cascades CUDA → RyzenAI →
 * OpenVINO NPU → QNN → CPU when preference is {@code auto}. Override with
 * {@code SMILE_ONNX_GENAI_PROVIDER}.
 *
 * <p>The onnxruntime-genai {@code test/models/qwen3-5} fixture is a tiny Identity
 * ONNX graph: load / tokenize / create {@link Generator} are supported, but a
 * full {@code generateNextToken} loop is not (KV cache growth). Those cases are
 * gated by {@link #fullGenerationSupported}.
 *
 * @author Haifeng Li
 */
@Tag("native")
public class GenAINativeTest {
    private static Path modelDir;
    private static boolean modelReady;
    private static boolean fullGenerationSupported;
    private static String activeProvider = "default";

    @BeforeAll
    public static void beforeAll() {
        Assumptions.assumeTrue(GenAI.available(),
                "onnxruntime-genai native library not on the OS library path "
                        + "(set ONNXRUNTIME_NATIVE_PATH / ONNXRUNTIME_GENAI_NATIVE_PATH)");
        String prop = firstNonBlank(
                System.getenv("SMILE_ONNX_GENAI_MODEL"),
                System.getProperty("smile.onnx.genai.model"));
        if (prop != null) {
            Path dir = Path.of(prop);
            modelReady = Files.isDirectory(dir)
                    && Files.isRegularFile(dir.resolve("genai_config.json"));
            if (modelReady) {
                modelDir = dir;
                fullGenerationSupported = !isDummyFixture(dir);
                // Probe provider once so later tests reuse the cached CUDA result.
                try (Model probe = Model.open(modelDir)) {
                    activeProvider = probe.provider();
                }
                System.out.println("GenAINativeTest provider=" + activeProvider
                        + " preference=" + GenAI.providerPreference()
                        + " cudaNatives=" + GenAI.cudaNativesPresent()
                        + " cudaUsable=" + GenAI.cudaProviderUsable());
            }
        }
    }

    @AfterAll
    public static void afterAll() {
        // Prefer an orderly GenAI teardown before the JVM unloads CUDA EP DLLs
        // (Windows can otherwise ACCESS_VIOLATION in onnxruntime_providers_cuda).
        try {
            GenAI.shutdown();
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    /**
     * Dummy GenAI unit-test models ship Identity ONNX files named {@code dummy_*.onnx}.
     */
    private static boolean isDummyFixture(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("dummy_") && name.endsWith(".onnx");
            });
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    public void sequencesRoundTrip() {
        try (Sequences sequences = Sequences.create()) {
            sequences.append(new int[]{1, 2, 3});
            sequences.append(new int[]{4, 5});
            assertEquals(2, sequences.count());
            assertArrayEquals(new int[]{1, 2, 3}, sequences.get(0));
            assertArrayEquals(new int[]{4, 5}, sequences.get(1));
        }
    }

    @Test
    public void modelLoadsAndTokenizes() {
        Assumptions.assumeTrue(modelReady,
                "Set SMILE_ONNX_GENAI_MODEL to a GenAI model dir");
        try (Model model = Model.open(modelDir);
             Tokenizer tokenizer = model.createTokenizer()) {
            assertEquals(activeProvider, model.provider());
            int[] tokens = tokenizer.encodeToArray("Hello");
            assertTrue(tokens.length > 0);
            String decoded = tokenizer.decode(tokens);
            assertNotNull(decoded);
            assertFalse(decoded.isBlank());
        }
    }

    @Test
    public void generatorCreatesWithSmallMaxLength() {
        Assumptions.assumeTrue(modelReady,
                "Set SMILE_ONNX_GENAI_MODEL to a GenAI model dir");
        try (Model model = Model.open(modelDir);
             GeneratorParams params = model.createGeneratorParams()) {
            params.setSearchOption("max_length", 8.0);
            params.setSearchOption("do_sample", false);
            try (Generator generator = Generator.of(model, params)) {
                assertNotNull(generator);
            }
        }
    }

    @Test
    public void encodeGenerateDecodeSmoke() {
        Assumptions.assumeTrue(modelReady,
                "Set SMILE_ONNX_GENAI_MODEL to a GenAI model dir");
        Assumptions.assumeTrue(fullGenerationSupported,
                "Full generateNextToken loop requires a real GenAI model "
                        + "(dummy Identity fixtures cannot grow KV cache)");
        try (Model model = Model.open(modelDir);
             Tokenizer tokenizer = model.createTokenizer();
             GeneratorParams params = model.createGeneratorParams()) {
            params.setSearchOption("max_length", 32.0);
            params.setSearchOption("do_sample", false);
            int[] prompt = tokenizer.encodeToArray("Hello");
            assertTrue(prompt.length > 0);
            try (Generator generator = Generator.of(model, params);
                 TokenizerStream stream = tokenizer.createStream()) {
                generator.appendTokens(prompt);
                int steps = 0;
                while (!generator.isDone() && steps < 8) {
                    generator.generateNextToken();
                    stream.decode(generator.getLastToken(0));
                    steps++;
                }
                assertTrue(steps > 0);
                int[] full = generator.getSequence(0);
                assertTrue(full.length >= prompt.length);
                assertNotNull(tokenizer.decode(full));
            }
        }
    }

    @Test
    public void simpleGenAIGenerate() {
        Assumptions.assumeTrue(modelReady,
                "Set SMILE_ONNX_GENAI_MODEL to a GenAI model dir");
        Assumptions.assumeTrue(fullGenerationSupported,
                "Full generate requires a real GenAI model "
                        + "(dummy Identity fixtures cannot grow KV cache)");
        try (SimpleGenAI genai = SimpleGenAI.open(modelDir);
             GeneratorParams params = genai.createGeneratorParams()) {
            assertEquals(activeProvider, genai.model().provider());
            params.setSearchOption("max_length", 32.0);
            params.setSearchOption("do_sample", false);
            StringBuilder streamed = new StringBuilder();
            String result = genai.generate(params, "Hi", streamed::append);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    public void genAiChatModelEncode() {
        Assumptions.assumeTrue(modelReady,
                "Set SMILE_ONNX_GENAI_MODEL to a GenAI model dir");
        try (GenAiChatModel chat = GenAiChatModel.open(modelDir)) {
            assertEquals("onnx/genai", chat.family());
            assertTrue(chat.maxSeqLen() > 0);
            assertFalse(chat.name().isBlank());
            int[] tokens = chat.encodeChat(new Message(Role.user, "Say hi"));
            assertTrue(tokens.length > 0);

            if (fullGenerationSupported) {
                var completion = chat.generate(tokens, 8, 0.0, 1.0, false, 0, null, null);
                assertNotNull(completion.content());
                assertEquals(chat.name(), completion.model());
                assertNotNull(completion.reason());
            }
        }
    }

    @Test
    public void reportsSelectedProvider() {
        Assumptions.assumeTrue(modelReady,
                "Set SMILE_ONNX_GENAI_MODEL to a GenAI model dir");
        assertTrue(
                activeProvider.equals("cuda")
                        || activeProvider.equals("ryzenai")
                        || activeProvider.equals("openvino")
                        || activeProvider.equals("qnn")
                        || activeProvider.equals("default"),
                "unexpected provider: " + activeProvider
                        + " (preference=" + GenAI.providerPreference() + ")");
    }
}