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
 * Greedy speculative accept/reject helpers (Leviathan-style prefix match).
 *
 * <p>Given draft tokens {@code d[0..N)} and target samples {@code t[0..N]}
 * (length {@code N+1}, where {@code t[i]} is the target sample at draft depth
 * {@code i} and {@code t[N]} is the bonus after a full accept), accept the
 * longest prefix where {@code t[i] == d[i]} and always emit the bonus token
 * {@code t[r]} for {@code r} accepted drafts.
 *
 * @author Haifeng Li
 */
public final class SpeculativeDecoding {
    private SpeculativeDecoding() {}

    /**
     * Result of one speculative verify round.
     *
     * @param numDraftAccepted number of draft tokens accepted ({@code 0..N}).
     * @param acceptedTokens   accepted drafts plus the bonus token (length {@code r+1}).
     */
    public record AcceptResult(int numDraftAccepted, int[] acceptedTokens) {
        /** @return total tokens to emit this round ({@code numDraftAccepted + 1}). */
        public int numTokens() {
            return acceptedTokens.length;
        }

        /** @return the bonus (target) token always kept at the end. */
        public int bonusToken() {
            return acceptedTokens[acceptedTokens.length - 1];
        }
    }

    /**
     * Greedy accept: exact match between draft and target samples.
     *
     * @param drafts         draft token ids {@code [N]} ({@code N >= 1}).
     * @param targetSamples  target samples {@code [N+1]} (greedy argmax per verify position).
     * @return accept result with {@code r+1} tokens.
     */
    public static AcceptResult acceptGreedy(int[] drafts, int[] targetSamples) {
        if (drafts == null || drafts.length == 0) {
            throw new IllegalArgumentException("drafts must be non-empty");
        }
        if (targetSamples == null || targetSamples.length != drafts.length + 1) {
            throw new IllegalArgumentException(
                    "targetSamples length must be drafts.length + 1");
        }
        int r = 0;
        while (r < drafts.length && targetSamples[r] == drafts[r]) {
            r++;
        }
        int[] accepted = new int[r + 1];
        System.arraycopy(drafts, 0, accepted, 0, r);
        accepted[r] = targetSamples[r];
        return new AcceptResult(r, accepted);
    }
}
