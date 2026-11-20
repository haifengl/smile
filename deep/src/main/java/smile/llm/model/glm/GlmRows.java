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
 * A growing {@code [len, width]} row cache with amortized doubling, kept outside every tensor scope.
 *
 * @author Haifeng Li
 */
final class GlmRows {
    private static final long MIN_CAPACITY = 1024;

    final int width;
    final ScalarType type;
    private Tensor buf;
    private long len;
    private long ptr;       // cached device address of buf (0 = not read yet); the buffer moves only in ensure/close

    GlmRows(int width, ScalarType type) {
        this.width = width;
        this.type = type;
    }

    long length() {
        return len;
    }

    /**
     * Makes room for {@code rows} rows in total (the buffer may move: pointers taken before are stale afterwards).
     *
     * @param rows the number of rows the cache must hold.
     */
    void ensure(long rows) {
        if (buf == null || rows > buf.size(0)) {
            long cap = Math.max(MIN_CAPACITY, buf == null ? 0 : buf.size(0));
            while (cap < rows) {
                cap *= 2;
            }
            Tensor next = keep(o("aten::zeros", new long[]{cap, width}, type).to(device()));
            if (buf != null) {
                if (len > 0) {
                    smile.torch.Aten.call("aten::copy_", narrow(next, 0, 0, len), narrow(buf, 0, 0, len), false);
                }
                buf.close();
            }
            buf = next;
            ptr = 0;
        }
    }

    /** Appends {@code [m, width]} rows (converted to the cache dtype). */
    void append(Tensor rows) {
        long m = rows.size(0);
        if (m == 0) {
            return;
        }
        ensure(len + m);
        smile.torch.Aten.call("aten::copy_", narrow(buf, 0, len, m), rows, false);
        len += m;
    }

    /** Sets the row count after a kernel wrote rows straight into the buffer (see {@link #ensure}). */
    void setLength(long rows) {
        len = rows;
    }

    /** The device address of the buffer ({@link #ensure} must have been called). */
    long pointer() {
        if (ptr == 0) {
            ptr = smile.torch.Native.dsDataPtrs(new Tensor[]{buf})[0];
        }
        return ptr;
    }

    /** The valid rows {@code [len, width]} (a view of the cache). */
    Tensor view() {
        return narrow(buf, 0, 0, len);
    }

    void close() {
        if (buf != null) {
            buf.close();
            buf = null;
        }
        ptr = 0;
        len = 0;
    }
}
