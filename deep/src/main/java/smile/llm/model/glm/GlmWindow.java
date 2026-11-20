/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.glm;

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static smile.llm.model.glm.Gx.*;

/**
 * The fixed-address inputs and saved intermediates of one verify-window graph: {@code b} request slots of
 * {@code rows} consecutive tokens each (request-major rows). Shared by the graph body and the commit that keeps the
 * accepted rows, which needs what the window forward saved (per KDA layer the state after every row and the raw rows,
 * per MLA layer the indexer keys and gates).
 *
 * @author Haifeng Li
 */
final class GlmWindow {
    final int b;
    final int rows;
    final int n;
    final long maxPools;
    final Tensor ids;
    final Tensor slots;
    final Tensor lensReq;
    final Tensor lensRow;
    /** Latent, pooled-key and ring addresses per request and MLA layer, int64 {@code [mlaLayers * 3, b]}. */
    final Tensor req;
    /** Latent and pooled-key addresses per row and MLA layer, int64 {@code [mlaLayers * 2, n]}. */
    final Tensor rowPtrs;
    final Tensor accept;
    final Tensor[] recSave;
    final Tensor[] rawSave;
    final Tensor[] keySave;
    final Tensor[] gateSave;
    private final int[] kdaOf;
    private final int[] mlaOf;

    GlmWindow(GlmBlock[] blocks, int mlaLayers, int b, int rows, long maxPools) {
        this.b = b;
        this.rows = rows;
        this.n = b * rows;
        this.maxPools = maxPools;
        this.ids = keep(Tensor.of(new long[n], n).to(device()));
        this.slots = keep(Tensor.of(new int[b], b).to(device()));
        this.lensReq = keep(Tensor.of(new int[b], b).to(device()));
        this.lensRow = keep(Tensor.of(new int[n], n).to(device()));
        this.req = keep(Tensor.of(new long[mlaLayers * 3 * b], (long) mlaLayers * 3, b).to(device()));
        this.rowPtrs = keep(Tensor.of(new long[mlaLayers * 2 * n], (long) mlaLayers * 2, n).to(device()));
        this.accept = keep(Tensor.of(new int[b], b).to(device()));
        this.kdaOf = new int[blocks.length];
        this.mlaOf = new int[blocks.length];
        int kdas = 0;
        int mlas = 0;
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i].isMla()) {
                mlaOf[i] = mlas++;
                kdaOf[i] = -1;
            } else {
                kdaOf[i] = kdas++;
                mlaOf[i] = -1;
            }
        }
        recSave = new Tensor[kdas];
        rawSave = new Tensor[kdas];
        keySave = new Tensor[mlas];
        gateSave = new Tensor[mlas];
        for (int i = 0; i < blocks.length; i++) {
            if (kdaOf[i] >= 0) {
                GlmKda k = blocks[i].kda();
                recSave[kdaOf[i]] = keep(zeros(ScalarType.Float, b, rows, k.heads(), 128, 128));
                rawSave[kdaOf[i]] = keep(zeros(ScalarType.Float, b, rows, 3L * k.width()));
            } else {
                keySave[mlaOf[i]] = keep(zeros(ScalarType.Float, n, 128));
                gateSave[mlaOf[i]] = keep(zeros(ScalarType.Float, n, 128));
            }
        }
    }

    int kdaIndex(int layer) {
        return kdaOf[layer];
    }

    int mlaIndex(int layer) {
        return mlaOf[layer];
    }

    void close() {
        for (Tensor t : new Tensor[]{ids, slots, lensReq, lensRow, req, rowPtrs, accept}) {
            t.close();
        }
        for (Tensor[] ts : new Tensor[][]{recSave, rawSave, keySave, gateSave}) {
            for (Tensor t : ts) {
                t.close();
            }
        }
    }
}
