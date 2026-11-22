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
package smile.torch;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import smile.deep.tensor.Tensor;

/**
 * Binding of the Inkling-2 decode kernels ({@code smile_inkling_ops.cu}): one dispatcher that takes raw device
 * pointers and launches on torch's current stream (so launches are captured by CUDA graph capture). The symbol is part
 * of {@code libsmile_torch}; for development it can also come from a stand-alone library named by the system property
 * {@code smile.inkling.lib} or the environment variable {@code SMILE_INKLING_LIB}.
 *
 * @author Haifeng Li
 */
public final class InklingNative {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena ARENA = Arena.ofAuto();
    private static final MethodHandle OP;
    private static final MethodHandle ERROR;
    private static final MethodHandle DATA_PTR;

    static {
        SymbolLookup lookup = smile_torch_h.SYMBOL_LOOKUP;
        String lib = System.getProperty("smile.inkling.lib", System.getenv("SMILE_INKLING_LIB"));
        Optional<MemorySegment> op = lookup.find("smile_inkling_op");
        if (op.isEmpty() && lib != null && Files.exists(Path.of(lib))) {
            lookup = SymbolLookup.libraryLookup(Path.of(lib), ARENA);
            op = lookup.find("smile_inkling_op");
        }
        OP = op.map(s -> LINKER.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT))).orElse(null);
        ERROR = lookup.find("smile_inkling_error").map(s -> LINKER.downcallHandle(s,
                FunctionDescriptor.of(ValueLayout.ADDRESS))).orElse(null);
        DATA_PTR = smile_torch_h.SYMBOL_LOOKUP.find("smile_tensor_data_ptr").map(s -> LINKER.downcallHandle(s,
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))).orElse(null);
    }

    private InklingNative() { }

    /** @return {@code true} when the kernels are available. */
    public static boolean isAvailable() {
        return OP != null && DATA_PTR != null;
    }

    /**
     * The device address of a tensor's first element.
     *
     * @param t a tensor.
     * @return the address.
     */
    public static long dataPtr(Tensor t) {
        try {
            return ((MemorySegment) DATA_PTR.invokeExact(t.handle())).address();
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Launches one kernel.
     *
     * @param op      the operation number (see {@code smile_inkling_ops.cu}).
     * @param pointers device pointers.
     * @param ints    integer parameters.
     * @param doubles floating-point parameters.
     */
    public static void op(int op, long[] pointers, long[] ints, double[] doubles) {
        // one reusable native buffer per thread (a launch makes no allocation); the arrays are small
        MemorySegment buf = BUFFER.get();
        final long iOff = 8L * Math.max(1, pointers.length);
        final long dOff = iOff + 8L * Math.max(1, ints.length);
        if (dOff + 8L * Math.max(1, doubles.length) > buf.byteSize()) {
            throw new IllegalArgumentException("too many kernel parameters");
        }
        for (int k = 0; k < pointers.length; k++) {
            buf.set(ValueLayout.JAVA_LONG, 8L * k, pointers[k]);
        }
        for (int k = 0; k < ints.length; k++) {
            buf.set(ValueLayout.JAVA_LONG, iOff + 8L * k, ints[k]);
        }
        for (int k = 0; k < doubles.length; k++) {
            buf.set(ValueLayout.JAVA_DOUBLE, dOff + 8L * k, doubles[k]);
        }
        try {
            int rc = (int) OP.invokeExact(op, buf, pointers.length, buf.asSlice(iOff), ints.length, buf.asSlice(dOff), doubles.length);
            if (rc != 0) {
                String msg = "";
                if (ERROR != null) {
                    MemorySegment s = (MemorySegment) ERROR.invokeExact();
                    msg = s.reinterpret(1024).getString(0);
                }
                throw new IllegalStateException("smile_inkling_op " + op + " failed (" + rc + "): " + msg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private static final ThreadLocal<MemorySegment> BUFFER = ThreadLocal.withInitial(
            () -> Arena.global().allocate(4096, 16));
}
