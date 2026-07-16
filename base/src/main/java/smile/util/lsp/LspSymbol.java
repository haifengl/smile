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
 * Represents a named symbol (class, method, field, variable, …) inside a
 * document or workspace, as returned by the {@code documentSymbol} and
 * {@code workspaceSymbol} operations.
 *
 * <p>All position values are 1-based (as shown in editors).
 *
 * @param name            the symbol name.
 * @param kind            the LSP symbol kind integer (1 = File, 2 = Module,
 *                        3 = Namespace, 5 = Class, 6 = Method, 7 = Property,
 *                        8 = Field, 12 = Function, 13 = Variable, etc.).
 * @param containerName   the name of the containing symbol, or {@code null}.
 * @param uri             the document URI that contains this symbol.
 * @param startLine       the 1-based start line.
 * @param startCharacter  the 1-based start character offset.
 * @param endLine         the 1-based end line.
 * @param endCharacter    the 1-based end character offset.
 *
 * @author Haifeng Li
 */
public record LspSymbol(
        String name,
        int kind,
        String containerName,
        String uri,
        int startLine,
        int startCharacter,
        int endLine,
        int endCharacter) {

    /**
     * Creates an {@code LspSymbol} from an LSP4J
     * {@link org.eclipse.lsp4j.SymbolInformation}.
     *
     * @param si the LSP4J symbol information.
     * @return the equivalent editor-friendly symbol.
     */
    public static LspSymbol fromSymbolInformation(org.eclipse.lsp4j.SymbolInformation si) {
        var range = si.getLocation().getRange();
        return new LspSymbol(
                si.getName(),
                si.getKind().getValue(),
                si.getContainerName(),
                si.getLocation().getUri(),
                range.getStart().getLine() + 1,
                range.getStart().getCharacter() + 1,
                range.getEnd().getLine() + 1,
                range.getEnd().getCharacter() + 1);
    }

    /**
     * Creates an {@code LspSymbol} from an LSP4J
     * {@link org.eclipse.lsp4j.DocumentSymbol} and its parent document URI.
     *
     * @param ds  the LSP4J document symbol.
     * @param uri the document URI that contains this symbol.
     * @return the equivalent editor-friendly symbol.
     */
    public static LspSymbol fromDocumentSymbol(org.eclipse.lsp4j.DocumentSymbol ds, String uri) {
        var range = ds.getRange();
        return new LspSymbol(
                ds.getName(),
                ds.getKind().getValue(),
                null,
                uri,
                range.getStart().getLine() + 1,
                range.getStart().getCharacter() + 1,
                range.getEnd().getLine() + 1,
                range.getEnd().getCharacter() + 1);
    }

    /**
     * Returns the location of this symbol.
     *
     * @return the {@link LspLocation}.
     */
    public LspLocation location() {
        return new LspLocation(uri, startLine, startCharacter, endLine, endCharacter);
    }

    /**
     * Returns a human-readable name for the given LSP symbol kind integer.
     *
     * @param kind the LSP symbol kind value.
     * @return a short descriptive string.
     */
    public static String kindName(int kind) {
        return switch (kind) {
            case  1 -> "File";
            case  2 -> "Module";
            case  3 -> "Namespace";
            case  4 -> "Package";
            case  5 -> "Class";
            case  6 -> "Method";
            case  7 -> "Property";
            case  8 -> "Field";
            case  9 -> "Constructor";
            case 10 -> "Enum";
            case 11 -> "Interface";
            case 12 -> "Function";
            case 13 -> "Variable";
            case 14 -> "Constant";
            case 15 -> "String";
            case 16 -> "Number";
            case 17 -> "Boolean";
            case 18 -> "Array";
            case 19 -> "Object";
            case 20 -> "Key";
            case 21 -> "Null";
            case 22 -> "EnumMember";
            case 23 -> "Struct";
            case 24 -> "Event";
            case 25 -> "Operator";
            case 26 -> "TypeParameter";
            default -> "Unknown(" + kind + ")";
        };
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public String toString() {
        return kindName(kind) + " " + name
                + (containerName != null ? " in " + containerName : "")
                + " @ " + uri + ":" + startLine + ":" + startCharacter;
    }
}

