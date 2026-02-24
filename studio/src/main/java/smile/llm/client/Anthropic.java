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

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import com.anthropic.core.http.AsyncStreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextDelta;

/**
 * Anthropic service.
 *
 * @author Haifeng Li
 */
public class Anthropic extends LLM {
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
    AnthropicClientAsync client;

    /**
     * Constructor.
     * @param model the model name.
     */
    public Anthropic(String model) {
        this(singleton, model);
    }

    /**
     * Constructor with customized client.
     * @param client a client instance.
     * @param model the model name.
     */
    public Anthropic(AnthropicClientAsync client, String model) {
        super(model);
        this.client = client;
    }

    @Override
    public Anthropic withBaseUrl(String baseUrl) {
        client = client.withOptions(builder -> builder.baseUrl(baseUrl));
        return this;
    }

    @Override
    public Anthropic withApiKey(String apiKey) {
        client = client.withOptions(builder -> builder.replaceHeaders("x-api-key", apiKey));
        return this;
    }

    /**
     * Returns a chat request parameters.
     * @param message the user message.
     * @param params the request parameters.
     * @return a chat request parameters.
     */
    private MessageCreateParams build(String message, List<Message> history, Properties params) {
        var builder = MessageCreateParams.builder().model(model());

        var temperature = params.getProperty(TEMPERATURE, "");
        if (!temperature.isBlank()) {
            try {
                builder.temperature(Double.parseDouble(temperature));
            } catch (NumberFormatException ex) {
                logger.error("Invalid temperature: {}", temperature);
            }
        }

        var stop = params.getProperty(STOP, "");
        if (!stop.isBlank()) {
            builder.addStopSequence(stop);
        }

        String maxOutputTokens = params.getProperty(MAX_OUTPUT_TOKENS, "");
        if (!maxOutputTokens.isBlank()) {
            try {
                builder.maxTokens(Integer.parseInt(maxOutputTokens));
            } catch (NumberFormatException ex) {
                logger.error("Invalid maxOutputTokens: {}", maxOutputTokens);
            }
        }

        var system = params.getProperty(SYSTEM_PROMPT, "");
        if (!system.isBlank()) {
            builder.system(system);
        }

        for (var msg : history) {
            switch (msg.role()) {
                case user -> builder.addUserMessage(msg.content());
                case assistant -> builder.addAssistantMessage(msg.content());
            }
        }
        builder.addUserMessage(message);
        return builder.build();
    }

    @Override
    public CompletableFuture<String> complete(String message, List<Message> history, Properties params) {
        var request = build(message, history, params);
        var accumulator = MessageAccumulator.create();
        return client.messages().create(request)
                .thenApply(msg -> msg.content().stream()
                        .flatMap(block -> block.text().stream())
                        .map(TextBlock::text)
                        .collect(Collectors.joining()));
    }

    @Override
    public void complete(String message, List<Message> history, Properties params, StreamResponseHandler handler) {
        var request = build(message, history, params);
        var accumulator = MessageAccumulator.create();
        client.messages().createStreaming(request)
                .subscribe(new AsyncStreamResponse.Handler<>() {
                    @Override
                    public void onNext(RawMessageStreamEvent chunk) {
                        accumulator.accumulate(chunk).contentBlockDelta().stream()
                                .flatMap(block -> block.delta().text().stream())
                                .map(TextDelta::text)
                                .forEach(handler::onNext);
                    }

                    @Override
                    public void onComplete(Optional<Throwable> error) {
                        handler.onComplete(error);
                    }
        });
    }
}
