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

/**
 * In LLM serving, Radix KV cache management uses a Radix Tree (compressed
 * prefix tree) to organize and reuse Key-Value (KV) cache activations across
 * multiple requests. Instead of recomputing shared system prompts or
 * conversation histories, the inference engine finds the longest matching
 * prefix and only computes the diverging suffix, saving enormous amounts
 * of GPU memory and compute cycles.
 * <p>
 * Every node in the radix tree represents a specific token prefix.
 * If 1,000 requests are all prepended with the same 512-token system prompt,
 * the KV state for that prompt is computed only once and shared across all
 * requests.
 * <p>
 * When conversation paths or agent workflows diverge, the tree branches.
 * This allows the server to retain the shared history (e.g., turns 1
 * through 9) while only caching or computing the new, unique turn for
 * each specific request.
 * <p>
 * Incoming prompts are matched against paths in the tree, allowing for
 * instantaneous reuse of existing K and V values before the decoding
 * phase even starts.
 *
 * @author Haifeng Li
 */
package smile.llm.cache;
