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
package smile.onnx;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import smile.onnx.foreign.OrtApi;
import smile.onnx.foreign.onnxruntime_c_api_h;
import smile.util.OS;

/**
 * Represents an ONNX Runtime inference session for a single model.
 *
 * <p>An {@code InferenceSession} loads an ONNX model, compiles it
 * (with optional graph optimisations), and exposes the {@link #run} method
 * for executing inference.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * try (var session = InferenceSession.create("resnet50.onnx")) {
 *     // Inspect the model
 *     session.inputNames().forEach(System.out::println);
 *
 *     // Build inputs
 *     float[] pixels = ...; // 1 × 3 × 224 × 224
 *     Map<String, OrtValue> inputs = Map.of(
 *         "input", OrtValue.fromFloatArray(pixels, new long[]{1, 3, 224, 224})
 *     );
 *
 *     // Run inference and read the output
 *     try (var inputs = ...) {
 *         OrtValue[] outputs = session.run(inputs);
 *         float[] scores = outputs[0].toFloatArray();
 *         // close outputs when done
 *         for (var v : outputs) v.close();
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>A single {@code InferenceSession} may be used concurrently from multiple
 * threads. Each call to {@link #run} is independent.
 *
 * @author Haifeng Li
 */
public class InferenceSession implements AutoCloseable {
    /** The ORT API pointer. */
    private final MemorySegment api;
    /** The ORT environment pointer (shared). */
    private final MemorySegment env;
    /** The native OrtSession pointer. */
    private final MemorySegment session;
    /** Whether this instance owns the OrtEnv (and must release it). */
    private final boolean envOwner;

    /** Cached input node information. */
    private final List<NodeInfo> inputInfos;
    /** Cached output node information. */
    private final List<NodeInfo> outputInfos;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Package-private factory used by {@link Environment} to create a session
     * that does <em>not</em> own its OrtEnv.
     */
    static InferenceSession wrap(MemorySegment api, MemorySegment env, MemorySegment session) {
        return new InferenceSession(api, env, session, false);
    }

    /** Constructor — use the static factory methods. */
    InferenceSession(MemorySegment api, MemorySegment env, MemorySegment session,
                     boolean envOwner) {
        this.api = api;
        this.env = env;
        this.session = session;
        this.envOwner = envOwner;
        this.inputInfos  = loadNodeInfos(true);
        this.outputInfos = loadNodeInfos(false);
    }

    /**
     * Creates an {@code InferenceSession} from a model file path using
     * default session options.
     *
     * @param modelPath path to the {@code .onnx} model file.
     * @return the loaded session.
     */
    public static InferenceSession create(String modelPath) {
        return create(modelPath, new SessionOptions());
    }

    /**
     * Creates an {@code InferenceSession} from a model file path using
     * the supplied session options.
     *
     * @param modelPath      path to the {@code .onnx} model file.
     * @param sessionOptions session configuration.
     * @return the loaded session.
     */
    public static InferenceSession create(String modelPath, SessionOptions sessionOptions) {
        MemorySegment api = OrtRuntime.api();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment env = createEnv(api, arena, LoggingLevel.WARNING, "smile-onnx");
            // Windows expects a 16-bit wide-character string encoded in UTF-16LE (Little Endian)
            MemorySegment pathSeg = arena.allocateFrom(modelPath, OS.isWindows() ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8);
            MemorySegment pSession = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st = OrtApi.CreateSession.invoke(
                    OrtApi.CreateSession(api),
                    env, pathSeg, sessionOptions.handle, pSession);
            OrtRuntime.checkStatus(api, st);
            MemorySegment sess = pSession.get(onnxruntime_c_api_h.C_POINTER, 0);
            return new InferenceSession(api, env, sess, true);
        }
    }

    /**
     * Creates an {@code InferenceSession} from a model already loaded into a
     * byte array (e.g. from a JAR resource).
     *
     * @param modelBytes     the serialised ONNX model bytes.
     * @param sessionOptions session configuration.
     * @return the loaded session.
     */
    public static InferenceSession create(byte[] modelBytes, SessionOptions sessionOptions) {
        MemorySegment api = OrtRuntime.api();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment env = createEnv(api, arena, LoggingLevel.WARNING, "smile-onnx");
            MemorySegment modelSeg = arena.allocate(modelBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(modelBytes), ValueLayout.JAVA_BYTE, 0L,
                    modelSeg, ValueLayout.JAVA_BYTE, 0L, modelBytes.length);
            MemorySegment pSession = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st = OrtApi.CreateSessionFromArray.invoke(
                    OrtApi.CreateSessionFromArray(api),
                    env, modelSeg, (long) modelBytes.length, sessionOptions.handle, pSession);
            OrtRuntime.checkStatus(api, st);
            MemorySegment sess = pSession.get(onnxruntime_c_api_h.C_POINTER, 0);
            return new InferenceSession(api, env, sess, true);
        }
    }

    /**
     * Creates an {@code InferenceSession} from a model byte array using
     * default session options.
     *
     * @param modelBytes the serialised ONNX model bytes.
     * @return the loaded session.
     */
    public static InferenceSession create(byte[] modelBytes) {
        return create(modelBytes, new SessionOptions());
    }

    // ------------------------------------------------------------------
    // Inference
    // ------------------------------------------------------------------

    /**
     * Runs inference using all model inputs and all model outputs with
     * default run options.
     *
     * @param inputs a map of input name → {@link OrtValue}.
     * @return an array of output {@link OrtValue}s in the order returned by
     *         {@link #outputNames()}.
     */
    public OrtValue[] run(Map<String, OrtValue> inputs) {
        return run(inputs, outputNames().toArray(new String[0]), null);
    }

    /**
     * Runs inference for a selected set of outputs with default run options.
     *
     * @param inputs      a map of input name → {@link OrtValue}.
     * @param outputNames the names of the outputs to compute.
     * @return the requested outputs in the supplied order.
     */
    public OrtValue[] run(Map<String, OrtValue> inputs, String[] outputNames) {
        return run(inputs, outputNames, null);
    }

    /**
     * Runs inference with explicit run options.
     *
     * @param inputs      a map of input name → {@link OrtValue}.
     * @param outputNames the names of the outputs to compute.
     * @param runOptions  per-run options, or {@code null} for defaults.
     * @return the requested outputs in the supplied order.
     */
    public OrtValue[] run(Map<String, OrtValue> inputs, String[] outputNames,
                          RunOptions runOptions) {
        int numInputs  = inputs.size();
        int numOutputs = outputNames.length;

        try (Arena arena = Arena.ofConfined()) {
            // Build input name array (char*[]) and input OrtValue pointer array
            String[] inputNames = inputs.keySet().toArray(new String[0]);
            MemorySegment inputNamesPtrs = buildStringPointerArray(arena, inputNames);
            MemorySegment inputValuesPtrs = arena.allocate(
                    MemoryLayout.sequenceLayout(numInputs, onnxruntime_c_api_h.C_POINTER));
            for (int i = 0; i < numInputs; i++) {
                OrtValue v = inputs.get(inputNames[i]);
                if (v == null) {
                    throw new IllegalArgumentException("No OrtValue for input: " + inputNames[i]);
                }
                inputValuesPtrs.setAtIndex(onnxruntime_c_api_h.C_POINTER, i, v.handle);
            }

            // Build output name array
            MemorySegment outputNamesPtrs = buildStringPointerArray(arena, outputNames);
            // ORT writes output OrtValue* into this array
            MemorySegment outputValuesPtrs = arena.allocate(
                    MemoryLayout.sequenceLayout(numOutputs, onnxruntime_c_api_h.C_POINTER));

            MemorySegment runOptHandle = (runOptions != null)
                    ? runOptions.handle : MemorySegment.NULL;

            // OrtStatusPtr Run(session, runOptions,
            //   inputNames, inputValues, numInputs,
            //   outputNames, numOutputs, outputValues)
            MemorySegment st = OrtApi.Run.invoke(
                    OrtApi.Run(api),
                    session, runOptHandle,
                    inputNamesPtrs, inputValuesPtrs, (long) numInputs,
                    outputNamesPtrs, (long) numOutputs, outputValuesPtrs);
            OrtRuntime.checkStatus(api, st);

            OrtValue[] results = new OrtValue[numOutputs];
            for (int i = 0; i < numOutputs; i++) {
                MemorySegment vHandle = outputValuesPtrs.getAtIndex(onnxruntime_c_api_h.C_POINTER, i);
                results[i] = new OrtValue(vHandle, true);
            }
            return results;
        }
    }

    // ------------------------------------------------------------------
    // Model introspection
    // ------------------------------------------------------------------

    /**
     * Returns the number of model inputs.
     *
     * @return input count.
     */
    public int inputCount() {
        return inputInfos.size();
    }

    /**
     * Returns the number of model outputs.
     *
     * @return output count.
     */
    public int outputCount() {
        return outputInfos.size();
    }

    /**
     * Returns the input node information list.
     *
     * @return list of {@link NodeInfo} for each input.
     */
    public List<NodeInfo> inputInfos() {
        return List.copyOf(inputInfos);
    }

    /**
     * Returns the output node information list.
     *
     * @return list of {@link NodeInfo} for each output.
     */
    public List<NodeInfo> outputInfos() {
        return List.copyOf(outputInfos);
    }

    /**
     * Returns the ordered list of input names.
     *
     * @return input names.
     */
    public List<String> inputNames() {
        return inputInfos.stream().map(NodeInfo::name).toList();
    }

    /**
     * Returns the ordered list of output names.
     *
     * @return output names.
     */
    public List<String> outputNames() {
        return outputInfos.stream().map(NodeInfo::name).toList();
    }

    /**
     * Returns metadata associated with the model (producer name, version, etc.).
     *
     * @return the {@link ModelMetadata}.
     */
    public ModelMetadata metadata() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pMeta = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st = OrtApi.SessionGetModelMetadata.invoke(
                    OrtApi.SessionGetModelMetadata(api), session, pMeta);
            OrtRuntime.checkStatus(api, st);
            MemorySegment meta = pMeta.get(onnxruntime_c_api_h.C_POINTER, 0);
            try {
                return readMetadata(api, arena, meta);
            } finally {
                OrtApi.ReleaseModelMetadata.invoke(OrtApi.ReleaseModelMetadata(api), meta);
            }
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Creates a default OrtEnv. */
    private static MemorySegment createEnv(MemorySegment api, Arena arena,
                                            LoggingLevel level, String logId) {
        MemorySegment logIdSeg = arena.allocateFrom(logId);
        MemorySegment pEnv = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        MemorySegment st = OrtApi.CreateEnv.invoke(
                OrtApi.CreateEnv(api), level.value(), logIdSeg, pEnv);
        OrtRuntime.checkStatus(api, st);
        return pEnv.get(onnxruntime_c_api_h.C_POINTER, 0);
    }

    /** Loads all input or output NodeInfo objects from the session. */
    private List<NodeInfo> loadNodeInfos(boolean inputs) {
        try (Arena arena = Arena.ofConfined()) {
            // Get count
            MemorySegment pCount = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st;
            if (inputs) {
                st = OrtApi.SessionGetInputCount.invoke(
                        OrtApi.SessionGetInputCount(api), session, pCount);
            } else {
                st = OrtApi.SessionGetOutputCount.invoke(
                        OrtApi.SessionGetOutputCount(api), session, pCount);
            }
            OrtRuntime.checkStatus(api, st);
            long count = pCount.get(ValueLayout.JAVA_LONG, 0);

            // Get default allocator
            MemorySegment pAlloc = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            st = OrtApi.GetAllocatorWithDefaultOptions.invoke(
                    OrtApi.GetAllocatorWithDefaultOptions(api), pAlloc);
            OrtRuntime.checkStatus(api, st);
            MemorySegment allocator = pAlloc.get(onnxruntime_c_api_h.C_POINTER, 0);

            List<NodeInfo> infos = new ArrayList<>((int) count);
            for (long i = 0; i < count; i++) {
                // Get name
                MemorySegment pName = arena.allocate(onnxruntime_c_api_h.C_POINTER);
                if (inputs) {
                    st = OrtApi.SessionGetInputName.invoke(
                            OrtApi.SessionGetInputName(api), session, i, allocator, pName);
                } else {
                    st = OrtApi.SessionGetOutputName.invoke(
                            OrtApi.SessionGetOutputName(api), session, i, allocator, pName);
                }
                OrtRuntime.checkStatus(api, st);
                MemorySegment namePtr = pName.get(onnxruntime_c_api_h.C_POINTER, 0);
                String name = OrtRuntime.readString(namePtr);
                OrtApi.AllocatorFree.invoke(OrtApi.AllocatorFree(api), allocator, namePtr);

                // Get type info
                MemorySegment pTypeInfo = arena.allocate(onnxruntime_c_api_h.C_POINTER);
                if (inputs) {
                    st = OrtApi.SessionGetInputTypeInfo.invoke(
                            OrtApi.SessionGetInputTypeInfo(api), session, i, pTypeInfo);
                } else {
                    st = OrtApi.SessionGetOutputTypeInfo.invoke(
                            OrtApi.SessionGetOutputTypeInfo(api), session, i, pTypeInfo);
                }
                OrtRuntime.checkStatus(api, st);
                MemorySegment typeInfo = pTypeInfo.get(onnxruntime_c_api_h.C_POINTER, 0);

                try {
                    NodeInfo nodeInfo = readNodeInfo(arena, name, typeInfo);
                    infos.add(nodeInfo);
                } finally {
                    OrtApi.ReleaseTypeInfo.invoke(OrtApi.ReleaseTypeInfo(api), typeInfo);
                }
            }
            return infos;
        }
    }

    /** Converts an OrtTypeInfo pointer into a NodeInfo. */
    private NodeInfo readNodeInfo(Arena arena, String name, MemorySegment typeInfo) {
        // Get ONNX value type
        MemorySegment pOnnxType = arena.allocate(onnxruntime_c_api_h.C_INT);
        MemorySegment st = OrtApi.GetOnnxTypeFromTypeInfo.invoke(
                OrtApi.GetOnnxTypeFromTypeInfo(api), typeInfo, pOnnxType);
        OrtRuntime.checkStatus(api, st);
        OnnxType onnxType = OnnxType.of(pOnnxType.get(ValueLayout.JAVA_INT, 0));

        if (onnxType == OnnxType.TENSOR) {
            MemorySegment pTsi = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            st = OrtApi.CastTypeInfoToTensorInfo.invoke(
                    OrtApi.CastTypeInfoToTensorInfo(api), typeInfo, pTsi);
            OrtRuntime.checkStatus(api, st);
            MemorySegment tsi = pTsi.get(onnxruntime_c_api_h.C_POINTER, 0);
            // tsi is borrowed (not owned), do not release
            TensorInfo ti = OrtValue.readTensorInfo(api, arena, tsi);
            return new NodeInfo(name, onnxType, ti);
        }
        return new NodeInfo(name, onnxType, null);
    }

    /** Reads ModelMetadata from a native OrtModelMetadata pointer. */
    private ModelMetadata readMetadata(MemorySegment api, Arena arena, MemorySegment meta) {
        String producerName = allocatorReadString(api, arena,
                (alloc, pOut) -> OrtApi.ModelMetadataGetProducerName.invoke(
                        OrtApi.ModelMetadataGetProducerName(api), meta, alloc, pOut));

        String graphName = allocatorReadString(api, arena,
                (alloc, pOut) -> OrtApi.ModelMetadataGetGraphName.invoke(
                        OrtApi.ModelMetadataGetGraphName(api), meta, alloc, pOut));

        String graphDesc = allocatorReadString(api, arena,
                (alloc, pOut) -> OrtApi.ModelMetadataGetGraphDescription.invoke(
                        OrtApi.ModelMetadataGetGraphDescription(api), meta, alloc, pOut));

        String domain = allocatorReadString(api, arena,
                (alloc, pOut) -> OrtApi.ModelMetadataGetDomain.invoke(
                        OrtApi.ModelMetadataGetDomain(api), meta, alloc, pOut));

        String description = allocatorReadString(api, arena,
                (alloc, pOut) -> OrtApi.ModelMetadataGetDescription.invoke(
                        OrtApi.ModelMetadataGetDescription(api), meta, alloc, pOut));

        // Version (int64 output)
        MemorySegment pVersion = arena.allocate(ValueLayout.JAVA_LONG);
        MemorySegment st = OrtApi.ModelMetadataGetVersion.invoke(
                OrtApi.ModelMetadataGetVersion(api), meta, pVersion);
        OrtRuntime.checkStatus(api, st);
        long version = pVersion.get(ValueLayout.JAVA_LONG, 0);

        // Custom metadata keys
        MemorySegment pAlloc = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        st = OrtApi.GetAllocatorWithDefaultOptions.invoke(
                OrtApi.GetAllocatorWithDefaultOptions(api), pAlloc);
        OrtRuntime.checkStatus(api, st);
        MemorySegment allocator = pAlloc.get(onnxruntime_c_api_h.C_POINTER, 0);

        MemorySegment pKeys = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        MemorySegment pNumKeys = arena.allocate(ValueLayout.JAVA_LONG);
        st = OrtApi.ModelMetadataGetCustomMetadataMapKeys.invoke(
                OrtApi.ModelMetadataGetCustomMetadataMapKeys(api),
                meta, allocator, pKeys, pNumKeys);
        OrtRuntime.checkStatus(api, st);
        long numKeys = pNumKeys.get(ValueLayout.JAVA_LONG, 0);

        Map<String, String> customMeta = new HashMap<>();
        if (numKeys > 0) {
            MemorySegment keysArray = pKeys.get(onnxruntime_c_api_h.C_POINTER, 0)
                    .reinterpret(numKeys * onnxruntime_c_api_h.C_POINTER.byteSize());
            for (long k = 0; k < numKeys; k++) {
                MemorySegment keyPtr = keysArray.getAtIndex(onnxruntime_c_api_h.C_POINTER, k);
                String key = OrtRuntime.readString(keyPtr);

                // Look up value for this key
                MemorySegment keySeg = arena.allocateFrom(key);
                String value = allocatorReadString(api, arena,
                        (alloc, pOut) -> OrtApi.ModelMetadataLookupCustomMetadataMap.invoke(
                                OrtApi.ModelMetadataLookupCustomMetadataMap(api),
                                meta, alloc, keySeg, pOut));
                customMeta.put(key, value);
                OrtApi.AllocatorFree.invoke(OrtApi.AllocatorFree(api), allocator, keyPtr);
            }
            // Free the keys array itself
            OrtApi.AllocatorFree.invoke(OrtApi.AllocatorFree(api), allocator,
                    pKeys.get(onnxruntime_c_api_h.C_POINTER, 0));
        }

        return new ModelMetadata(producerName, graphName, graphDesc, domain, description,
                version, Map.copyOf(customMeta));
    }

    /** Calls an ORT function that writes an allocator-owned string into a char**. */
    private String allocatorReadString(MemorySegment api, Arena arena,
            java.util.function.BiFunction<MemorySegment, MemorySegment, MemorySegment> fn) {
        MemorySegment pAlloc = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        MemorySegment st = OrtApi.GetAllocatorWithDefaultOptions.invoke(
                OrtApi.GetAllocatorWithDefaultOptions(api), pAlloc);
        OrtRuntime.checkStatus(api, st);
        MemorySegment allocator = pAlloc.get(onnxruntime_c_api_h.C_POINTER, 0);

        MemorySegment pStr = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        st = fn.apply(allocator, pStr);
        OrtRuntime.checkStatus(api, st);
        MemorySegment strPtr = pStr.get(onnxruntime_c_api_h.C_POINTER, 0);
        String result = OrtRuntime.readString(strPtr);
        OrtApi.AllocatorFree.invoke(OrtApi.AllocatorFree(api), allocator, strPtr);
        return result;
    }

    /**
     * Builds a native {@code char*[]} array from a Java {@code String[]}.
     * Each string is allocated as a null-terminated C string in the arena.
     */
    private static MemorySegment buildStringPointerArray(Arena arena, String[] names) {
        MemorySegment ptrArray = arena.allocate(
                MemoryLayout.sequenceLayout(names.length, onnxruntime_c_api_h.C_POINTER));
        for (int i = 0; i < names.length; i++) {
            MemorySegment nameSeg = arena.allocateFrom(names[i]);
            ptrArray.setAtIndex(onnxruntime_c_api_h.C_POINTER, i, nameSeg);
        }
        return ptrArray;
    }

    @Override
    public void close() {
        OrtApi.ReleaseSession.invoke(OrtApi.ReleaseSession(api), session);
        if (envOwner) {
            OrtApi.ReleaseEnv.invoke(OrtApi.ReleaseEnv(api), env);
        }
    }

    @Override
    public String toString() {
        return "InferenceSession{inputs=" + inputNames() + ", outputs=" + outputNames() + "}";
    }
}


