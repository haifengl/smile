/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.SubmissionPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import org.jboss.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.*;
import smile.llm.llama.*;
import smile.util.HuggingFaceHub;

/**
 * Application-scoped service that loads a Llama LLM and handles chat
 * completion requests.
 *
 * <p>The model is loaded once at application startup from the location
 * configured by {@code smile.chat.model}:
 * <ul>
 *   <li>If the value is an existing local directory, the model is loaded
 *       directly from that path.</li>
 *   <li>Else if the value looks like a Hugging Face Hub repository ID
 *       ({@code owner/name}), the required model files are downloaded to
 *       the local HF cache before loading.</li>
 *   <li>Otherwise the chat service stays unavailable (no HF download is
 *       attempted for filesystem-like paths).</li>
 * </ul>
 *
 * <p>If the model cannot be loaded, the service starts in an
 * <em>unavailable</em> state and every chat request returns HTTP 503.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class ChatService {
    private static final Logger logger = Logger.getLogger(ChatService.class);

    /** The loaded LLM; {@code null} when the model failed to load. */
    private Llama model;
    /**
     * Public model id exposed by the chat API (HF repo id or local directory
     * name). Independent of {@link Llama#toString()}, which still embeds a
     * Llama-family prefix for historical reasons.
     */
    private final String modelId;

    /**
     * Loads the LLM upon application start.
     * The {@code @ApplicationScoped} scope ensures the model is loaded once and reused.
     *
     * <p>After weights are loaded, a shared {@link smile.llm.cache.KvCachePool}
     * is allocated using {@link MemConfig#fractionStatic()} of the remaining
     * free GPU memory.
     *
     * @param config the chat service configuration.
     * @param mem    GPU memory budgeting configuration.
     */
    @Inject
    public ChatService(ChatServiceConfig config, MemConfig mem) {
        String modelSpec = config.model();
        this.modelId = publicModelId(modelSpec);
        try {
            double memFraction = mem.fractionStatic();
            Path localPath = Path.of(modelSpec);
            if (Files.isDirectory(localPath)) {
                model = Llama.build(modelSpec, config.tokenizer(),
                        config.maxBatchSize(), config.maxSeqLen(), config.device(), memFraction);
            } else if (looksLikeHuggingFaceRepoId(modelSpec)) {
                model = loadFromHuggingFace(config, memFraction);
            } else {
                logger.warnf("Chat model '%s' is neither a local directory nor a Hugging Face "
                        + "repository ID; chat completions will return HTTP 503", modelSpec);
            }
        } catch (Exception ex) {
            // Keep the service up in an unavailable state so classic ML / ONNX
            // endpoints still work; chat requests return HTTP 503.
            logger.warnf(ex, "Failed to load chat model '%s'; chat completions will return HTTP 503",
                    modelSpec);
        }
    }

    /**
     * Derives the public API model id from {@code smile.chat.model}.
     *
     * <ul>
     *   <li>Local directory → final path segment (e.g. {@code Llama3.1-8B-Instruct})</li>
     *   <li>Hugging Face repo id → as configured (e.g. {@code Qwen/Qwen2.5-7B-Instruct})</li>
     * </ul>
     *
     * @param modelSpec the configured {@code smile.chat.model} value.
     * @return the public model id.
     */
    static String publicModelId(String modelSpec) {
        if (modelSpec == null || modelSpec.isBlank()) {
            return "unknown";
        }
        String spec = modelSpec.trim();
        Path path = Path.of(spec);
        if (Files.isDirectory(path)) {
            Path fileName = path.getFileName();
            return fileName != null ? fileName.toString() : spec;
        }
        return spec;
    }

    /**
     * Returns {@code true} when {@code spec} looks like a Hugging Face Hub
     * repository ID ({@code owner/name}), not a filesystem path.
     *
     * <p>Rejects absolute paths, relative path prefixes ({@code ./}, {@code ../}),
     * Windows drive letters, and multi-segment paths such as
     * {@code serve/src/test/resources/...}.
     *
     * @param spec the configured {@code smile.chat.model} value.
     * @return {@code true} if the value should be resolved via Hugging Face Hub.
     */
    static boolean looksLikeHuggingFaceRepoId(String spec) {
        if (spec == null || spec.isBlank()) return false;
        String s = spec.trim();
        if (s.startsWith("/") || s.startsWith(".") || s.contains("\\") || s.contains(":")) {
            return false;
        }
        int slash = s.indexOf('/');
        if (slash <= 0 || slash != s.lastIndexOf('/') || slash == s.length() - 1) {
            return false;
        }
        return true;
    }

    /**
     * Returns {@code true} if the LLM model is loaded and ready.
     *
     * @return {@code true} if available.
     */
    public boolean isAvailable() {
        return model != null;
    }

    /**
     * Returns the public model id used in chat completion requests/responses.
     *
     * <p>This is the configured Hugging Face repo id or local directory name —
     * not a Meta/Llama-specific prefix — so future non-Llama models keep a
     * stable client-facing id.
     *
     * @return the model id, or {@code "unknown"} when the model is not loaded.
     */
    public String modelName() {
        return model != null ? modelId : "unknown";
    }

    /**
     * Returns {@code true} when {@code requested} may be served by the loaded model.
     *
     * <p>{@code null}, blank, or omitted requests are accepted and use the loaded
     * model. Otherwise the value must equal {@link #modelName()}.
     *
     * @param requested the {@code model} field from the chat completion request.
     * @return {@code true} if the request may proceed.
     */
    public boolean acceptsModel(String requested) {
        return matchesModelId(requested, modelName());
    }

    /**
     * Pure matching helper for {@link #acceptsModel(String)}.
     *
     * @param requested    request {@code model} value.
     * @param loadedModelId currently loaded model id from {@link #modelName()}.
     * @return {@code true} when the request should be accepted.
     */
    static boolean matchesModelId(String requested, String loadedModelId) {
        if (requested == null || requested.isBlank()) {
            return true;
        }
        return requested.trim().equals(loadedModelId);
    }

    /**
     * Completes a chat dialog.
     *
     * @param request   the chat completion request.
     * @param publisher the flow publisher that receives streamed token chunks.
     * @return the array of completion results, one per dialog in the batch.
     */
    public ChatCompletion[] complete(CompletionRequest request, SubmissionPublisher<String> publisher) {
        Message[][] dialogs = { request.messages };
        return model.chat(dialogs, request.maxTokens, request.temperature,
                request.topP, request.logprobs, request.seed, publisher);
    }

    /**
     * Downloads HuggingFace-format model files and returns a loaded Llama model.
     *
     * <p>Downloads {@code config.json}, {@code model.safetensors.index.json} (when
     * present), every safetensors shard listed in the index, and the SentencePiece
     * tokenizer ({@code original/tokenizer.model} or {@code tokenizer.model}).
     *
     * @param config the chat service configuration; {@code config.model()} is the HF repo ID.
     * @param memFractionStatic fraction of free GPU memory for the KV cache pool.
     * @return the loaded Llama model.
     * @throws Exception if a required file cannot be downloaded or the model fails to load.
     */
    private Llama loadFromHuggingFace(ChatServiceConfig config, double memFractionStatic) throws Exception {
        String repoId = config.model();
        logger.infof("Model directory '%s' not found locally. Downloading from Hugging Face Hub...", repoId);

        Path configPath = HuggingFaceHub.download(repoId, "config.json");
        String checkpointDir = configPath.getParent().toString();
        logger.infof("Downloaded config.json to %s", checkpointDir);

        Set<String> shards = resolveSafeTensorShards(repoId);
        for (String shard : shards) {
            logger.infof("Downloading safetensors shard: %s", shard);
            HuggingFaceHub.download(repoId, shard);
        }

        String tokenizerPath = resolveTokenizer(repoId, config.tokenizer());
        return Llama.build(checkpointDir, tokenizerPath,
                config.maxBatchSize(), config.maxSeqLen(), config.device(), memFractionStatic);
    }

    /**
     * Resolves the list of safetensors shard filenames for a HuggingFace repo.
     * Prefers {@code model.safetensors.index.json}; falls back to a single
     * {@code model.safetensors} file.
     */
    private Set<String> resolveSafeTensorShards(String repoId) throws IOException {
        Set<String> shards = new LinkedHashSet<>();
        try {
            Path indexPath = HuggingFaceHub.download(repoId, "model.safetensors.index.json");
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(indexPath.toFile());
            JsonNode weightMap = root.get("weight_map");
            if (weightMap == null || !weightMap.isObject()) {
                throw new IOException("Invalid model.safetensors.index.json: missing weight_map");
            }
            for (var entry : weightMap.properties()) {
                shards.add(entry.getValue().asString());
            }
            return shards;
        } catch (FileNotFoundException e) {
            logger.debugf("No model.safetensors.index.json in %s; trying model.safetensors", repoId);
        }

        // Single-file checkpoint.
        HuggingFaceHub.download(repoId, "model.safetensors");
        shards.add("model.safetensors");
        return shards;
    }

    /**
     * Resolves the tokenizer path: uses a configured local file when present,
     * otherwise downloads {@code original/tokenizer.model} (Llama 3+) or
     * {@code tokenizer.model} from the HuggingFace repo.
     */
    private String resolveTokenizer(String repoId, String configuredTokenizer) throws IOException {
        if (configuredTokenizer != null && !configuredTokenizer.isBlank()
                && Files.exists(Path.of(configuredTokenizer))) {
            return configuredTokenizer;
        }

        String[] candidates = {"original/tokenizer.model", "tokenizer.model"};
        for (String candidate : candidates) {
            try {
                Path path = HuggingFaceHub.download(repoId, candidate);
                logger.infof("Downloaded tokenizer: %s", path);
                return path.toString();
            } catch (FileNotFoundException ignored) {
                logger.debugf("Tokenizer not found at '%s'", candidate);
            }
        }
        throw new IOException("tokenizer.model not found in Hugging Face repository: " + repoId);
    }
}
