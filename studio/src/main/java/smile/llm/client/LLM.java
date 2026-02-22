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

/**
 * LLM inference interface.
 *
 * @author Haifeng Li
 */
public interface LLM {
    /** The property key for the model. */
    String MODEL = "model";
    /** The property key for the system prompt. */
    String SYSTEM_PROMPT = "";
    /** The property key for the upper bound of output tokens. */
    String MAX_OUTPUT_TOKENS = "maxOutputTokens";

    /**
     * Returns the LLM API call options.
     * @return the LLM API call options.
     */
    Properties options();

    /**
     * Asynchronously completes a message.
     * @param message the user message.
     * @return a future of completion.
     */
    CompletableFuture<String> complete(String message);

    /**
     * Asynchronously completes a message in a streaming way.
     * @param message the user message.
     * @param consumer the consumer of completion chunks.
     * @param handler the exception handler.
     */
    void complete(String message, Consumer<String> consumer, Function<Throwable, ? extends Void> handler);

    /**
     * Returns the upper bound for the number of tokens that can be generated
     * for a response, including visible output tokens and reasoning tokens.
     * @param defaultValue the default value if the user doesn't set the context property.
     * @return the upper bound for the number of output tokens.
     */
    default int maxOutputTokens(int defaultValue) {
        String maxOutputTokens = options().getProperty("maxOutputTokens", String.valueOf(defaultValue));
        try {
            return Integer.parseInt(maxOutputTokens);
        } catch (NumberFormatException ex) {
            System.err.println("Invalid maxOutputTokens: " + maxOutputTokens);
            return defaultValue;
        }
    }
}
