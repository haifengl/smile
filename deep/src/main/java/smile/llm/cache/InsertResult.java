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
package smile.llm.cache;

/**
 * Result returned by {@link RadixCache#insert}.
 *
 * @param prefixLen the number of tokens that were already present in the cache
 *                  before this insert. The caller may free the duplicate KV
 *                  cache entries for indices {@code [0, prefixLen)}.
 * @param lastNode  the {@link RadixTreeNode} that holds the newly inserted (or
 *                  previously cached) tokens. The caller should keep a
 *                  reference to this node for lock-reference management.
 *
 * @author Haifeng Li
 */
public record InsertResult(int prefixLen, RadixTreeNode lastNode) {}
