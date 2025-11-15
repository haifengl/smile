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

import java.util.concurrent.CompletableFuture;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

/**
 * AZure AI client.
 *
 * @author Haifeng Li
 */
public interface AZure {
    OpenAIAsyncClient client = new OpenAIClientBuilder()
            .credential(new AzureKeyCredential("05c3bd98e376410da4bc63b5aff2c6ed"))
            .endpoint("https://apimgenaidev.azure-api.net/shared-openai/deployments/gpt-4.1-shared/chat/completions?api-version=2025-01-01-preview")
            .buildAsyncClient();
    String codingSystemPrompt = """
            You are a highly skilled Java programming assistant.
            You are a machine learning expert and can build highly
            efficient model with latest SMILE library.
            Your task is to complete code snippets, adhering to
            the provided context and best practices. Ensure the
            completed code is syntactically correct and logically
            sound.""";
    String completionUserPrompt = """
            Complete the next line of Java code based on the provided context.%n%n
            Context:%n%s%n%n
            Current line start: %s""";
    String generationUserPrompt = """
            Generate Java code based on the provided context and task.%n%n
            Context:%n%s%n%n
            Task:%n%s%n%n""";

    /**
     * Completes a line of code.
     * @param context the previous lines of code.
     * @param start the current line start.
     * @return a future of completion.
     */
    static CompletableFuture<ChatCompletion> complete(String context, String start) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addSystemMessage(codingSystemPrompt)
                .addUserMessage(String.format(completionUserPrompt, context, start))
                .stop("\n")
                .model(ChatModel.GPT_5)
                .build();
        return client.async().chat().completions().create(params);
    }

    /**
     * Generates a piece of code.
     * @param context the previous lines of code.
     * @param task the user prompt of task.
     * @return a stream of completion chucks.
     */
    static AsyncStreamResponse<ChatCompletionChunk> generate(String context, String task) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addSystemMessage(codingSystemPrompt)
                .addUserMessage(String.format(generationUserPrompt, context, task))
                .model(ChatModel.GPT_5)
                .build();
        return client.async().chat().completions().createStreaming(params);
    }
}
