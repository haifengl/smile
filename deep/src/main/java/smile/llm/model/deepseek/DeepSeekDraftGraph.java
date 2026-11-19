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
package smile.llm.model.deepseek;

import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

/**
 * One DSpark draft pass over a fixed number of sequence slots as a CUDA graph (per rank): the token ids, positions, hidden
 * states and ring pointers are static tensors refilled before each replay, so a round's draft costs one launch instead of a
 * few hundred. Slots beyond the real sequences point at a padding session.
 */
final class DeepSeekDraftGraph {
    private final DeepSeekModel model;
    private final DeepSeekDraft draft;
    private final int slots;
    private final DeepSeekDraft.Inputs in;
    private MemorySegment graph;
    private Tensor tokens;
    private Tensor conf;
    private DeepSeekModel.Session[] composition;

    private DeepSeekDraftGraph(DeepSeekModel model, DeepSeekDraft draft, int slots, int tapWidth) {
        this.model = model;
        this.draft = draft;
        this.slots = slots;
        this.in = new DeepSeekDraft.Inputs(draft, slots, tapWidth, true);
    }

    /** Warms the pass up eagerly, then captures it; run on the rank's thread outside any tensor scope, on all ranks together. */
    static DeepSeekDraftGraph capture(DeepSeekModel model, DeepSeekModel.Session pad, int slots, int tapWidth, int deviceIndex) {
        DeepSeekDraft draft = model.draft();
        DeepSeekDraftGraph g = new DeepSeekDraftGraph(model, draft, slots, tapWidth);
        model.ensureDraftState(pad);
        draft.fill(g.in, new int[0], new int[0], new Tensor[0], new DeepSeekAttention.State[0][], pad.draft);
        try (var guard = Tensor.noGradGuard()) {
            Dsv.scoped(() -> draft.core(model, g.in)[0]).close();
            g.graph = Native.cudaGraphCreate();
            Native.cudaGraphCaptureBegin(g.graph, deviceIndex);
            try {
                Dsv.scoped(() -> {
                    Tensor[] r = draft.core(model, g.in);
                    g.tokens = r[0];
                    g.conf = r[1];
                    g.tokens.detachFromScopes();
                    g.conf.detachFromScopes();
                    return r[0];
                });
            } finally {
                Native.cudaGraphCaptureEnd(g.graph);
            }
        }
        return g;
    }

    /** Drafts for the sessions {@code rows} (at most {@code slots}); returns their tokens and confidences. */
    DeepSeekDraft.Result[] replay(int[] lastIds, int[] startPos, DeepSeekModel.Session[] rows, DeepSeekModel.Session pad) {
        int n = lastIds.length;
        DeepSeekAttention.State[][] states = new DeepSeekAttention.State[n][];
        Tensor[] taps = new Tensor[n];
        for (int i = 0; i < n; i++) {
            states[i] = rows[i].draft;
            taps[i] = rows[i].lastTap;
        }
        // the pointer tables only change with the composition, but they are cheap next to the pass: refill every round
        draft.fill(in, lastIds, startPos, taps, states, pad.draft);
        Native.cudaGraphReplay(graph);
        return draft.read(tokens, conf, n);
    }

    void close() {
        if (graph != null) {
            Native.cudaGraphDestroy(graph);
            graph = null;
        }
    }
}
