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
package smile.llm.model.deepseek;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

/**
 * Reads tensors of a DeepSeek-V4.1 checkpoint (sharded safetensors) one at a time, so a slice of the model can be
 * loaded without touching the other shards.
 *
 * <p>Dtypes: BF16, F32, I8 (packed fp4 experts) and F8_E4M3 load as such; {@code F8_E8M0} scales load as raw
 * {@code uint8} exponent bytes (see {@link DeepSeekOps#e8m0ToFloat}). Tensors that live in a huge shard but were
 * range-fetched into {@code engram_small.safetensors} (the engram projection, not its table) are found there.
 *
 * @author Haifeng Li
 */
public final class DeepSeekCheckpoint implements AutoCloseable {
    private record Entry(String dtype, long[] shape, long begin, long end) { }
    private record Shard(FileChannel channel, long dataStart, Map<String, Entry> entries) { }

    private final Path dir;
    private final Map<String, String> index = new HashMap<>();
    private final Map<String, Shard> shards = new HashMap<>();

    /**
     * Opens a checkpoint directory containing {@code model.safetensors.index.json}.
     *
     * @param dir the checkpoint directory.
     * @throws IOException if the index cannot be read.
     */
    public DeepSeekCheckpoint(Path dir) throws IOException {
        this.dir = dir;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode map = mapper.readTree(dir.resolve("model.safetensors.index.json").toFile()).get("weight_map");
        for (var e : map.properties()) {
            index.put(e.getKey(), e.getValue().asString());
        }
        Path small = dir.resolve("engram_small.safetensors");
        if (Files.exists(small)) {
            for (String name : shard("engram_small.safetensors").entries().keySet()) {
                index.put(name, "engram_small.safetensors");
            }
        }
    }

    /**
     * Whether a tensor is available locally.
     *
     * @param name the tensor name.
     * @return {@code true} if the index lists it and its shard file exists.
     */
    public boolean has(String name) {
        String file = index.get(name);
        return file != null && Files.exists(dir.resolve(file));
    }

    private synchronized Shard shard(String file) throws IOException {
        Shard s = shards.get(file);
        if (s == null) {
            FileChannel ch = FileChannel.open(dir.resolve(file), StandardOpenOption.READ);
            ByteBuffer len = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            ch.read(len, 0);
            len.flip();
            long n = len.getLong();
            ByteBuffer hdr = ByteBuffer.allocate((int) n);
            ch.read(hdr, 8);
            JsonNode root = new ObjectMapper().readTree(new String(hdr.array(), StandardCharsets.UTF_8));
            Map<String, Entry> entries = new HashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = root.properties().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (e.getKey().equals("__metadata__")) {
                    continue;
                }
                JsonNode v = e.getValue();
                long[] shape = new long[v.get("shape").size()];
                for (int i = 0; i < shape.length; i++) {
                    shape[i] = v.get("shape").get(i).asLong();
                }
                entries.put(e.getKey(), new Entry(v.get("dtype").asString(), shape,
                        v.get("data_offsets").get(0).asLong(), v.get("data_offsets").get(1).asLong()));
            }
            s = new Shard(ch, 8 + n, entries);
            shards.put(file, s);
        }
        return s;
    }

    /**
     * Loads a tensor to CPU.
     *
     * @param name the tensor name, e.g. {@code layers.0.attn.wq_a.weight}.
     * @return the tensor (see the class comment for dtypes).
     */
    public Tensor get(String name) {
        try {
            String file = index.get(name);
            if (file == null) {
                throw new IllegalArgumentException("no such tensor: " + name);
            }
            Shard s = shard(file);
            Entry e = s.entries().get(name);
            ScalarType dtype = switch (e.dtype()) {
                case "BF16" -> ScalarType.BFloat16;
                case "F32" -> ScalarType.Float;
                case "F16" -> ScalarType.Half;
                case "I8" -> ScalarType.Int8;
                case "U8", "F8_E8M0" -> ScalarType.UInt8;
                case "F8_E4M3" -> ScalarType.Float8e4m3fn;
                case "I64" -> ScalarType.Int64;
                case "I32" -> ScalarType.Int32;
                default -> throw new IllegalArgumentException("unsupported dtype " + e.dtype() + " of " + name);
            };
            Tensor t = Tensor.empty(new Tensor.Options().dtype(dtype).device(Device.CPU()), e.shape());
            long length = e.end() - e.begin();
            if (Native.nbytes(t) != length) {
                throw new IOException(name + ": payload " + length + " bytes, tensor " + Native.nbytes(t));
            }
            MemorySegment dst = MemorySegment.ofAddress(Native.dataPtr(t).address()).reinterpret(length);
            ByteBuffer buf = dst.asByteBuffer();
            long pos = s.dataStart() + e.begin();
            while (buf.hasRemaining()) {
                int n = s.channel().read(buf, pos);
                if (n < 0) {
                    throw new IOException("unexpected end of file reading " + name);
                }
                pos += n;
            }
            return t;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Shape of a tensor, from the shard header (nothing is read).
     *
     * @param name the tensor name.
     * @return the shape.
     */
    public long[] shape(String name) {
        return entry(name).shape();
    }

    /**
     * Whether a tensor is stored as int8 (packed fp4 pairs), from the shard header.
     *
     * @param name the tensor name.
     * @return {@code true} for I8.
     */
    public boolean isInt8(String name) {
        return entry(name).dtype().equals("I8");
    }

    private Entry entry(String name) {
        try {
            String file = index.get(name);
            if (file == null) {
                throw new IllegalArgumentException("no such tensor: " + name);
            }
            return shard(file).entries().get(name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Reads rows of a 2-D tensor straight from its shard file (for the huge engram tables).
     *
     * @param name the tensor name.
     * @param rows the row ids.
     * @return a CPU tensor {@code [rows.length, width]} of the tensor's dtype.
     */
    public Tensor readRows(String name, long[] rows) {
        try {
            String file = index.get(name);
            if (file == null) {
                throw new IllegalArgumentException("no such tensor: " + name);
            }
            Shard s = shard(file);
            Entry e = s.entries().get(name);
            ScalarType dtype = switch (e.dtype()) {
                case "F8_E4M3" -> ScalarType.Float8e4m3fn;
                case "U8", "F8_E8M0" -> ScalarType.UInt8;
                default -> throw new IllegalArgumentException("readRows: unsupported dtype " + e.dtype() + " of " + name);
            };
            long width = e.shape()[1];   // one byte per element for the dtypes above
            Tensor t = Tensor.empty(new Tensor.Options().dtype(dtype).device(Device.CPU()), rows.length, width);
            ByteBuffer buf = MemorySegment.ofAddress(Native.dataPtr(t).address()).reinterpret(rows.length * width).asByteBuffer();
            for (long r : rows) {
                if (r < 0 || r >= e.shape()[0]) {
                    throw new IllegalArgumentException(name + ": row " + r + " out of range");
                }
                buf.limit(buf.position() + (int) width);
                long pos = s.dataStart() + e.begin() + r * width;
                while (buf.hasRemaining()) {
                    int n = s.channel().read(buf, pos);
                    if (n < 0) {
                        throw new IOException("unexpected end of file reading " + name);
                    }
                    pos += n;
                }
            }
            return t;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Loads a tensor to the model's device ({@link Dsv#device()}).
     *
     * @param name the tensor name.
     * @return the tensor.
     */
    public Tensor dev(String name) {
        return Dsv.dev(get(name));
    }

    /**
     * Loads a tensor reinterpreted without conversion ({@code view(dtype)}), e.g. int8 bytes as fp4 pairs.
     *
     * @param name  the tensor name.
     * @param dtype the dtype to view the bytes as.
     * @return the viewed tensor.
     */
    public Tensor getAs(String name, ScalarType dtype) {
        return Aten.one("aten::view.dtype", get(name), dtype);
    }

    @Override
    public void close() throws IOException {
        for (Shard s : shards.values()) {
            s.channel().close();
        }
        shards.clear();
    }
}
