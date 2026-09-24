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
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allowlist of architectures supported by ONNX Runtime GenAI for chat.
 *
 * <p>Updated as onnx-genai gains models. Whisper is listed but not chat-capable.
 *
 * @author Haifeng Li
 */
public final class GenAISupportedModels {
    private static final Pattern MODEL_TYPE = Pattern.compile(
            "\"model_type\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARCHITECTURES = Pattern.compile(
            "\"architectures\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);

    private GenAISupportedModels() {}

    /**
     * Chat-capable (and Whisper) families tracked for Olive / OGA conversion.
     */
    public enum Family {
        /** AMD OLMo. */
        AMD_OLMO(true, "olmo", "amd_olmo", "amd-olmo"),
        /** ChatGLM / GLM. */
        CHATGLM(true, "chatglm", "glm", "chatglm2", "chatglm3", "chatglm4"),
        /** DeepSeek. */
        DEEPSEEK(true, "deepseek", "deepseek_v2", "deepseek_v3", "deepseek_r1"),
        /** ERNIE. */
        ERNIE(true, "ernie", "ernie4", "ernie_4", "ernie45", "ernie_4_5"),
        /** Fara. */
        FARA(true, "fara"),
        /** Gemma. */
        GEMMA(true, "gemma", "gemma2", "gemma3", "google/gemma"),
        /** GPT-OSS. */
        GPT_OSS(true, "gpt-oss", "gpt_oss", "gptoss"),
        /** Granite. */
        GRANITE(true, "granite", "granite_moe", "granitemoe"),
        /** Granite MoE hybrid. */
        GRANITE_MOE_HYBRID(true, "granite_moe_hybrid", "granite-moe-hybrid"),
        /** Hunyuan. */
        HUNYUAN(true, "hunyuan", "hunyuan_dense", "hunyuandense"),
        /** InternLM. */
        INTERNLM2(true, "internlm", "internlm2", "internlm3"),
        /** LFM2. */
        LFM2(true, "lfm2", "lfm"),
        /** Llama. */
        LLAMA(true, "llama", "llama2", "llama3", "llama4", "meta-llama", "meta/llama"),
        /** Mistral / Mixtral. */
        MISTRAL(true, "mistral", "mixtral", "mistralai"),
        /** Nemotron. */
        NEMOTRON(true, "nemotron", "nvidia/nemotron"),
        /** Phi. */
        PHI(true, "phi", "phi2", "phi3", "phi3_5", "phi4", "microsoft/phi"),
        /** Qwen. */
        QWEN(true, "qwen", "qwen2", "qwen2_5", "qwen3", "qwen3_5", "qwen3_8"),
        /** SmolLM. */
        SMOLLM3(true, "smollm", "smollm2", "smollm3", "huggingfacetb/smollm"),
        /** Whisper (speech; not chat-capable). */
        WHISPER(false, "whisper");

        private final boolean chat;
        private final String[] aliases;

        Family(boolean chat, String... aliases) {
            this.chat = chat;
            this.aliases = aliases;
        }

        /**
         * Returns whether this family is suitable for chat completions.
         *
         * @return {@code true} when suitable for {@code /chat/completions}.
         */
        public boolean isChat() {
            return chat;
        }

        boolean matchesToken(String token) {
            if (token == null || token.isBlank()) {
                return false;
            }
            String t = normalize(token);
            for (String alias : aliases) {
                String a = normalize(alias);
                if (t.equals(a) || t.startsWith(a + "_") || t.startsWith(a + "-")
                        || t.contains("/" + a) || t.endsWith("/" + a)
                        || t.contains(a)) {
                    // Prefer precise starts for short aliases like "phi" vs "graph".
                    if (a.length() <= 3) {
                        if (t.equals(a) || t.startsWith(a + "_") || t.startsWith(a + "-")
                                || t.contains("/" + a)) {
                            return true;
                        }
                        continue;
                    }
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Matches a local HF checkpoint directory via {@code config.json}.
     *
     * @param checkpointDir model directory.
     * @return chat-capable family when listed; empty when unknown or Whisper.
     */
    public static Optional<Family> match(Path checkpointDir) {
        if (checkpointDir == null || !Files.isDirectory(checkpointDir)) {
            return Optional.empty();
        }
        Path config = checkpointDir.resolve("config.json");
        if (!Files.isRegularFile(config)) {
            return matchRepoId(checkpointDir.getFileName().toString());
        }
        try {
            String json = Files.readString(config);
            Matcher mt = MODEL_TYPE.matcher(json);
            if (mt.find()) {
                Optional<Family> f = matchToken(mt.group(1));
                if (f.isPresent()) {
                    return f.filter(Family::isChat);
                }
            }
            Matcher arch = ARCHITECTURES.matcher(json);
            if (arch.find()) {
                String body = arch.group(1);
                for (String part : body.split(",")) {
                    String cleaned = part.replace("\"", "").trim();
                    Optional<Family> f = matchToken(cleaned);
                    if (f.isPresent()) {
                        return f.filter(Family::isChat);
                    }
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return matchRepoId(checkpointDir.getFileName().toString());
    }

    /**
     * Matches a Hugging Face repo id or directory name heuristic.
     *
     * @param modelSpec {@code owner/name} or leaf directory name.
     * @return chat-capable family when listed.
     */
    public static Optional<Family> matchRepoId(String modelSpec) {
        if (modelSpec == null || modelSpec.isBlank()) {
            return Optional.empty();
        }
        return matchToken(modelSpec).filter(Family::isChat);
    }

    /**
     * Returns whether a plain HF checkpoint is eligible for Olive → OGA conversion
     * for chat (excludes Whisper and unknown families).
     *
     * @param checkpointDir local dir, or {@code null} to use {@code modelSpec} only.
     * @param modelSpec     HF id or path string.
     * @return {@code true} when allowlisted for chat OGA.
     */
    public static boolean isChatConvertible(Path checkpointDir, String modelSpec) {
        if (checkpointDir != null && Files.isDirectory(checkpointDir)) {
            Optional<Family> local = match(checkpointDir);
            if (local.isPresent()) {
                return true;
            }
        }
        return matchRepoId(modelSpec).isPresent();
    }

    static Optional<Family> matchToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        // Prefer longer / more specific families first where aliases overlap.
        Family[] order = {
                Family.GRANITE_MOE_HYBRID, Family.GRANITE, Family.GPT_OSS,
                Family.SMOLLM3, Family.INTERNLM2, Family.HUNYUAN, Family.NEMOTRON,
                Family.DEEPSEEK, Family.CHATGLM, Family.ERNIE, Family.LFM2,
                Family.AMD_OLMO, Family.FARA, Family.GEMMA, Family.MISTRAL,
                Family.PHI, Family.QWEN, Family.LLAMA, Family.WHISPER
        };
        for (Family f : order) {
            if (f.matchesToken(token)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replace('-', '_').trim();
    }
}
