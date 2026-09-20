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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import smile.util.OS;

/**
 * Registry of {@link GenAIProviderCandidate}s and per-id usable caches for
 * {@link Model#open}.
 *
 * <p>Classical Vitis AI EP for general ONNX lives in
 * {@link smile.onnx.SessionOptions#appendVitisAiExecutionProvider()}, not here.
 *
 * @author Haifeng Li
 */
final class GenAIProviders {
    private static final Map<String, Boolean> USABLE = new ConcurrentHashMap<>();

    static final GenAIProviderCandidate CUDA = new CudaCandidate();
    static final GenAIProviderCandidate RYZEN_AI = new RyzenAiCandidate();
    static final GenAIProviderCandidate OPENVINO_NPU = new OpenVinoNpuCandidate();
    static final GenAIProviderCandidate QNN = new QnnCandidate();
    static final GenAIProviderCandidate DIRECT_ML = new DirectMlCandidate();

    private GenAIProviders() {}

    /**
     * Ordered candidates for a normalized {@link GenAI#providerPreference()}.
     *
     * @param preference normalized preference.
     * @return candidates to try (may be empty for {@code cpu}).
     */
    static List<GenAIProviderCandidate> candidatesFor(String preference) {
        return switch (preference) {
            case "cpu" -> List.of();
            case "cuda" -> List.of(CUDA);
            case "ryzenai" -> List.of(RYZEN_AI);
            case "openvino" -> List.of(OPENVINO_NPU);
            case "qnn" -> List.of(QNN);
            case "dml" -> List.of(DIRECT_ML);
            case "npu" -> List.of(RYZEN_AI, OPENVINO_NPU, QNN);
            // auto: accelerators then Windows DirectML, then CPU fallback in Model.open
            default -> List.of(CUDA, RYZEN_AI, OPENVINO_NPU, QNN, DIRECT_ML);
        };
    }

    static Boolean usable(String id) {
        return USABLE.get(id);
    }

    static void noteUsable(String id, boolean usable) {
        USABLE.put(id, usable);
    }

    /** Package test hook. */
    static void clearUsableCache() {
        USABLE.clear();
    }

    // -------------------------------------------------------------------------
    // Candidates
    // -------------------------------------------------------------------------

    private static final class CudaCandidate implements GenAIProviderCandidate {
        @Override
        public String id() {
            return "cuda";
        }

        @Override
        public boolean nativesPresent() {
            return libraryPresent("onnxruntime_providers_cuda")
                    || libraryPresent("onnxruntime-genai-cuda");
        }

        @Override
        public void configure(Config config, String modelDir) {
            config.clearProviders().appendProvider("cuda");
        }
    }

    /**
     * AMD Ryzen AI OGA path (hybrid NPU+iGPU or NPU-only models). Provider key
     * matches AMD {@code genai_config.json}: {@code RyzenAI}.
     */
    private static final class RyzenAiCandidate implements GenAIProviderCandidate {
        @Override
        public String id() {
            return "ryzenai";
        }

        @Override
        public boolean nativesPresent() {
            String ryzenRoot = System.getenv("RYZEN_AI_INSTALLATION_PATH");
            if (ryzenRoot != null && !ryzenRoot.isBlank() && Files.isDirectory(Path.of(ryzenRoot))) {
                return true;
            }
            String genaiDir = GenAI.genaiNativeDir();
            if (genaiDir != null && genaiDir.toLowerCase(Locale.ROOT).contains("ryzenai")) {
                return true;
            }
            // DirectML EP often ships with the AMD OGA DirectML-RyzenAI wheel.
            if (libraryPresent("onnxruntime_providers_dml")
                    && (genaiDir != null || ryzenRoot != null)) {
                return true;
            }
            return false;
        }

        @Override
        public void configure(Config config, String modelDir) {
            if (genaiConfigMentionsRyzenAi(modelDir)) {
                // Honor hybrid_opt_* / NPU options already in genai_config.json.
                return;
            }
            config.clearProviders().appendProvider("RyzenAI");
        }
    }

    private static final class OpenVinoNpuCandidate implements GenAIProviderCandidate {
        @Override
        public String id() {
            return "openvino";
        }

        @Override
        public boolean nativesPresent() {
            return libraryPresent("onnxruntime_providers_openvino");
        }

        @Override
        public void configure(Config config, String modelDir) {
            config.clearProviders()
                    .appendProvider("openvino")
                    .setProviderOption("openvino", "device_type", "NPU");
        }
    }

    private static final class QnnCandidate implements GenAIProviderCandidate {
        @Override
        public String id() {
            return "qnn";
        }

        @Override
        public boolean nativesPresent() {
            return libraryPresent("onnxruntime_providers_qnn");
        }

        @Override
        public void configure(Config config, String modelDir) {
            config.clearProviders().appendProvider("qnn");
            String htp = GenAI.findLibraryFile("QnnHtp");
            if (htp != null) {
                config.setProviderOption("qnn", "backend_path", htp);
            }
        }
    }

    /**
     * Windows DirectML GPU (iGPU/dGPU) — last accelerator before CPU on
     * {@code auto}. Skipped on non-Windows even if listed in the cascade.
     */
    private static final class DirectMlCandidate implements GenAIProviderCandidate {
        @Override
        public String id() {
            return "dml";
        }

        @Override
        public boolean nativesPresent() {
            if (!OS.isWindows()) {
                return false;
            }
            return libraryPresent("onnxruntime_providers_dml");
        }

        @Override
        public void configure(Config config, String modelDir) {
            config.clearProviders().appendProvider("dml");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean libraryPresent(String bareName) {
        return GenAI.libraryFilePresent(bareName);
    }

    private static boolean genaiConfigMentionsRyzenAi(String modelDir) {
        Path cfg = Path.of(modelDir, "genai_config.json");
        if (!Files.isRegularFile(cfg)) {
            return false;
        }
        try {
            String json = Files.readString(cfg);
            return json.contains("\"RyzenAI\"") || json.contains("\"ryzenai\"");
        } catch (IOException e) {
            return false;
        }
    }

    /** Visible for tests: preference → candidate ids. */
    static List<String> candidateIdsFor(String preference) {
        List<String> ids = new ArrayList<>();
        for (GenAIProviderCandidate c : candidatesFor(preference)) {
            ids.add(c.id());
        }
        return ids;
    }
}
