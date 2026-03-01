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
import smile.llm.Conversation;
import smile.llm.Message;
import smile.llm.ToolCallOutput;
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
     * Returns a response request builder.
     * @param params the request parameters.
     * @param tools the tools available for LLM.
     * @return a response request builder.
     */
    private ResponseCreateParams.Builder responseBuilder(Properties params,
                                                         List<Class<? extends smile.llm.tool.Tool>> tools) {
        var builder = ResponseCreateParams.builder().model(model());

        for (var tool : tools) {
            builder.addTool(tool);
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

        return builder;
    }

    /**
     * Returns an input to response request.
     * @param message the user message.
     * @param messages the conversation history.
     * @return an input to response request.
     */
    private List<ResponseInputItem> input(String message, List<Message> messages) {
        List<ResponseInputItem> input = new ArrayList<>();
        for (var msg : messages) {
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

        return input;
    }

    /**
     * Returns a chat completion request builder.
     * @param params the request parameters.
     * @param tools the tools available for LLM.
     * @return a chat completion request builder.
     */
    private ChatCompletionCreateParams.Builder paramsBuilder(Properties params,
                                                             List<Class<? extends smile.llm.tool.Tool>> tools) {
        // only 1 chat completion choice to generate
        var builder = ChatCompletionCreateParams.builder().model(model()).n(1);

        for (var tool : tools) {
            builder.addTool(tool);
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

        return builder;
    }

    /** Extracts the text response. */
    private String response(ChatCompletion completion) {
        return completion.choices().stream()
                .flatMap(choice -> choice.message().content().stream())
                .collect(Collectors.joining());
    }

    @Override
    public CompletableFuture<String> complete(String message, Properties params) {
        var request = paramsBuilder(params, List.of());
        request.addUserMessage(message);
        return client.chat().completions().create(request.build())
                .thenApply(this::response);
    }

    @Override
    public void complete(String message, Conversation conversation, StreamResponseHandler handler) {
        conversation.add(Message.user(message));
        var request = paramsBuilder(conversation.params(), conversation.tools());
        for (var msg : conversation.messages()) {
            switch (msg.role()) {
                case user -> request.addUserMessage(msg.content());
                case assistant -> request.addAssistantMessage(msg.content());
                case tool -> {
                    request.addMessage((ChatCompletionMessage) msg.toolCall().message());
                    for (var toolCall : msg.toolCall().outputs()) {
                        request.addMessage(ChatCompletionToolMessageParam.builder()
                                .toolCallId(toolCall.id())
                                .contentAsJson(toolCall.output())
                                .build());
                    }
                }
            }
        }

        request.addUserMessage(message);
        // For streaming chat completions, token usage data is not included by default.
        // This will cause an additional, final chunk to be streamed at the end of the response.
        request.streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());
        complete(request, conversation, handler);
    }

    /**
     * Completes a chat message in a streaming way, with tool calls handling.
     * @param request the chat completion request builder.
     * @param handler the stream response handler.
     */
    private void complete(ChatCompletionCreateParams.Builder request, Conversation conversation, StreamResponseHandler handler) {
        var accumulator = ChatCompletionAccumulator.create();
        var stream = client.chat().completions().createStreaming(request.build());
        stream.subscribe(new AsyncStreamResponse.Handler<>() {
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
                    var completion = accumulator.chatCompletion();
                    completion.usage().ifPresent(usage ->
                            logger.info("Chat completion completed with {} total tokens, {} completion tokens and {} prompt tokens",
                                    usage.totalTokens(), usage.completionTokens(), usage.promptTokens()));

                    var toolCalling = completion.choices().stream()
                            .map(ChatCompletion.Choice::message)
                            .peek(request::addMessage)
                            .map(message -> {
                                var toolCalls = message.toolCalls().stream().flatMap(Collection::stream).map(toolCall -> {
                                    var tool = toolCall.asFunction();
                                    var id = tool.id();
                                    var func = tool.function();
                                    var input = func.arguments();
                                    logger.info("Tool call: name={}, input={}", func.name(), input);

                                    var output = callTool(func);
                                    var toolCallOutput = new ToolCallOutput(id, output);

                                    // Add the tool call result to the conversation.
                                    request.addMessage(ChatCompletionToolMessageParam.builder()
                                            .toolCallId(id)
                                            .contentAsJson(output)
                                            .build());
                                    return toolCallOutput;
                                }).toList();
                                return Message.toolCall(message, toolCalls);
                            }).toList();

                    boolean anyToolCalled = false;
                    for (var msg : toolCalling) {
                        if (!msg.toolCall().outputs().isEmpty()) {
                            anyToolCalled = true;
                            conversation.add(msg);
                        }
                    }

                    if (anyToolCalled) {
                        // Continue the conversation after tool calls.
                        complete(request, conversation, handler);
                    } else {
                        conversation.add(Message.assistant(response(completion)));
                    }
                } else {
                    conversation.add(Message.error(error.get().getMessage()));
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
    private String callTool(ChatCompletionMessageFunctionToolCall.Function tool) {
        return switch (tool.name()) {
            case "Read" -> tool.arguments(Read.class).run();
            case "Write" -> tool.arguments(Write.class).run();
            case "Append" -> tool.arguments(Append.class).run();
            case "Edit" -> tool.arguments(Edit.class).run();
            case "Bash" -> tool.arguments(Bash.class).run();
            default -> "Error: unsupported tool " + tool.name();
        };
    }
}
