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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    // Configures using the `openai.apiKey`, `openai.orgId`, `openai.projectId`,
    // `openai.webhookSecret` and `openai.baseUrl` system properties.
    // Or configures using the `OPENAI_API_KEY`, `OPENAI_ORG_ID`, `OPENAI_PROJECT_ID`,
    // `OPENAI_WEBHOOK_SECRET` and `OPENAI_BASE_URL` environment variables.
    // Don't create more than one client in the same application. Each client has
    // a connection pool and thread pools, which are more efficient to share between requests.
    static final OpenAIClientAsync singleton = OpenAIOkHttpClientAsync.fromEnv();
    /** Instance client will reuse connection and thread pool of singleton. */
    final OpenAIClientAsync client;
    final Properties context = new Properties();

    /**
     * Constructor.
     */
    public OpenAI() {
        client = singleton;
    }

    /**
     * Constructor.
     * @param apiKey API key for authentication and authorization.
     */
    public OpenAI(String apiKey) {
        client = singleton.withOptions(builder -> builder.apiKey(apiKey));
    }

    /**
     * For subclass which needs to customize the client.
     * @param client a client instance.
     */
    OpenAI(OpenAIClientAsync client) {
        this.client = client;
    }

    @Override
    public Properties context() {
        return context;
    }

    @Override
    public CompletableFuture<Response> request(String input) {
        var params = ResponseCreateParams.builder()
                .model(agent())
                .instructions(context.getProperty("instructions"))
                .input(input)
                .build();

        return client.responses().create(params);
    }

    @Override
    public CompletableFuture<String> complete(String message) {
        var params = ChatCompletionCreateParams.builder()
                .model(coder())
                .stop("\n")
                .addSystemMessage(context.getProperty("instructions"))
                .addUserMessage(message)
                .build();
        return client.chat().completions().create(params)
                .thenApply(completion -> completion.choices().stream()
                            .flatMap(choice -> choice.message().content().stream())
                            .collect(Collectors.joining()));
    }

    @Override
    public CompletableFuture<Stream<String>> generate(String message) {
        var params = ChatCompletionCreateParams.builder()
                .model(coder())
                .maxCompletionTokens(2048)
                .addSystemMessage(context.getProperty("instructions"))
                .addUserMessage(message)
                .build();
        return client.chat().completions().create(params)
                .thenApply(completion -> completion.choices().stream()
                        .flatMap(choice -> choice.message().content().stream()));
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
}
