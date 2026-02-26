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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.*;
import com.openai.models.responses.*;
import smile.llm.Message;
import smile.llm.tool.*;

/**
 * OpenAI service.
 *
 * @author Haifeng Li
 */
public class OpenAI extends LLM {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OpenAI.class);
    /**
     * Don't create more than one client in the same application. Each client has
     * a connection pool and thread pools, which are more efficient to share between requests.
     * Configures using the `openai.apiKey`, `openai.orgId`, `openai.projectId`,
     * `openai.webhookSecret` and `openai.baseUrl` system properties.
     * Or configures using the `OPENAI_API_KEY`, `OPENAI_ORG_ID`, `OPENAI_PROJECT_ID`,
     * `OPENAI_WEBHOOK_SECRET` and `OPENAI_BASE_URL` environment variables.
     */
    static final OpenAIClientAsync singleton = OpenAIOkHttpClientAsync.fromEnv();
    /** Instance client will reuse connection and thread pool of singleton. */
    OpenAIClientAsync client;

    /**
     * Constructor.
     * @param model the model name, aka the deployment name in Azure.
     */
    public OpenAI(String model) {
        this(singleton, model);
    }

    /**
     * Constructor with customized client.
     * @param client a client instance.
     * @param model the model name, aka the deployment name in Azure.
     */
    public OpenAI(OpenAIClientAsync client, String model) {
        super(model);
        this.client = client;
    }

    /**
     * Returns an instance of Azure OpenAI deployment.
     * @param apiKey API key for authentication and authorization.
     * @param baseUrl the base URL for the service.
     * @param model the model name, aka the deployment name in Azure.
     * @return an instance of Azure OpenAI deployment.
     */
    public static OpenAI azure(String apiKey, String baseUrl, String model) {
        // The new client will reuse connection and thread pool
        var client = OpenAI.singleton.withOptions(builder -> builder.baseUrl(baseUrl + "/openai")
                .credential(AzureApiKeyCredential.create(apiKey))
                .azureServiceVersion(AzureOpenAIServiceVersion.fromString("2025-04-01-preview"))
                .azureUrlPathMode(AzureUrlPathMode.UNIFIED));
        return new OpenAI(client, model);
    }

    /**
     * Returns an instance of Azure OpenAI deployment with legacy URL path.
     * @param apiKey API key for authentication and authorization.
     * @param baseUrl the base URL for the service.
     * @param model the model name, aka the deployment name in Azure.
     * @return an instance of Azure OpenAI deployment with legacy URL path.
     */
    public static OpenAI legacy(String apiKey, String baseUrl, String model) {
        // The new client will reuse connection and thread pool
        var client = OpenAI.singleton.withOptions(builder -> builder.baseUrl(baseUrl)
                .credential(AzureApiKeyCredential.create(apiKey))
                .azureServiceVersion(AzureOpenAIServiceVersion.fromString("2025-04-01-preview"))
                .azureUrlPathMode(AzureUrlPathMode.LEGACY));
        return new OpenAI(client, model);
    }

    @Override
    public OpenAI withBaseUrl(String baseUrl) {
        client = client.withOptions(builder -> builder.baseUrl(baseUrl));
        return this;
    }

    @Override
    public OpenAI withApiKey(String apiKey) {
        client = client.withOptions(builder -> builder.apiKey(apiKey));
        return this;
    }

    /**
     * Returns a future of response from OpenAI service.
     * @param message the user message.
     * @param params the request parameters.
     * @return a future of response.
     */
    private ResponseCreateParams.Builder responseBuilder(String message, List<Message> history, Properties params) {
        var builder = ResponseCreateParams.builder()
                .model(model())
                .addTool(Read.class)
                .addTool(Write.class)
                .addTool(Append.class)
                .addTool(Edit.class)
                .addTool(Bash.class);

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
            logger.warn("Stop sequences are not supported in the new responses API. The stop parameter is ignored: {}", stop);
            // builder.stop(stop);
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
            logger.debug("System prompt:\n{}", system);
            builder.instructions(system);
        }

        List<ResponseInputItem> input = new ArrayList<>();
        for (var msg : history) {
            var role = switch (msg.role()) {
                case user -> EasyInputMessage.Role.USER;
                case assistant -> EasyInputMessage.Role.ASSISTANT;
                default -> null;
            };
            if (role != null) {
                input.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                        .role(role)
                        .content(msg.content())
                        .build()));
            }
        }

        input.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.USER)
                .content(message)
                .build()));
        builder.inputOfResponse(input);
        return builder;
    }

    /**
     * Returns a chat completion request builder.
     * @param message the user message.
     * @param params the request parameters.
     * @return a chat completion request builder.
     */
    private ChatCompletionCreateParams.Builder requestBuilder(String message, List<Message> history, Properties params) {
        // only 1 chat completion choice to generate
        var builder = ChatCompletionCreateParams.builder()
                .model(model())
                .n(1)
                .addTool(Read.class)
                .addTool(Write.class)
                .addTool(Append.class)
                .addTool(Edit.class)
                .addTool(Bash.class);

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
            builder.stop(stop);
        }

        String maxOutputTokens = params.getProperty(MAX_OUTPUT_TOKENS, "");
        if (!maxOutputTokens.isBlank()) {
            try {
                builder.maxCompletionTokens(Integer.parseInt(maxOutputTokens));
            } catch (NumberFormatException ex) {
                logger.error("Invalid maxOutputTokens: {}", maxOutputTokens);
            }
        }

        var system = params.getProperty(SYSTEM_PROMPT, "");
        if (!system.isBlank()) {
            logger.debug("System prompt:\n{}", system);
            builder.addDeveloperMessage(system);
        }

        for (var msg : history) {
            switch (msg.role()) {
                case user -> builder.addUserMessage(msg.content());
                case assistant -> builder.addAssistantMessage(msg.content());
            }
        }
        builder.addUserMessage(message);
        return builder;
    }

    @Override
    public CompletableFuture<String> complete(String message, List<Message> history, Properties params) {
        var request = requestBuilder(message, history, params);
        return client.chat().completions().create(request.build())
                .thenApply(completion -> completion.choices().stream()
                            .flatMap(choice -> choice.message().content().stream())
                            .collect(Collectors.joining()));
    }

    @Override
    public void complete(String message, List<Message> history, Properties params, StreamResponseHandler handler) {
        var request = requestBuilder(message, history, params);
        complete(request, handler);
    }

    /**
     * Completes a chat message in a streaming way, with tool calls handling.
     * @param request the chat completion request builder.
     * @param handler the stream response handler.
     */
    private void complete(ChatCompletionCreateParams.Builder request, StreamResponseHandler handler) {
        var accumulator = ChatCompletionAccumulator.create();
        client.chat().completions().createStreaming(request.build())
                .subscribe(new AsyncStreamResponse.Handler<>() {
                    @Override
                    public void onNext(ChatCompletionChunk chunk) {
                        accumulator.accumulate(chunk);
                        chunk.choices().stream()
                                .flatMap(choice -> choice.delta().content().stream())
                                .forEach(handler::onNext);
                    }

                    @Override
                    public void onComplete(Optional<Throwable> error) {
                        if (error.isEmpty()) {
                            long toolCallCount = accumulator.chatCompletion().choices().stream()
                                    .map(ChatCompletion.Choice::message)
                                    .peek(request::addMessage)
                                    .flatMap(message -> message.toolCalls().stream().flatMap(Collection::stream))
                                    .map(toolCall -> {
                                        var function = toolCall.asFunction().function();
                                        Object result = callTool(toolCall.asFunction().function());
                                        logger.debug("ToolCall({}) -> {}", function.name(), result);
                                        // Add the tool call result to the conversation.
                                        request.addMessage(ChatCompletionToolMessageParam.builder()
                                                .toolCallId(toolCall.asFunction().id())
                                                .contentAsJson(result)
                                                .build());
                                        return result;
                                    }).count();

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
    private Object callTool(ChatCompletionMessageFunctionToolCall.Function tool) {
        return switch (tool.name()) {
            case "Read" -> tool.arguments(Read.class).run();
            case "Write" -> tool.arguments(Write.class).run();
            case "Append" -> tool.arguments(Append.class).run();
            case "Edit" -> tool.arguments(Edit.class).run();
            case "Bash" -> tool.arguments(Bash.class).run();
            default ->
                throw new IllegalArgumentException("Unknown tool: " + tool.name());
        };
    }
}
