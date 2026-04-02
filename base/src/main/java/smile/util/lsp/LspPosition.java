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
 * A 1-based position inside a text document, as displayed in editors.
 *
 * <p>The LSP specification uses 0-based lines and characters internally.
 * This record stores the editor-friendly 1-based values and provides
 * {@link #toLspLine()} / {@link #toLspCharacter()} helpers for conversion.
 *
 * @param line      the 1-based line number (first line = 1).
 * @param character the 1-based character offset (first column = 1).
 *
 * @author Haifeng Li
 */
@SuppressWarnings("NullableProblems")
public record LspPosition(int line, int character) {

    /**
     * Validates that both coordinates are positive (≥ 1).
     */
    public LspPosition {
        if (line < 1) throw new IllegalArgumentException("line must be ≥ 1, got: " + line);
        if (character < 1) throw new IllegalArgumentException("character must be ≥ 1, got: " + character);
    }

    /**
     * Returns the 0-based line index required by the LSP specification.
     *
     * @return {@code line - 1}.
     */
    public int toLspLine() {
        return line - 1;
    }

    /**
     * Returns the 0-based character offset required by the LSP specification.
     *
     * @return {@code character - 1}.
     */
    public int toLspCharacter() {
        return character - 1;
    }

    /**
     * Converts this position to the LSP4J {@link org.eclipse.lsp4j.Position}.
     *
     * @return a new LSP4J {@code Position} with 0-based coordinates.
     */
    public org.eclipse.lsp4j.Position toProtocol() {
        return new org.eclipse.lsp4j.Position(toLspLine(), toLspCharacter());
    }

    /**
     * Creates an {@code LspPosition} from an LSP4J {@link org.eclipse.lsp4j.Position}
     * (0-based) by adding 1 to both coordinates.
     *
     * @param pos the 0-based LSP4J position.
     * @return the equivalent 1-based position.
     */
    public static LspPosition fromProtocol(org.eclipse.lsp4j.Position pos) {
        return new LspPosition(pos.getLine() + 1, pos.getCharacter() + 1);
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public String toString() {
        return "LspPosition{line=" + line + ", character=" + character + "}";
    }
}

