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

import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import com.openai.models.responses.Response;
import smile.studio.SmileStudio;

/**
 * LLM inference interface.
 *
 * @author Haifeng Li
 */
public interface LLM {
    /**
     * Returns the associated context object.
     * @return the associated context object.
     */
    Properties context();

    /**
     * Sends a request to LLM service.
     * @param input the input message.
     * @return a future of response object.
     */
    CompletableFuture<Response> request(String input);
    /**
     * Single line code completion.
     * @param message the user message.
     * @return a future of response message.
     */
    CompletableFuture<String> complete(String message);

    /**
     * Code block generation in an async streaming approach.
     * @param message the user message.
     * @param consumer the consumer of completion chunks.
     * @param handler the exception handler.
     */
    void generate(String message, Consumer<String> consumer, Function<Throwable, ? extends Void> handler);

    /**
     * Returns an LLM instance specified by app settings.
     * @return an LLM instance specified by app settings.
     */
    static Optional<LLM> getCoder() {
        try {
            var prefs = SmileStudio.preferences();
            var service = prefs.get("aiService", "OpenAI");

            LLM llm = switch (service) {
                case "OpenAI" -> {
                    var openai = new OpenAI();
                    openai.context().setProperty("model", prefs.get("azureOpenAIModel", "gpt-5.1-codex"));
                    yield openai;
                }

                case "Azure OpenAI" -> new AzureOpenAI(
                        prefs.get("azureOpenAIApiKey", ""),
                        prefs.get("azureOpenAIBaseUrl", ""),
                        prefs.get("azureOpenAIModel", "gpt-5.1-codex"));

                default -> {
                    System.out.println("Unknown AI service: " + service);
                    var openai = new OpenAI();
                    openai.context().setProperty("model", "gpt-5.1-codex");
                    yield openai;
                }
            };

            llm.context().setProperty("instructions", Prompt.smileDeveloper());
            return Optional.of(llm);
        } catch (Throwable t) {
            // It is often a rethrow exception
            System.err.println("Failed to initialize AI service: " + t.getCause());
        }

        return Optional.empty();
    }
}
