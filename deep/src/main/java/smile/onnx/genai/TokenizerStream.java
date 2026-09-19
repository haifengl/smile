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
package smile.onnx.genai;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.onnx.genai.foreign.ort_genai_c_h;

/**
 * Incremental token → text decoder used while streaming generation.
 *
 * @author Haifeng Li
 */
public final class TokenizerStream implements AutoCloseable {
    /** Native {@code OgaTokenizerStream*} handle. */
    private MemorySegment handle;

    TokenizerStream(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Decodes one newly generated token into a text chunk (may be empty while
     * multi-byte / multi-token pieces accumulate).
     *
     * <p>The native buffer is owned by the stream until the next
     * {@code decode} call or {@link #close()}; it must not be freed with
     * {@code OgaDestroyString}.
     *
     * @param token token id.
     * @return decoded chunk; never {@code null}.
     */
    public String decode(int token) {
        GenAIRuntime.requireOpen(handle, "TokenizerStream");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ort_genai_c_h.C_POINTER);
            GenAIRuntime.checkResult(ort_genai_c_h.OgaTokenizerStreamDecode(handle, token, out));
            // Borrowed pointer — do not OgaDestroyString (see ort_genai_c.h).
            return GenAIRuntime.readString(out.get(ort_genai_c_h.C_POINTER, 0));
        }
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyTokenizerStream(handle);
            handle = MemorySegment.NULL;
        }
    }
}
