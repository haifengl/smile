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
import java.util.stream.Collectors;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

/**
 * OpenAI service.
 *
 * @author Haifeng Li
 */
public class OpenAI implements LLM {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OpenAI.class);
    // Configures using the `openai.apiKey`, `openai.orgId`, `openai.projectId`,
    // `openai.webhookSecret` and `openai.baseUrl` system properties.
    // Or configures using the `OPENAI_API_KEY`, `OPENAI_ORG_ID`, `OPENAI_PROJECT_ID`,
    // `OPENAI_WEBHOOK_SECRET` and `OPENAI_BASE_URL` environment variables.
    // Don't create more than one client in the same application. Each client has
    // a connection pool and thread pools, which are more efficient to share between requests.
    static final OpenAIClientAsync singleton = OpenAIOkHttpClientAsync.fromEnv();
    /** Instance client will reuse connection and thread pool of singleton. */
    final OpenAIClientAsync client;
    /** The client for legacy APIs. */
    final OpenAIClientAsync legacy;
    /** The context object. */
    final Properties context = new Properties();

    /**
     * Constructor.
     */
    public OpenAI() {
        this(singleton, singleton);
    }

    /**
     * Constructor.
     * @param apiKey API key for authentication and authorization.
     */
    public OpenAI(String apiKey) {
        this(singleton.withOptions(builder -> builder.apiKey(apiKey)));
    }

    /**
     * Constructor with customized client.
     * @param client a client instance for responses API class.
     */
    OpenAI(OpenAIClientAsync client) {
        this(client, client);
    }

    /**
     * Constructor with customized client.
     * @param client a client instance for responses API class.
     * @param legacy a client instance for legacy API calls.
     */
    OpenAI(OpenAIClientAsync client, OpenAIClientAsync legacy) {
        this.client = client;
        this.legacy = legacy;
    }

    @Override
    public Properties context() {
        return context;
    }

    @Override
    public CompletableFuture<Response> request(String input) {
        var params = ResponseCreateParams.builder()
                .model(agent())
                .maxOutputTokens(maxOutputTokens(8192))
                .instructions(context.getProperty("instructions"))
                .input(input)
                .build();

        return client.responses().create(params);
    }

    @Override
    public CompletableFuture<String> complete(String message) {
        var params = ChatCompletionCreateParams.builder()
                .model(coder())
                .n(1) // only 1 chat completion choice to generate
                .temperature(0.2) // low temperature for more predictable, focused, and deterministic code
                .stop("\n") // stop at the end of line
                .addDeveloperMessage(context.getProperty("instructions"))
                .addUserMessage(message)
                .build();
        return legacy.chat().completions().create(params)
                .thenApply(completion -> completion.choices().stream()
                            .flatMap(choice -> choice.message().content().stream())
                            .collect(Collectors.joining()));
    }

    @Override
    public void generate(String message, Consumer<String> consumer, Function<Throwable, ? extends Void> handler) {
        var params = ChatCompletionCreateParams.builder()
                .model(coder())
                .n(1) // only 1 chat completion choice to generate
                .temperature(0.2) // low temperature for more predictable, focused, and deterministic code
                .maxCompletionTokens(maxOutputTokens(2048))
                .addDeveloperMessage(context.getProperty("instructions"))
                .addUserMessage(message)
                .build();
        legacy.chat().completions().createStreaming(params)
                .subscribe(completion -> completion.choices().stream()
                        .flatMap(choice -> choice.delta().content().stream())
                        .forEach(consumer))
                .onCompleteFuture()
                .exceptionally(handler)
                .join();
    }

    /**
     * Returns the model for agentic workflows.
     * @return the model for agentic workflows.
     */
    public String agent() {
        return context.getProperty("model", ChatModel.GPT_5_1.toString());
    }

    /**
     * Returns the model for coding.
     * @return the model for coding.
     */
    public String coder() {
        return context.getProperty("model", ChatModel.GPT_5_1_CODEX.toString());
    }

    /**
     * Returns the upper bound for the number of tokens that can be generated
     * for a response, including visible output tokens and reasoning tokens.
     * @param defaultValue the default value if the user doesn't set the context property.
     * @return the upper bound for the number of output tokens.
     */
    private int maxOutputTokens(int defaultValue) {
        String maxOutputTokens = context.getProperty("maxOutputTokens", String.valueOf(defaultValue));
        try {
            return Integer.parseInt(maxOutputTokens);
        } catch (NumberFormatException ex) {
            logger.error("Invalid maxOutputTokens: {}", maxOutputTokens);
            return defaultValue;
        }
    }
}
