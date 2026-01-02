/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import java.io.IOException;
import java.util.concurrent.SubmissionPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import org.jboss.logging.Logger;
import smile.llm.*;
import smile.llm.llama.*;

/**
 * The chat completion service provider.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class ChatService {
    private static final Logger logger = Logger.getLogger(ChatService.class);
    /** The LLM models. */
    private Llama model;

    /**
     * Load LLM model upon application start.
     * The @ApplicationScoped scope ensures the models are loaded once and reused
     */
    @Inject
    public ChatService(ChatServiceConfig config) {
        try {
            model = Llama.build(config.model(), config.tokenizer(),
                    config.maxBatchSize(), config.maxSeqLen(), config.device());
        } catch (Throwable t) {
            logger.errorf(t, "Failed to load model '%s'", config.model());
        }
    }

    /**
     * Returns true if the service/model is available.
     * @return true if the service/model is available.
     */
    public boolean isAvailable() {
        return model != null;
    }

    /**
     * Completes a chat request.
     * @param request the chat request.
     * @param publisher the flow publisher.
     */
    public CompletionPrediction[] complete(CompletionRequest request, SubmissionPublisher<String> publisher) {
        Message[][] dialogs = { request.messages };
        return model.chat(dialogs, request.maxTokens, request.temperature,
                request.topP, request.logprobs, request.seed, publisher);
    }
}
