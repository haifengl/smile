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
package smile.util.lsp;

/**
 * A source location returned by LSP operations — a file URI together with
 * the start and end positions of the relevant text range.
 *
 * <p>All position values are 1-based (as shown in editors).
 *
 * @param uri            the document URI (e.g. {@code file:///home/user/Foo.java}).
 * @param startLine      the 1-based start line of the range.
 * @param startCharacter the 1-based start character offset of the range.
 * @param endLine        the 1-based end line of the range.
 * @param endCharacter   the 1-based end character offset of the range.
 *
 * @author Haifeng Li
 */
@SuppressWarnings("NullableProblems")
public record LspLocation(
        String uri,
        int startLine,
        int startCharacter,
        int endLine,
        int endCharacter) {

    /**
     * Builds an {@code LspLocation} from an LSP4J {@link org.eclipse.lsp4j.Location}.
     * Converts 0-based LSP coordinates to 1-based editor coordinates.
     *
     * @param loc the LSP4J location.
     * @return the equivalent editor-friendly location.
     */
    public static LspLocation fromProtocol(org.eclipse.lsp4j.Location loc) {
        var range = loc.getRange();
        return new LspLocation(
                loc.getUri(),
                range.getStart().getLine() + 1,
                range.getStart().getCharacter() + 1,
                range.getEnd().getLine() + 1,
                range.getEnd().getCharacter() + 1);
    }

    /**
     * Returns the start position of this location.
     *
     * @return the 1-based start {@link LspPosition}.
     */
    public LspPosition start() {
        return new LspPosition(startLine, startCharacter);
    }

    /**
     * Returns the end position of this location.
     *
     * @return the 1-based end {@link LspPosition}.
     */
    public LspPosition end() {
        return new LspPosition(endLine, endCharacter);
    }

    @Override
    public String toString() {
        return uri + ":" + startLine + ":" + startCharacter
                + "-" + endLine + ":" + endCharacter;
    }
}
