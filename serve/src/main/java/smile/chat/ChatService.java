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
import java.util.Objects;
import java.util.concurrent.SubmissionPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import org.jboss.logging.Logger;
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
 *   <li>Otherwise the value is treated as a Hugging Face Hub repository ID
 *       (e.g. {@code meta-llama/Meta-Llama-3-8B}) and the required model
 *       files are downloaded to the local HF cache before loading.</li>
 * </ul>
 *
 * <p>If the model cannot be loaded, the service starts in an
 * <em>unavailable</em> state and every request returns HTTP 503.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class ChatService {
    private static final Logger logger = Logger.getLogger(ChatService.class);

    /** Candidate locations for {@code params.json} inside a HF model repo. */
    private static final String[] PARAMS_LOCATIONS = {"params.json", "original/params.json"};

    /** The loaded LLM; {@code null} when the model failed to load. */
    private Llama model;

    /**
     * Loads the LLM upon application start.
     * The {@code @ApplicationScoped} scope ensures the model is loaded once and reused.
     *
     * @param config the chat service configuration.
     */
    @Inject
    public ChatService(ChatServiceConfig config) {
        try {
            if (Files.exists(Path.of(config.model()))) {
                model = Llama.build(config.model(), config.tokenizer(),
                        config.maxBatchSize(), config.maxSeqLen(), config.device());
            } else {
                model = loadFromHuggingFace(config);
            }
        } catch (Exception ex) {
            logger.errorf(ex, "Failed to load model '%s'", config.model());
        }
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
     * Returns the fully-qualified model identifier (e.g. {@code meta/llama3/Meta-Llama-3-8B}).
     *
     * @return the model name string, or {@code "unknown"} when the model is not loaded.
     */
    public String modelName() {
        return model != null ? model.toString() : "unknown";
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
     * Downloads model files from the Hugging Face Hub and returns a loaded Llama model.
     *
     * <p>The method performs the following steps:
     * <ol>
     *   <li>Searches for {@code params.json} in the repo root, then falls back to
     *       {@code original/params.json} (the standard layout for Llama 3+).</li>
     *   <li>Downloads the checkpoint shards ({@code consolidated.00.pt}, …
     *       up to the number of shards indicated by the {@code WORLD_SIZE}
     *       environment variable, defaulting to {@code 1}).</li>
     *   <li>Resolves the tokenizer: uses {@code config.tokenizer()} if it points
     *       to an existing local file; otherwise downloads {@code tokenizer.model}
     *       from the same repository.</li>
     *   <li>Calls {@link Llama#build} with the local HF-cache snapshot directory
     *       as the checkpoint directory.</li>
     * </ol>
     *
     * @param config the chat service configuration; {@code config.model()} is the HF repo ID.
     * @return the loaded Llama model.
     * @throws Exception if a required file cannot be downloaded or the model fails to load.
     */
    private Llama loadFromHuggingFace(ChatServiceConfig config) throws Exception {
        String repoId = config.model();
        logger.infof("Model directory '%s' not found locally. Downloading from Hugging Face Hub...", repoId);

        // Locate params.json: try the repo root first, then original/ (Llama 3+).
        Path paramsPath = null;
        String subfolder = null;
        for (String candidate : PARAMS_LOCATIONS) {
            try {
                paramsPath = HuggingFaceHub.download(repoId, candidate);
                if (candidate.contains("/")) {
                    subfolder = candidate.substring(0, candidate.lastIndexOf('/'));
                }
                logger.infof("Found params.json at: %s", paramsPath);
                break;
            } catch (FileNotFoundException ignored) {
                logger.debugf("params.json not found at '%s', trying next candidate.", candidate);
            }
        }
        if (paramsPath == null) {
            throw new IOException("params.json not found in Hugging Face repository: " + repoId);
        }

        // The parent directory of params.json is the checkpoint directory for Llama.build().
        String checkpointDir = paramsPath.getParent().toString();

        // Download checkpoint shards: consolidated.00.pt … consolidated.0N.pt.
        int worldSize = Integer.parseInt(
                Objects.requireNonNullElse(System.getenv("WORLD_SIZE"), "1"));
        for (int i = 0; i < worldSize; i++) {
            String name = String.format("consolidated.%02d.pt", i);
            String filename = subfolder != null ? subfolder + "/" + name : name;
            logger.infof("Downloading checkpoint shard: %s", filename);
            HuggingFaceHub.download(repoId, filename);
        }

        // Resolve the tokenizer path.
        String configuredTokenizer = config.tokenizer();
        String tokenizerPath;
        if (!configuredTokenizer.isBlank() && Files.exists(Path.of(configuredTokenizer))) {
            tokenizerPath = configuredTokenizer;
        } else {
            String tokenizerFile = subfolder != null
                    ? subfolder + "/tokenizer.model"
                    : "tokenizer.model";
            logger.infof("Downloading tokenizer from HF repo: %s", tokenizerFile);
            tokenizerPath = HuggingFaceHub.download(repoId, tokenizerFile).toString();
        }

        return Llama.build(checkpointDir, tokenizerPath,
                config.maxBatchSize(), config.maxSeqLen(), config.device());
    }
}
