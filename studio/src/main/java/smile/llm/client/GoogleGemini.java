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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import smile.llm.Message;

/**
 * Google Gemini.
 *
 * @author Haifeng Li
 */
public class GoogleGemini extends LLM {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GoogleGemini.class);
    /** Instance client will reuse connection and thread pool of singleton. */
    final Client client;

    /**
     * Constructor.
     * @param apiKey API key for authentication and authorization.
     * @param model the model name.
     */
    public GoogleGemini(String apiKey, String model) {
        this(Client.builder().apiKey(apiKey).build(), model);
    }

    /**
     * Constructor with customized client.
     * @param client a client instance.
     * @param model the model name.
     */
    public GoogleGemini(Client client, String model) {
        super(model);
        this.client = client;
    }

    /**
     * Returns a chat request configuration.
     * @param params the request parameters.
     * @return a chat request configuration.
     */
    private GenerateContentConfig config(Properties params) {
        // only 1 chat completion choice to generate
        var builder = GenerateContentConfig.builder().candidateCount(1);

        var temperature = params.getProperty(TEMPERATURE, "");
        if (!temperature.isBlank()) {
            try {
                builder.temperature(Float.parseFloat(temperature));
            } catch (NumberFormatException ex) {
                logger.error("Invalid temperature: {}", temperature);
            }
        }

        var stop = params.getProperty(STOP, "");
        if (!stop.isBlank()) {
            builder.stopSequences(stop);
        }

        String maxOutputTokens = params.getProperty(MAX_OUTPUT_TOKENS, "");
        if (!maxOutputTokens.isBlank()) {
            try {
                builder.maxOutputTokens(Integer.parseInt(maxOutputTokens));
            } catch (NumberFormatException ex) {
                logger.error("Invalid maxOutputTokens: {}", maxOutputTokens);
            }
        }

        var system = params.getProperty(SYSTEM_PROMPT, "");
        if (!system.isBlank()) {
            var instructions = Content.fromParts(Part.fromText(system));
            builder.systemInstruction(instructions);
        }
        return builder.build();
    }

    /**
     * Returns the request contents.
     * @param message the user message.
     * @param history the conversation history.
     * @return the request contents.
     */
    private List<Content> contents(String message, List<Message> history) {
        List<Content> contents = new ArrayList<>();
        for (var msg : history) {
            switch (msg.role()) {
                case user -> contents.add(Content.builder()
                        .role("user")
                        .parts(Part.fromText(msg.content()))
                        .build());
                case assistant -> contents.add(Content.builder()
                        .role("assistant")
                        .parts(Part.fromText(msg.content()))
                        .build());
            }
        }

        contents.add(Content.builder()
                .role("user")
                .parts(Part.fromText(message))
                .build());

        return contents;
    }

    @Override
    public CompletableFuture<String> complete(String message, List<Message> history, Properties params) {
        var config = config(params);
        return client.async.models
                .generateContent(model(), contents(message, history), config(params))
                .thenApply(GenerateContentResponse::text);
    }

    @Override
    public void complete(String message, List<Message> history, Properties params, StreamResponseHandler handler) {
        // To save resources and avoid connection leaks, close the
        // response stream after consumption.
        try (var stream = client.models.generateContentStream(model(), contents(message, history), config(params))) {
            for (var response : stream) {
                handler.onNext(response.text());
            }
            handler.onComplete(Optional.empty());
        } catch (Throwable t) {
            handler.onComplete(Optional.of(t));
        }
    }
}
