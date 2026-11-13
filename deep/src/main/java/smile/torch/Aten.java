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
package smile.torch;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

/**
 * Calls any registered ATen operator by its schema name through the dispatcher
 * ({@code smile_aten_call}), so models can use ops that have no dedicated binding:
 * <pre>{@code
 * Tensor c = Aten.one("aten::cat", new Tensor[]{a, b}, 1);
 * Tensor[] topk = Aten.call("aten::topk", x, 6, -1, true, true);
 * Tensor y = Aten.one("aten::masked_fill.Scalar", x, mask, Double.NEGATIVE_INFINITY);
 * }</pre>
 *
 * <p>Arguments follow the operator's schema order (see {@code torch.ops.aten.<name>.<overload>._schema}); trailing
 * arguments that are left out take their schema defaults. Accepted Java values: {@link Tensor}, {@code null}
 * (None), {@link Boolean}, {@link Integer}/{@link Long} (int or Scalar), {@link Double}/{@link Float}, {@link String},
 * {@code int[]}/{@code long[]} (int[]), {@code Tensor[]} (Tensor[]), {@link OptionalTensors} (Tensor?[]) and
 * {@link ScalarType} (a dtype argument). Tensor results, and tensor lists flattened, are returned.
 *
 * <p>Calls run without autograd. Failures throw {@link IllegalStateException} with the native message.
 *
 * @author Haifeng Li
 */
public final class Aten {
    private Aten() {
    }

    /**
     * A {@code Tensor?[]} argument, as taken by {@code aten::index}: {@code null} entries become None.
     *
     * @param tensors the entries.
     */
    public record OptionalTensors(Tensor... tensors) { }

    private static final class B {
        static final Linker L = Linker.nativeLinker();
        static final MethodHandle CREATE = h("smile_aten_args_create", FunctionDescriptor.of(ValueLayout.ADDRESS));
        static final MethodHandle FREE = h("smile_aten_args_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        static final MethodHandle TENSOR = h("smile_aten_args_push_tensor",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        static final MethodHandle NONE = h("smile_aten_args_push_none", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        static final MethodHandle INT = h("smile_aten_args_push_int",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        static final MethodHandle DOUBLE = h("smile_aten_args_push_double",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));
        static final MethodHandle BOOL = h("smile_aten_args_push_bool",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        static final MethodHandle STRING = h("smile_aten_args_push_string",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        static final MethodHandle INTS = h("smile_aten_args_push_ints",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        static final MethodHandle TENSORS = h("smile_aten_args_push_tensors",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        static final MethodHandle OPT_TENSORS = h("smile_aten_args_push_opt_tensors",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        static final MethodHandle CALL = h("smile_aten_call", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        private static MethodHandle h(String name, FunctionDescriptor d) {
            return smile_torch_h.SYMBOL_LOOKUP.find(name)
                    .map(s -> L.downcallHandle(s, d))
                    .orElse(null);
        }
    }

    /**
     * Returns true if the ATen dispatcher entry points are present in {@code smile_torch}.
     *
     * @return true if {@link #call(String, Object...)} is supported by the loaded native library.
     */
    public static boolean isAvailable() {
        try {
            return B.CALL != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Largest number of tensor outputs of one call. */
    private static final int MAX_OUTPUTS = 16;

    /**
     * Calls an operator.
     *
     * @param op   the operator, {@code "aten::name"} or {@code "aten::name.overload"}.
     * @param args the arguments in schema order.
     * @return the tensor outputs.
     */
    public static Tensor[] call(String op, Object... args) {
        if (B.CALL == null) {
            throw new UnsupportedOperationException("smile_aten_call is not available in this build of smile_torch");
        }
        int dot = op.indexOf('.');
        String name = dot < 0 ? op : op.substring(0, dot);
        String overload = dot < 0 ? "" : op.substring(dot + 1);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment a = (MemorySegment) B.CREATE.invokeExact();
            try {
                for (Object arg : args) {
                    push(arena, a, arg);
                }
                MemorySegment outs = arena.allocate(ValueLayout.ADDRESS, MAX_OUTPUTS);
                int n = (int) B.CALL.invokeExact(arena.allocateFrom(name), arena.allocateFrom(overload), a, outs, MAX_OUTPUTS);
                if (n < 0) {
                    throw new IllegalStateException(op + ": " + lastError());
                }
                Tensor[] result = new Tensor[n];
                for (int i = 0; i < n; i++) {
                    result[i] = new Tensor(outs.getAtIndex(ValueLayout.ADDRESS, i));
                }
                return result;
            } finally {
                B.FREE.invokeExact(a);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(op, t);
        }
    }

    /**
     * Calls an operator that returns one tensor.
     *
     * @param op   the operator.
     * @param args the arguments in schema order.
     * @return the first output.
     */
    public static Tensor one(String op, Object... args) {
        Tensor[] out = call(op, args);
        if (out.length == 0) {
            throw new IllegalStateException(op + " returned no tensor");
        }
        return out[0];
    }

    private static String lastError() {
        MemorySegment p = smile_torch_h.smile_last_error();
        return p.equals(MemorySegment.NULL) ? "unknown error" : p.reinterpret(4096).getString(0);
    }

    private static void push(Arena arena, MemorySegment a, Object arg) throws Throwable {
        switch (arg) {
            case null -> B.NONE.invokeExact(a);
            case Tensor t -> B.TENSOR.invokeExact(a, t.handle());
            case Boolean b -> B.BOOL.invokeExact(a, b ? 1 : 0);
            case Integer i -> B.INT.invokeExact(a, (long) i);
            case Long l -> B.INT.invokeExact(a, (long) l);
            case Double d -> B.DOUBLE.invokeExact(a, (double) d);
            case Float f -> B.DOUBLE.invokeExact(a, (double) f);
            case String s -> B.STRING.invokeExact(a, arena.allocateFrom(s));
            case ScalarType s -> B.INT.invokeExact(a, (long) s.code());
            case long[] v -> B.INTS.invokeExact(a, arena.allocateFrom(ValueLayout.JAVA_LONG, v), (long) v.length);
            case int[] v -> {
                long[] w = new long[v.length];
                for (int i = 0; i < v.length; i++) {
                    w[i] = v[i];
                }
                B.INTS.invokeExact(a, arena.allocateFrom(ValueLayout.JAVA_LONG, w), (long) w.length);
            }
            case Tensor[] v -> B.TENSORS.invokeExact(a, handles(arena, v), (long) v.length);
            case OptionalTensors o -> B.OPT_TENSORS.invokeExact(a, handles(arena, o.tensors()), (long) o.tensors().length);
            default -> throw new IllegalArgumentException("unsupported ATen argument " + arg.getClass());
        }
    }

    private static MemorySegment handles(Arena arena, Tensor[] v) {
        MemorySegment seg = arena.allocate(ValueLayout.ADDRESS, Math.max(1, v.length));
        for (int i = 0; i < v.length; i++) {
            seg.setAtIndex(ValueLayout.ADDRESS, i, v[i] == null ? MemorySegment.NULL : v[i].handle());
        }
        return seg;
    }
}
