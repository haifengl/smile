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
    static Optional<LLM> getInstance() {
        try {
            var prefs = SmileStudio.preferences();
            var service = prefs.get("aiService", "OpenAI");

            LLM llm = switch (service) {
                case "OpenAI" -> {
                    var openai = new OpenAI();
                    openai.context().setProperty("model", prefs.get("openaiModel", "gpt-5.1-codex"));
                    yield openai;
                }

                case "Azure OpenAI" -> new AzureOpenAI(
                        prefs.get("azureOpenAIApiKey", ""),
                        prefs.get("azureOpenAIBaseUrl", ""),
                        prefs.get("azureOpenAIModel", "gpt-5.1-codex"));

                case "Anthropic" -> {
                    var anthropic = new Anthropic();
                    anthropic.context().setProperty("model", prefs.get("anthropicModel", "claude-sonnet-4-5"));
                    yield anthropic;
                }

                case "GoogleGemini" -> {
                    var gemini = new GoogleGemini(prefs.get("googleGeminiApiKey", ""));
                    gemini.context().setProperty("model", prefs.get("googleGeminiModel", "gemini-3-pro-preview"));
                    yield gemini;
                }

                case "GoogleVertexAI" -> {
                    var vertex = new GoogleVertexAI(
                            prefs.get("googleVertexAIApiKey", ""),
                            prefs.get("googleVertexAIBaseUrl", ""));
                    vertex.context().setProperty("model", prefs.get("googleVertexAIModel", "gemini-3-pro-preview"));
                    yield vertex;
                }

                default -> {
                    System.out.println("Unknown AI service: " + service);
                    var openai = new OpenAI();
                    openai.context().setProperty("model", "gpt-5.1-codex");
                    yield openai;
                }
            };

            return Optional.of(llm);
        } catch (Throwable t) {
            // It is often a rethrow exception
            System.err.println("Failed to initialize AI service: " + t.getCause());
        }

        return Optional.empty();
    }

    /**
     * Returns an LLM instance specified by app settings.
     * @return an LLM instance specified by app settings.
     */
    static Optional<LLM> getCoder() {
        var llm = getInstance();
        llm.ifPresent(model -> model.context().setProperty("instructions", Prompt.smileDeveloper()));
        return llm;
    }

    /**
     * Returns the upper bound for the number of tokens that can be generated
     * for a response, including visible output tokens and reasoning tokens.
     * @param defaultValue the default value if the user doesn't set the context property.
     * @return the upper bound for the number of output tokens.
     */
    default int maxOutputTokens(int defaultValue) {
        String maxOutputTokens = context().getProperty("maxOutputTokens", String.valueOf(defaultValue));
        try {
            return Integer.parseInt(maxOutputTokens);
        } catch (NumberFormatException ex) {
            System.err.println("Invalid maxOutputTokens: " + maxOutputTokens);
            return defaultValue;
        }
    }
}
