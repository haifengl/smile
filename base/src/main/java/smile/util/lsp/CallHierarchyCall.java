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

import java.util.List;

/**
 * Represents one edge in a call hierarchy graph — either an incoming call
 * (a caller that invokes the focus item) or an outgoing call (a callee
 * invoked by the focus item).
 *
 * <p>Each edge has a {@link #item()} describing the other endpoint and a
 * list of {@link #ranges()} indicating the exact text ranges inside the
 * focus document where the call occurs.
 *
 * <p>All position values are 1-based (as shown in editors).
 *
 * @param item   the caller (for incoming) or callee (for outgoing).
 * @param ranges the call-site ranges within the focus document; each entry
 *               is a four-element array {@code [startLine, startChar,
 *               endLine, endChar]} using 1-based coordinates.
 *
 * @author Haifeng Li
 */
public record CallHierarchyCall(CallHierarchyItem item, List<int[]> ranges) {

    /**
     * Creates a {@code CallHierarchyCall} from an LSP4J
     * {@link org.eclipse.lsp4j.CallHierarchyIncomingCall}.
     *
     * @param call the LSP4J incoming call.
     * @return the equivalent editor-friendly call.
     */
    public static CallHierarchyCall fromIncoming(
            org.eclipse.lsp4j.CallHierarchyIncomingCall call) {
        return new CallHierarchyCall(
                CallHierarchyItem.fromProtocol(call.getFrom()),
                convertRanges(call.getFromRanges()));
    }

    /**
     * Creates a {@code CallHierarchyCall} from an LSP4J
     * {@link org.eclipse.lsp4j.CallHierarchyOutgoingCall}.
     *
     * @param call the LSP4J outgoing call.
     * @return the equivalent editor-friendly call.
     */
    public static CallHierarchyCall fromOutgoing(
            org.eclipse.lsp4j.CallHierarchyOutgoingCall call) {
        return new CallHierarchyCall(
                CallHierarchyItem.fromProtocol(call.getTo()),
                convertRanges(call.getFromRanges()));
    }

    /** Converts a list of LSP4J ranges to 1-based int[4] arrays. */
    private static List<int[]> convertRanges(List<org.eclipse.lsp4j.Range> ranges) {
        if (ranges == null) return List.of();
        return ranges.stream()
                .map(r -> new int[]{
                        r.getStart().getLine() + 1,
                        r.getStart().getCharacter() + 1,
                        r.getEnd().getLine() + 1,
                        r.getEnd().getCharacter() + 1})
                .toList();
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public String toString() {
        return item + " [" + ranges.size() + " call-site(s)]";
    }
}

