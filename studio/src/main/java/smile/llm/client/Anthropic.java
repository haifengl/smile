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
import com.anthropic.helpers.BetaMessageAccumulator;
import com.anthropic.models.beta.messages.*;
import smile.llm.Message;
import smile.llm.tool.*;

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
     * Returns a chat completion request builder.
     * @param params the request parameters.
     * @param toolCalls If true, the request will be configured to handle tool calls.
     * @return a chat completion request builder.
     */
    private MessageCreateParams.Builder requestBuilder(Properties params, boolean toolCalls) {
        var builder = MessageCreateParams.builder().model(model());

        if (toolCalls) {
            builder.addTool(Read.class)
                   .addTool(Write.class)
                   .addTool(Append.class)
                   .addTool(Edit.class)
                   .addTool(Bash.class);
        }

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

        return builder;
    }

    /**
     * Adds the request input to the request builder.
     * @param message the user message.
     * @param conversation the conversation history.
     * @return the updated request builder.
     */
    private MessageCreateParams.Builder input(MessageCreateParams.Builder builder,
                                              String message, List<Message> conversation) {
        for (var msg : conversation) {
            switch (msg.role()) {
                case user -> builder.addUserMessage(msg.content());
                case assistant -> builder.addAssistantMessage(msg.content());
            }
        }
        builder.addUserMessage(message);
        return builder;
    }

    @Override
    public CompletableFuture<String> complete(String message, Properties params) {
        var request = requestBuilder(params, false);
        return client.beta().messages().create(request.addUserMessage(message).build())
                .thenApply(msg -> msg.content().stream()
                        .flatMap(block -> block.text().stream())
                        .map(BetaTextBlock::text)
                        .collect(Collectors.joining()));
    }

    @Override
    public void complete(String message, List<Message> conversation, Properties params, StreamResponseHandler handler) {
        var request = requestBuilder(params, true);
        complete(input(request, message, conversation), handler);
    }

    /**
     * Completes a chat message in a streaming way, with tool calls handling.
     * @param request the chat completion request builder.
     * @param handler the stream response handler.
     */
    private void complete(MessageCreateParams.Builder request, StreamResponseHandler handler) {
        var accumulator = BetaMessageAccumulator.create();
        client.beta().messages().createStreaming(request.build())
                .subscribe(new AsyncStreamResponse.Handler<>() {
                    @Override
                    public void onNext(BetaRawMessageStreamEvent chunk) {
                        accumulator.accumulate(chunk);
                        chunk.contentBlockDelta().stream()
                                .flatMap(block -> block.delta().text().stream())
                                .map(BetaTextDelta::text)
                                .forEach(handler::onNext);
                    }

                    @Override
                    public void onComplete(Optional<Throwable> error) {
                        if (error.isEmpty()) {
                            long toolCallCount = accumulator.message().content().stream()
                                    .flatMap(block -> block.toolUse().stream())
                                    .map(toolUse -> request
                                            // Add a message indicating that the tool use was requested.
                                            .addAssistantMessageOfBetaContentBlockParams(
                                                    List.of(BetaContentBlockParam.ofToolUse(BetaToolUseBlockParam.builder()
                                                            .name(toolUse.name())
                                                            .id(toolUse.id())
                                                            .input(toolUse.toParam()._input())
                                                            .build())))
                                            // Add a message with the result of the requested tool use.
                                            .addUserMessageOfBetaContentBlockParams(
                                                    List.of(BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder()
                                                            .toolUseId(toolUse.id())
                                                            .contentAsJson(callTool(toolUse))
                                                            .build()))))
                                    .count();

                            if (toolCallCount > 0) {
                                // Continue the conversation after tool calls.
                                complete(request, handler);
                            } else {
                                handler.onComplete(error);
                            }
                        } else {
                            handler.onComplete(error);
                        }
                    }
                });
    }

    /**
     * Calls the tool and returns the result.
     * @param tool the tool function to call.
     * @return the tool call result.
     */
    private Object callTool(BetaToolUseBlock tool) {
        return switch (tool.name()) {
            case "Read" -> tool.input(Read.class).run();
            case "Write" -> tool.input(Write.class).run();
            case "Append" -> tool.input(Append.class).run();
            case "Edit" -> tool.input(Edit.class).run();
            case "Bash" -> tool.input(Bash.class).run();
            default ->
                    throw new IllegalArgumentException("Unknown tool: " + tool.name());
        };
    }
}
