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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.SubmissionPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import org.jboss.logging.Logger;
import smile.llm.*;
import smile.llm.llama.*;

/**
 * Application-scoped service that loads a Llama LLM and handles chat
 * completion requests.
 *
 * <p>The model is loaded once at application startup from the path
 * configured by {@code smile.chat.model}. If the path does not exist,
 * the service starts in an <em>unavailable</em> state and every request
 * will receive an HTTP 503 response.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class ChatService {
    private static final Logger logger = Logger.getLogger(ChatService.class);
    /** The loaded LLM; {@code null} when the model file is absent or failed to load. */
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
                logger.infof("LLM model '%s' doesn't exist. Chat service won't be available.", config.model());
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
}
