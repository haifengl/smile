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
 * Represents a single item in a call hierarchy — a named function or method
 * that can serve as the focus of
 * {@link LanguageService#incomingCalls(smile.util.lsp.CallHierarchyItem)} or
 * {@link LanguageService#outgoingCalls(smile.util.lsp.CallHierarchyItem)} queries.
 *
 * <p>All position values are 1-based (as shown in editors).
 *
 * @param name           the symbol name.
 * @param kind           the LSP symbol kind integer.
 * @param detail         optional detail string (e.g. the signature or container).
 * @param uri            the document URI that defines this symbol.
 * @param startLine      the 1-based start line of the symbol's name range.
 * @param startCharacter the 1-based start character of the symbol's name range.
 * @param endLine        the 1-based end line of the symbol's name range.
 * @param endCharacter   the 1-based end character of the symbol's name range.
 *
 * @author Haifeng Li
 */
public record CallHierarchyItem(
        String name,
        int kind,
        String detail,
        String uri,
        int startLine,
        int startCharacter,
        int endLine,
        int endCharacter) {

    /**
     * Creates a {@code CallHierarchyItem} from an LSP4J
     * {@link org.eclipse.lsp4j.CallHierarchyItem}.
     *
     * @param item the LSP4J call hierarchy item.
     * @return the equivalent editor-friendly item.
     */
    public static CallHierarchyItem fromProtocol(org.eclipse.lsp4j.CallHierarchyItem item) {
        var range = item.getRange();
        return new CallHierarchyItem(
                item.getName(),
                item.getKind().getValue(),
                item.getDetail(),
                item.getUri(),
                range.getStart().getLine() + 1,
                range.getStart().getCharacter() + 1,
                range.getEnd().getLine() + 1,
                range.getEnd().getCharacter() + 1);
    }

    /**
     * Converts this item back to an LSP4J
     * {@link org.eclipse.lsp4j.CallHierarchyItem} for use in follow-up
     * {@code callHierarchy/incomingCalls} or {@code callHierarchy/outgoingCalls}
     * requests.
     *
     * @return the LSP4J call hierarchy item with 0-based coordinates.
     */
    public org.eclipse.lsp4j.CallHierarchyItem toProtocol() {
        var start = new org.eclipse.lsp4j.Position(startLine - 1, startCharacter - 1);
        var end   = new org.eclipse.lsp4j.Position(endLine   - 1, endCharacter   - 1);
        var range = new org.eclipse.lsp4j.Range(start, end);
        var item  = new org.eclipse.lsp4j.CallHierarchyItem();
        item.setName(name);
        item.setKind(org.eclipse.lsp4j.SymbolKind.forValue(kind));
        item.setDetail(detail);
        item.setUri(uri);
        item.setRange(range);
        item.setSelectionRange(range);
        return item;
    }

    /**
     * Returns the location of this item's name range.
     *
     * @return the {@link LspLocation}.
     */
    public LspLocation location() {
        return new LspLocation(uri, startLine, startCharacter, endLine, endCharacter);
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public String toString() {
        return LspSymbol.kindName(kind) + " " + name
                + (detail != null && !detail.isBlank() ? " (" + detail + ")" : "")
                + " @ " + uri + ":" + startLine + ":" + startCharacter;
    }
}

