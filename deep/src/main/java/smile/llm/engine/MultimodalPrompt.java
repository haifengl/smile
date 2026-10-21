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
package smile.llm.engine;

/**
 * A prompt whose media (images, videos) was preprocessed by a model-specific processor. The engine only needs the
 * expanded token ids and whether vision tensors are present; the {@link ModelExecutor} that produced the prompt
 * interprets the rest.
 *
 * @author Haifeng Li
 */
public interface MultimodalPrompt {
    /**
     * Returns the prompt token ids with every media placeholder expanded to its final length.
     *
     * @return token ids.
     */
    int[] inputIds();

    /**
     * Returns whether the prompt carries vision tensors.
     *
     * @return {@code true} when media must be encoded during prefill.
     */
    boolean hasVision();
}
