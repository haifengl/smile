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
 * LSP (Language Server Protocol) client built on top of LSP4J.
 *
 * <p>The main entry point is {@code LanguageClient}, which
 * manages the full lifecycle of a language server process and exposes
 * high-level navigation and search operations.
 *
 * <h2>Supported operations</h2>
 * <ul>
 *   <li>{@code goToDefinition} — find where a symbol is defined</li>
 *   <li>{@code findReferences} — find all references to a symbol</li>
 *   <li>{@code hover} — get documentation / type info for a symbol</li>
 *   <li>{@code documentSymbol} — list all symbols in a file</li>
 *   <li>{@code workspaceSymbol} — search for symbols across the workspace</li>
 *   <li>{@code goToImplementation} — find implementations of an interface or abstract method</li>
 *   <li>{@code prepareCallHierarchy} — resolve the call-hierarchy item at a position</li>
 *   <li>{@code incomingCalls} — find all callers of a function</li>
 *   <li>{@code outgoingCalls} — find all callees of a function</li>
 * </ul>
 *
 * <h2>All position parameters are 1-based</h2>
 * <p>The {@code line} and {@code character} parameters accepted by every
 * operation use the same 1-based numbering displayed in editors (e.g.
 * line&nbsp;1, column&nbsp;1 is the very first character of a file).
 * The client transparently converts them to the 0-based coordinates
 * required by the LSP specification.
 *
 * @author Haifeng Li
 */
package smile.util.lsp;

