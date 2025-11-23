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
package smile.studio.agent;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

/**
 * Google Gemini.
 *
 * @author Haifeng Li
 */
public class GoogleGemini implements LLM {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GoogleGemini.class);
    /** Instance client will reuse connection and thread pool of singleton. */
    final Client client;
    /** The context object. */
    final Properties context = new Properties();

    /**
     * Constructor.
     * @param apiKey API key for authentication and authorization.
     */
    public GoogleGemini(String apiKey) {
        this(Client.builder().apiKey(apiKey).build());
    }

    /**
     * Constructor with customized client.
     * @param client a client instance.
     */
    GoogleGemini(Client client) {
        this.client = client;
    }

    @Override
    public Properties context() {
        return context;
    }

    @Override
    public CompletableFuture<String> complete(String message) {
        var instructions = Content.fromParts(Part.fromText(context.getProperty("instructions")));
        var config = GenerateContentConfig.builder()
                .candidateCount(1)
                .temperature(0.2f)
                .stopSequences("\n")
                .systemInstruction(instructions)
                .build();
        return client.async.models.generateContent(model(), message, config)
                .thenApply(GenerateContentResponse::text);
    }

    @Override
    public void generate(String message, Consumer<String> consumer, Function<Throwable, ? extends Void> handler) {
        var instructions = Content.fromParts(Part.fromText(context.getProperty("instructions")));
        var config = GenerateContentConfig.builder()
                .candidateCount(1)
                .temperature(0.2f)
                .maxOutputTokens(maxOutputTokens(2048))
                .systemInstruction(instructions)
                .build();

        // To save resources and avoid connection leaks, close the
        // response stream after consumption.
        try (var stream = client.models.generateContentStream(model(), message, config)) {
            for (var response : stream) {
                consumer.accept(response.text());
            }
        } catch (Throwable t) {
            handler.apply(t);
        }
    }

    /**
     * Returns the configured model.
     * @return the configured model.
     */
    String model() {
        return context.getProperty("model", "gemini-3-pro-preview");
    }
}
