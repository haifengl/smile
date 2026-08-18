/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm;

import java.util.concurrent.SubmissionPublisher;

/**
 * Common façade for chat-capable decoder LLMs (Llama, Qwen, …).
 *
 * @author Haifeng Li
 */
public interface LanguageModel {
    /** Architecture family label (e.g. {@code meta/llama3}, {@code alibaba/qwen3.5}). */
    String family();

    /** Model instance / checkpoint name. */
    String name();

    /**
     * Generates completions from tokenized prompts.
     */
    ChatCompletion[] generate(int[][] prompts, int maxGenLen, double temperature,
                              double topp, boolean logprobs, long seed,
                              SubmissionPublisher<String> publisher);

    /**
     * Generates assistant replies for dialogs.
     */
    ChatCompletion[] chat(Message[][] dialogs, int maxGenLen, double temperature,
                          double topp, boolean logprobs, long seed,
                          SubmissionPublisher<String> publisher);
}
