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
    /** The cache holds fp8 latent records (see {@link #fp8Latent()}): {@code width} is the record size in bytes. */
    final boolean fp8;
    private Tensor buf;
    private long len;
    private long ptr;       // cached device address of buf (0 = not read yet); the buffer moves only in ensure/close

    GlmRows(int width, ScalarType type) {
        this(width, type, false);
    }

    private GlmRows(int width, ScalarType type, boolean fp8) {
        this.width = width;
        this.type = type;
        this.fp8 = fp8;
    }

    /** Bytes of a latent record in the fp8 cache: 512 e4m3 values and four float32 scales (one per 128 dimensions). */
    static final int FP8_RECORD = 528;

    /** A latent cache of fp8 records (about half the bytes of 512 bfloat16 values per token). */
    static GlmRows fp8Latent() {
        return new GlmRows(FP8_RECORD, ScalarType.UInt8, true);
    }

    /** Width of a row in values (the record size is {@link #width} bytes for an fp8 cache). */
    int valueWidth() {
        return fp8 ? 512 : width;
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
            // allocated on the device: building the zeros on the host and copying them took seconds at 32k+ tokens (every layer of
            // every rank doubles at the same position)
            Tensor next = keep(Tensor.zeros(new Tensor.Options().dtype(type).device(device()), cap, width));
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
        if (fp8 && rows.dtype() != ScalarType.UInt8) {
            // new latent rows: quantize straight into the cache (raw records, e.g. from a snapshot, are copied below)
            smile.torch.Native.glmLatentQuantize(o("aten::contiguous", f32(rows)), narrow(buf, 0, len, m));
        } else {
            smile.torch.Aten.call("aten::copy_", narrow(buf, 0, len, m), rows, false);
        }
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
