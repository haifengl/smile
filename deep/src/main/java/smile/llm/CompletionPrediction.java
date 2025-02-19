/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm;

/**
 * Prompt completion prediction.
 * @param model the model used for the chat completion.
 * @param content the generated text completion.
 * @param promptTokens the list of prompt tokens.
 * @param completionTokens the list of generated tokens.
 * @param reason the finish reason.
 * @param logprobs the optional list of log probabilities of generated tokens.
 */
public record CompletionPrediction(String model, String content, int[] promptTokens, int[] completionTokens, FinishReason reason, float[] logprobs) {

}
