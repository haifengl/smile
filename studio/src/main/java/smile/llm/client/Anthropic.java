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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextDelta;

/**
 * Anthropic service.
 *
 * @author Haifeng Li
 */
public class Anthropic implements LLM {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Anthropic.class);
    /**
     * Don't create more than one client in the same application. Each client has
     * a connection pool and thread pools, which are more efficient to share between requests.
     * Configures using the `anthropic.apiKey`, `anthropic.authToken` and
     * `anthropic.baseUrl` system properties.
     * Or configures using the `ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN` and
     * `ANTHROPIC_BASE_URL` environment variables.
     */
    static final AnthropicClientAsync singleton = AnthropicOkHttpClientAsync.fromEnv();
    /** Instance client will reuse connection and thread pool of singleton. */
    final AnthropicClientAsync client;
    /** The context object. */
    final Properties context = new Properties();

    /**
     * Constructor.
     */
    public Anthropic() {
        this(singleton);
    }

    /**
     * Constructor.
     * @param baseUrl the base URL for the service.
     */
    public Anthropic(String baseUrl) {
        this(singleton.withOptions(builder -> builder.baseUrl(baseUrl)));
    }

    /**
     * Constructor with customized client.
     * @param client a client instance.
     */
    Anthropic(AnthropicClientAsync client) {
        this.client = client;
    }

    @Override
    public Properties context() {
        return context;
    }

    @Override
    public CompletableFuture<String> complete(String message) {
        var params = MessageCreateParams.builder()
                .model(model())
                .temperature(0.2) // low temperature for more predictable, focused, and deterministic code
                .addStopSequence("\n") // stop at the end of line
                .system(context.getProperty("instructions"))
                .addUserMessage(message)
                .build();

        var accumulator = MessageAccumulator.create();
        return client.messages().create(params)
                .thenApply(msg -> msg.content().stream()
                        .flatMap(block -> block.text().stream())
                        .map(TextBlock::text)
                        .collect(Collectors.joining()));
    }

    @Override
    public void complete(String message, Consumer<String> consumer, Function<Throwable, ? extends Void> handler) {
        var params = MessageCreateParams.builder()
                .model(model())
                .temperature(0.2) // low temperature for more predictable, focused, and deterministic code
                .maxTokens(maxOutputTokens(2048))
                .system(context.getProperty("instructions"))
                .addUserMessage(message)
                .build();

        var accumulator = MessageAccumulator.create();
        client.messages().createStreaming(params)
                .subscribe(event -> accumulator.accumulate(event).contentBlockDelta().stream()
                        .flatMap(block -> block.delta().text().stream())
                        .map(TextDelta::text)
                        .forEach(consumer))
                .onCompleteFuture()
                .exceptionally(handler)
                .join();
    }

    /**
     * Returns the configured model.
     * @return the configured model.
     */
    String model() {
        return context.getProperty("model", Model.CLAUDE_SONNET_4_5.toString());
    }
}
