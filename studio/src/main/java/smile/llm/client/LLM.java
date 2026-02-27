/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.client;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import smile.llm.Conversation;

/**
 * LLM inference client. Note that it is stateless and doesn't
 * save conversation history. The conversation history is passed
 * in the method parameter and the client is responsible for
 * formatting the conversation history into the prompt for LLM.
 * The client is also responsible for formatting the system prompt
 * and user message into the prompt for LLM.
 *
 *
 * @author Haifeng Li
 */
public abstract class LLM {
    /** The property key for the model. */
    public static final String MODEL = "model";
    /** The property key for the system prompt. */
    public static final String SYSTEM_PROMPT = "systemPrompt";
    /** The property key for the conversation history. */
    public static final String HISTORY = "history";
    /** The property key for the temperature. */
    public static final String TEMPERATURE = "temperature";
    /** The property key for the upper bound of output tokens. */
    public static final String MAX_OUTPUT_TOKENS = "maxOutputTokens";
    /** The property key for the stop token. */
    public static final String STOP = "stop";
    /** The model name in requests. */
    private String model;

    /**
     * Constructor.
     * @param model the model name, aka the deployment name in Azure.
     */
    public LLM(String model) {
        this.model = model;
    }

    /**
     * Returns the model name.
     * @return the model name.
     */
    public String model() {
        return model;
    }

    /**
     * Sets the LLM model.
     * @param model the model name.
     * @return this object.
     */
    public LLM withModel(String model) {
        this.model = model;
        return this;
    }

    /**
     * Sets the base URL of LLM service.
     * @param baseUrl the base URL of LLM service.
     * @return this object.
     */
    public LLM withBaseUrl(String baseUrl) {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the API key.
     * @param apiKey the API key.
     * @return this object.
     */
    public LLM withApiKey(String apiKey) {
        throw new UnsupportedOperationException();
    }

    /**
     * Asynchronously completes a message, without conversation history
     * and tool calls handling.
     * @param message the user message.
     * @return a future of completion.
     */
    public CompletableFuture<String> complete(String message) {
        return complete(message, new Properties());
    }

    /**
     * Asynchronously completes a message, without conversation history
     * and tool calls handling.
     * @param message the user message.
     * @param params the request parameters.
     * @return a future of completion.
     */
    public abstract CompletableFuture<String> complete(String message, Properties params);

    /**
     * Asynchronously completes a message in a streaming way, with tool calls handling.
     * @param message the user message.
     * @param conversation the conversation history in chronological order.
     * @param params the request parameters.
     * @param handler the stream response handler.
     */
    public abstract void complete(String message, Conversation conversation,
                                  Properties params, StreamResponseHandler handler);
}
