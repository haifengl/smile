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
 * Loaded audios for multimodal GenAI models.
 *
 * @author Haifeng Li
 */
public final class Audios implements AutoCloseable {
    /** Native {@code OgaAudios*} handle. */
    private MemorySegment handle;

    private Audios(MemorySegment handle) {
        this.handle = handle;
    }

    /**
     * Loads audios from file paths.
     *
     * @param paths one or more audio file paths.
     * @return audios owned by the caller.
     */
    public static Audios load(String... paths) {
        if (paths == null || paths.length == 0) {
            throw new IllegalArgumentException("paths must not be empty");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array = GenAIRuntime.createHandle(arena, ort_genai_c_h::OgaCreateStringArray);
            try {
                for (String path : paths) {
                    GenAIRuntime.checkResult(ort_genai_c_h.OgaStringArrayAddString(
                            array, arena.allocateFrom(path)));
                }
                MemorySegment audios = GenAIRuntime.createHandle(arena,
                        out -> ort_genai_c_h.OgaLoadAudios(array, out));
                return new Audios(audios);
            } finally {
                ort_genai_c_h.OgaDestroyStringArray(array);
            }
        }
    }

    /** @return the native handle (package use). */
    MemorySegment handle() {
        GenAIRuntime.requireOpen(handle, "Audios");
        return handle;
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyAudios(handle);
            handle = MemorySegment.NULL;
        }
    }
}
