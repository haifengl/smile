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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * JUnit 5 tests for the {@code smile.onnx} public API.
 *
 * <p>The "light" model fixtures under {@code core/src/test/resources/onnx/light/}
 * are stripped versions of well-known image classifiers (AlexNet, ResNet-50,
 * VGG-19, …) where all float initialisers have been replaced with constant
 * nodes. Each {@code .pb} companion file contains the expected output
 * {@code TensorProto} computed by the ONNX reference evaluator.
 *
 * <h2>Test coverage</h2>
 * <ul>
 *   <li>Session creation from file path and from byte array.</li>
 *   <li>Model metadata retrieval.</li>
 *   <li>Input / output node introspection (count, names, tensor info).</li>
 *   <li>Inference against all nine light models with float-zero input.</li>
 *   <li>Output tensor shape and data-type validation.</li>
 *   <li>Element-wise comparison of inference output vs. {@code .pb} expected.</li>
 *   <li>Partial-output selection (running only a subset of outputs).</li>
 *   <li>Session options: intra-op threads, graph optimisation level.</li>
 *   <li>Environment sharing across multiple sessions.</li>
 *   <li>Run options: log tag.</li>
 *   <li>{@link OrtValue} factory methods and data-extraction round-trips.</li>
 *   <li>{@link Environment#buildInfo()} and {@link Environment#availableProviders()}.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InferenceSessionTest {

    /** Base resource directory that contains the light model fixtures. */
    private static final String LIGHT_DIR = "onnx/light/";

    /** Tolerance for floating-point comparisons (absolute). */
    private static final float ABS_TOLERANCE = 1e-5f;

    /** All light model stem names used for dynamic inference tests. */
    private static final String[] LIGHT_MODEL_STEMS = {
        "bvlc_alexnet",
        "densenet121",
        "inception_v1",
        "inception_v2",
        "resnet50",
        "shufflenet",
        "squeezenet",
        "vgg19",
        "zfnet512"
    };

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Loads a resource from the test classpath and returns its absolute path.
     * Returns {@code null} when the resource does not exist.
     */
    private static Path resourcePath(String name) {
        URL url = InferenceSessionTest.class.getClassLoader().getResource(name);
        if (url == null) return null;
        try {
            return Paths.get(url.toURI());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns {@code true} if the named light model resource exists on the classpath.
     * {@code modelName} is the stem without the {@code "light_"} prefix or extension,
     * e.g. {@code "squeezenet"}.
     */
    private static boolean modelExists(String modelName) {
        return resourcePath(LIGHT_DIR + "light_" + modelName + ".onnx") != null;
    }

    /**
     * Creates a float input tensor of all-zeros whose shape is taken from
     * the first input node of {@code session}. Dynamic dimensions (-1) are
     * clamped to 1.
     */
    private static OrtValue zeroInputForSession(InferenceSession session) {
        NodeInfo inputNode = session.inputInfos().getFirst();
        TensorInfo ti = inputNode.tensorInfo();
        assertNotNull(ti, "Expected tensor info for first input node");
        long[] shape = ti.shape().clone();
        for (int i = 0; i < shape.length; i++) {
            if (shape[i] < 0) shape[i] = 1;
        }
        long n = 1;
        for (long d : shape) n *= d;
        return OrtValue.fromFloatArray(new float[(int) n], shape);
    }

    // -----------------------------------------------------------------------
    // Environment / runtime-level tests
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("Environment.buildInfo() returns a non-empty string")
    void testBuildInfo() {
        String info = Environment.buildInfo();
        assertNotNull(info);
        assertFalse(info.isBlank(), "buildInfo should not be blank");
    }

    @Test
    @Order(2)
    @DisplayName("Environment.availableProviders() always contains CPUExecutionProvider")
    void testAvailableProviders() {
        List<String> providers = Environment.availableProviders();
        assertNotNull(providers);
        assertFalse(providers.isEmpty(), "At least one provider expected");
        assertTrue(providers.contains("CPUExecutionProvider"),
                "CPUExecutionProvider should always be present; got: " + providers);
    }

    // -----------------------------------------------------------------------
    // Session creation tests
    // -----------------------------------------------------------------------

    @Test
    @Order(10)
    @DisplayName("Create session from file path")
    void testCreateSessionFromPath() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (InferenceSession session = InferenceSession.create(model.toString())) {
            assertNotNull(session);
            assertTrue(session.inputCount() > 0, "Expected at least one input");
            assertTrue(session.outputCount() > 0, "Expected at least one output");
        }
    }

    @Test
    @Order(11)
    @DisplayName("Create session from byte array")
    void testCreateSessionFromBytes() throws IOException {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        try (InputStream is = InferenceSessionTest.class.getClassLoader()
                .getResourceAsStream(LIGHT_DIR + "light_squeezenet.onnx")) {
            assertNotNull(is, "Resource stream must not be null");
            byte[] bytes = is.readAllBytes();
            try (InferenceSession session = InferenceSession.create(bytes)) {
                assertNotNull(session);
                assertTrue(session.inputCount() > 0);
                assertTrue(session.outputCount() > 0);
            }
        }
    }

    @Test
    @Order(12)
    @DisplayName("Create session with custom SessionOptions (intra-op threads + BASIC opt)")
    void testCreateSessionWithOptions() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (SessionOptions opts = new SessionOptions()) {
            opts.setIntraOpNumThreads(2)
                .setGraphOptimizationLevel(GraphOptimizationLevel.ENABLE_BASIC);
            try (InferenceSession session = InferenceSession.create(model.toString(), opts)) {
                assertNotNull(session);
                assertTrue(session.inputCount() > 0);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Metadata tests
    // -----------------------------------------------------------------------

    @Test
    @Order(20)
    @DisplayName("ModelMetadata fields are non-null and accessible")
    void testModelMetadata() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (InferenceSession session = InferenceSession.create(model.toString())) {
            ModelMetadata meta = session.metadata();
            assertNotNull(meta);
            // Fields may be empty strings but must not be null
            assertNotNull(meta.producerName());
            assertNotNull(meta.graphName());
            assertNotNull(meta.domain());
            assertNotNull(meta.description());
            assertNotNull(meta.graphDescription());
            assertNotNull(meta.customMetadata());
        }
    }

    // -----------------------------------------------------------------------
    // Node introspection tests
    // -----------------------------------------------------------------------

    @Test
    @Order(30)
    @DisplayName("inputInfos() returns well-formed NodeInfo records")
    void testInputInfos() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (InferenceSession session = InferenceSession.create(model.toString())) {
            List<NodeInfo> infos = session.inputInfos();
            assertNotNull(infos);
            assertEquals(session.inputCount(), infos.size());
            for (NodeInfo ni : infos) {
                assertNotNull(ni.name());
                assertFalse(ni.name().isBlank(), "Input name must not be blank");
                assertEquals(OnnxType.TENSOR, ni.onnxType());
                assertNotNull(ni.tensorInfo());
                assertNotNull(ni.tensorInfo().elementType());
                assertNotNull(ni.tensorInfo().shape());
                assertTrue(ni.tensorInfo().rank() > 0);
            }
        }
    }

    @Test
    @Order(31)
    @DisplayName("outputInfos() returns well-formed NodeInfo records")
    void testOutputInfos() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (InferenceSession session = InferenceSession.create(model.toString())) {
            List<NodeInfo> infos = session.outputInfos();
            assertNotNull(infos);
            assertEquals(session.outputCount(), infos.size());
            for (NodeInfo ni : infos) {
                assertNotNull(ni.name());
                assertFalse(ni.name().isBlank());
            }
        }
    }

    @Test
    @Order(32)
    @DisplayName("inputNames()/outputNames() correspond to inputInfos()/outputInfos()")
    void testNodeNames() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (InferenceSession session = InferenceSession.create(model.toString())) {
            List<String> inNames  = session.inputNames();
            List<String> outNames = session.outputNames();
            assertFalse(inNames.isEmpty());
            assertFalse(outNames.isEmpty());
            assertEquals(inNames,  session.inputInfos().stream().map(NodeInfo::name).toList());
            assertEquals(outNames, session.outputInfos().stream().map(NodeInfo::name).toList());
        }
    }

    @Test
    @Order(33)
    @DisplayName("NodeInfo.isTensor() is true for all inputs of ResNet-50")
    void testNodeInfoIsTensor() {
        Assumptions.assumeTrue(modelExists("resnet50"),
                "light_resnet50.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_resnet50.onnx");
        assertNotNull(model);
        try (InferenceSession session = InferenceSession.create(model.toString())) {
            for (NodeInfo ni : session.inputInfos()) {
                assertTrue(ni.isTensor(), "All inputs of ResNet-50 should be tensors");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Inference tests — all light models via @TestFactory / DynamicTest
    // -----------------------------------------------------------------------

    /**
     * Dynamically generates one inference test per light model.
     * Each test:
     * <ol>
     *   <li>Loads the {@code .onnx} model.</li>
     *   <li>Introspects the input shape from the model itself.</li>
     *   <li>Runs inference with an all-zero float tensor.</li>
     *   <li>Compares the output against the expected {@code .pb} TensorProto.</li>
     * </ol>
     */
    @TestFactory
    @Order(40)
    @DisplayName("Inference against all light models")
    Collection<DynamicTest> testLightModelInference() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String stem : LIGHT_MODEL_STEMS) {
            tests.add(dynamicTest("Inference: " + stem,
                    () -> runLightModelTest(stem)));
        }
        return tests;
    }

    /** Runs the full inference + output-comparison test for one light model. */
    private void runLightModelTest(String stem) throws IOException {
        String modelRes  = LIGHT_DIR + "light_" + stem + ".onnx";
        String outputRes = LIGHT_DIR + "light_" + stem + "_output_0.pb";

        Path modelPath  = resourcePath(modelRes);
        Path outputPath = resourcePath(outputRes);
        Assumptions.assumeTrue(modelPath  != null, modelRes  + " not found on classpath");
        Assumptions.assumeTrue(outputPath != null, outputRes + " not found on classpath");

        TensorProtoReader expected = TensorProtoReader.read(outputPath);
        assertNotNull(expected, "Failed to read expected output from " + outputRes);

        try (InferenceSession session = InferenceSession.create(modelPath.toString())) {
            OrtValue inputValue = zeroInputForSession(session);
            String   inputName  = session.inputNames().getFirst();
            Map<String, OrtValue> inputs = new LinkedHashMap<>();
            inputs.put(inputName, inputValue);

            OrtValue[] outputs = session.run(inputs);
            try {
                assertEquals(session.outputCount(), outputs.length,
                        "Output count mismatch for model " + stem);
                assertTrue(outputs.length > 0,
                        "Expected at least one output for model " + stem);

                OrtValue output0 = outputs[0];
                assertNotNull(output0);
                assertEquals(OnnxType.TENSOR, output0.onnxType(),
                        "First output should be TENSOR for model " + stem);
                assertTrue(output0.isTensor());

                TensorInfo ti = output0.tensorInfo();
                assertNotNull(ti);
                assertEquals(ElementType.FLOAT, ti.elementType(),
                        "Expected FLOAT element type for model " + stem);
                assertArrayEquals(expected.dims(), ti.shape(),
                        "Output shape mismatch for model " + stem);

                float[] actual = output0.toFloatArray();
                float[] exp    = expected.floatData();
                assertNotNull(exp, "Expected float data in .pb for model " + stem);
                assertEquals(exp.length, actual.length,
                        "Element count mismatch for model " + stem);
                for (int i = 0; i < exp.length; i++) {
                    final int idx = i;
                    assertEquals(exp[i], actual[i], ABS_TOLERANCE,
                            () -> String.format("Output[%d] mismatch for model %s", idx, stem));
                }
            } finally {
                inputValue.close();
                for (OrtValue v : outputs) if (v != null) v.close();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Partial output selection test
    // -----------------------------------------------------------------------

    @Test
    @Order(50)
    @DisplayName("run() with explicit output-name subset returns only those outputs")
    void testPartialOutputSelection() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (InferenceSession session = InferenceSession.create(model.toString())) {
            OrtValue inputValue = zeroInputForSession(session);
            String   inputName  = session.inputNames().getFirst();
            String   firstOut   = session.outputNames().getFirst();

            Map<String, OrtValue> inputs = Map.of(inputName, inputValue);
            OrtValue[] outputs = session.run(inputs, new String[]{ firstOut });
            try {
                assertEquals(1, outputs.length, "Should return exactly 1 output");
                assertNotNull(outputs[0]);
                assertTrue(outputs[0].isTensor());
            } finally {
                inputValue.close();
                for (OrtValue v : outputs) if (v != null) v.close();
            }
        }
    }

    // -----------------------------------------------------------------------
    // RunOptions test
    // -----------------------------------------------------------------------

    @Test
    @Order(51)
    @DisplayName("run() with explicit RunOptions completes without error")
    void testRunWithRunOptions() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (InferenceSession session = InferenceSession.create(model.toString());
             RunOptions runOpts = new RunOptions()) {
            runOpts.setLogTag("test-run");

            OrtValue inputValue = zeroInputForSession(session);
            String   inputName  = session.inputNames().getFirst();
            Map<String, OrtValue> inputs = Map.of(inputName, inputValue);

            OrtValue[] outputs = session.run(inputs,
                    session.outputNames().toArray(new String[0]), runOpts);
            try {
                assertTrue(outputs.length > 0);
                assertNotNull(outputs[0]);
            } finally {
                inputValue.close();
                for (OrtValue v : outputs) if (v != null) v.close();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Shared Environment test
    // -----------------------------------------------------------------------

    @Test
    @Order(52)
    @DisplayName("Shared Environment can serve multiple independent sessions")
    void testSharedEnvironment() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Assumptions.assumeTrue(modelExists("resnet50"),
                "light_resnet50.onnx not found on classpath");

        Path squeezenet = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        Path resnet50   = resourcePath(LIGHT_DIR + "light_resnet50.onnx");
        assertNotNull(squeezenet);
        assertNotNull(resnet50);

        try (Environment env = new Environment(LoggingLevel.WARNING, "test-env")) {
            try (InferenceSession s1 = env.createSession(squeezenet.toString());
                 InferenceSession s2 = env.createSession(resnet50.toString())) {

                OrtValue in1 = zeroInputForSession(s1);
                OrtValue in2 = zeroInputForSession(s2);
                Map<String, OrtValue> inputs1 = Map.of(s1.inputNames().getFirst(), in1);
                Map<String, OrtValue> inputs2 = Map.of(s2.inputNames().getFirst(), in2);

                OrtValue[] out1 = s1.run(inputs1);
                OrtValue[] out2 = s2.run(inputs2);
                try {
                    assertTrue(out1.length > 0, "s1 should produce outputs");
                    assertTrue(out2.length > 0, "s2 should produce outputs");
                    assertTrue(out1[0].isTensor());
                    assertTrue(out2[0].isTensor());
                } finally {
                    in1.close();
                    in2.close();
                    for (OrtValue v : out1) if (v != null) v.close();
                    for (OrtValue v : out2) if (v != null) v.close();
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // OrtValue round-trip tests
    // -----------------------------------------------------------------------

    @Test
    @Order(60)
    @DisplayName("OrtValue float round-trip: fromFloatArray / toFloatArray")
    void testOrtValueFloatRoundTrip() {
        float[] data  = { 1.0f, -2.5f, 3.14159f, Float.MIN_VALUE, Float.MAX_VALUE };
        long[]  shape = { 1, 5 };
        try (OrtValue v = OrtValue.fromFloatArray(data, shape)) {
            assertNotNull(v);
            assertTrue(v.isTensor());
            assertEquals(OnnxType.TENSOR, v.onnxType());
            TensorInfo ti = v.tensorInfo();
            assertEquals(ElementType.FLOAT, ti.elementType());
            assertArrayEquals(shape, ti.shape());
            assertEquals(5L, ti.elementCount());
            assertArrayEquals(data, v.toFloatArray(), ABS_TOLERANCE);
        }
    }

    @Test
    @Order(61)
    @DisplayName("OrtValue double round-trip: fromDoubleArray / toDoubleArray")
    void testOrtValueDoubleRoundTrip() {
        double[] data  = { 1.0, -2.5, Math.PI, Double.MIN_VALUE, Double.MAX_VALUE };
        long[]   shape = { 5 };
        try (OrtValue v = OrtValue.fromDoubleArray(data, shape)) {
            TensorInfo ti = v.tensorInfo();
            assertEquals(ElementType.DOUBLE, ti.elementType());
            assertArrayEquals(shape, ti.shape());
            assertArrayEquals(data, v.toDoubleArray(), 1e-12);
        }
    }

    @Test
    @Order(62)
    @DisplayName("OrtValue int32 round-trip: fromIntArray / toIntArray")
    void testOrtValueIntRoundTrip() {
        int[]  data  = { 0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE };
        long[] shape = { 1, 5 };
        try (OrtValue v = OrtValue.fromIntArray(data, shape)) {
            TensorInfo ti = v.tensorInfo();
            assertEquals(ElementType.INT32, ti.elementType());
            assertArrayEquals(shape, ti.shape());
            assertArrayEquals(data, v.toIntArray());
        }
    }

    @Test
    @Order(63)
    @DisplayName("OrtValue int64 round-trip: fromLongArray / toLongArray")
    void testOrtValueLongRoundTrip() {
        long[] data  = { 0L, Long.MAX_VALUE, Long.MIN_VALUE, 42L, -1L };
        long[] shape = { 5 };
        try (OrtValue v = OrtValue.fromLongArray(data, shape)) {
            TensorInfo ti = v.tensorInfo();
            assertEquals(ElementType.INT64, ti.elementType());
            assertArrayEquals(shape, ti.shape());
            assertArrayEquals(data, v.toLongArray());
        }
    }

    @Test
    @Order(64)
    @DisplayName("OrtValue int8 round-trip: fromByteArray / toByteArray")
    void testOrtValueByteRoundTrip() {
        byte[] data  = { 0, 1, -1, Byte.MAX_VALUE, Byte.MIN_VALUE };
        long[] shape = { 5 };
        try (OrtValue v = OrtValue.fromByteArray(data, shape)) {
            TensorInfo ti = v.tensorInfo();
            assertEquals(ElementType.INT8, ti.elementType());
            assertArrayEquals(shape, ti.shape());
            assertArrayEquals(data, v.toByteArray());
        }
    }

    @Test
    @Order(65)
    @DisplayName("OrtValue bool round-trip: fromBooleanArray values stored as 0/1 bytes")
    void testOrtValueBoolRoundTrip() {
        boolean[] data  = { true, false, true, true, false };
        long[]    shape = { 5 };
        try (OrtValue v = OrtValue.fromBooleanArray(data, shape)) {
            TensorInfo ti = v.tensorInfo();
            assertEquals(ElementType.BOOL, ti.elementType());
            assertArrayEquals(shape, ti.shape());
            byte[] bytes = v.toByteArray();
            assertEquals(data.length, bytes.length);
            for (int i = 0; i < data.length; i++) {
                assertEquals(data[i] ? 1 : 0, bytes[i] & 0xFF,
                        "Bool mismatch at index " + i);
            }
        }
    }

    @Test
    @Order(66)
    @DisplayName("OrtValue: rank-1 single-element tensor")
    void testOrtValueScalarTensor() {
        float[] data  = { 42.0f };
        long[]  shape = { 1 };
        try (OrtValue v = OrtValue.fromFloatArray(data, shape)) {
            TensorInfo ti = v.tensorInfo();
            assertEquals(1, ti.rank());
            assertEquals(1L, ti.elementCount());
            assertFalse(ti.isDynamic());
        }
    }

    @Test
    @Order(67)
    @DisplayName("OrtValue: 4-D image tensor [1, 3, 224, 224]")
    void testOrtValue4dTensorShape() {
        long[] shape = { 1, 3, 224, 224 };
        int    n     = 3 * 224 * 224;
        try (OrtValue v = OrtValue.fromFloatArray(new float[n], shape)) {
            TensorInfo ti = v.tensorInfo();
            assertEquals(4, ti.rank());
            assertArrayEquals(shape, ti.shape());
            assertEquals(n, ti.elementCount());
        }
    }

    // -----------------------------------------------------------------------
    // TensorInfo unit tests
    // -----------------------------------------------------------------------

    @Test
    @Order(70)
    @DisplayName("TensorInfo.elementCount() returns -1 when any dimension is dynamic")
    void testTensorInfoDynamicShape() {
        TensorInfo ti = new TensorInfo(ElementType.FLOAT, new long[]{ -1, 3, 224, 224 });
        assertTrue(ti.isDynamic());
        assertEquals(-1L, ti.elementCount());
    }

    @Test
    @Order(71)
    @DisplayName("TensorInfo.elementCount() is the product of all dimensions")
    void testTensorInfoStaticShape() {
        TensorInfo ti = new TensorInfo(ElementType.FLOAT, new long[]{ 2, 3, 4 });
        assertFalse(ti.isDynamic());
        assertEquals(24L, ti.elementCount());
        assertEquals(3, ti.rank());
    }

    @Test
    @Order(72)
    @DisplayName("TensorInfo.toString() includes element type and shape")
    void testTensorInfoToString() {
        TensorInfo ti = new TensorInfo(ElementType.FLOAT, new long[]{ 1, 1000 });
        String s = ti.toString();
        assertTrue(s.contains("FLOAT"), "toString should mention element type");
        assertTrue(s.contains("1000"),  "toString should mention shape dimension");
    }

    // -----------------------------------------------------------------------
    // NodeInfo unit tests
    // -----------------------------------------------------------------------

    @Test
    @Order(80)
    @DisplayName("NodeInfo convenience constructor sets OnnxType.TENSOR")
    void testNodeInfoConvenienceConstructor() {
        TensorInfo ti = new TensorInfo(ElementType.FLOAT, new long[]{ 1, 3, 224, 224 });
        NodeInfo   ni = new NodeInfo("data", ti);
        assertEquals("data", ni.name());
        assertEquals(OnnxType.TENSOR, ni.onnxType());
        assertTrue(ni.isTensor());
        assertSame(ti, ni.tensorInfo());
    }

    @Test
    @Order(81)
    @DisplayName("NodeInfo with non-tensor OnnxType has null tensorInfo")
    void testNodeInfoNonTensor() {
        NodeInfo ni = new NodeInfo("seq", OnnxType.SEQUENCE, null);
        assertFalse(ni.isTensor());
        assertNull(ni.tensorInfo());
    }

    @Test
    @Order(82)
    @DisplayName("NodeInfo.toString() includes node name and type for tensor nodes")
    void testNodeInfoToStringTensor() {
        TensorInfo ti = new TensorInfo(ElementType.FLOAT, new long[]{ 1, 1000 });
        NodeInfo   ni = new NodeInfo("softmax_output", ti);
        String s = ni.toString();
        assertTrue(s.contains("softmax_output"), "toString should contain node name");
        assertTrue(s.contains("TENSOR"),         "toString should mention TENSOR type");
    }

    // -----------------------------------------------------------------------
    // SessionOptions tests
    // -----------------------------------------------------------------------

    @Test
    @Order(90)
    @DisplayName("SessionOptions: all GraphOptimizationLevel values are accepted")
    void testSessionOptionsAllOptLevels() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        for (GraphOptimizationLevel level : GraphOptimizationLevel.values()) {
            try (SessionOptions opts = new SessionOptions()) {
                opts.setGraphOptimizationLevel(level);
                try (InferenceSession session = InferenceSession.create(model.toString(), opts)) {
                    assertNotNull(session, "Session must be created with level " + level);
                }
            }
        }
    }

    @Test
    @Order(91)
    @DisplayName("SessionOptions: all ExecutionMode values are accepted")
    void testSessionOptionsExecutionMode() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        for (ExecutionMode mode : ExecutionMode.values()) {
            try (SessionOptions opts = new SessionOptions()) {
                opts.setExecutionMode(mode);
                try (InferenceSession session = InferenceSession.create(model.toString(), opts)) {
                    assertNotNull(session, "Session must be created with mode " + mode);
                }
            }
        }
    }

    @Test
    @Order(92)
    @DisplayName("SessionOptions: intra-op and inter-op thread counts accepted")
    void testSessionOptionsThreadCounts() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (SessionOptions opts = new SessionOptions()) {
            opts.setIntraOpNumThreads(4).setInterOpNumThreads(2);
            try (InferenceSession session = InferenceSession.create(model.toString(), opts)) {
                assertNotNull(session);
            }
        }
    }

    // -----------------------------------------------------------------------
    // OnnxException tests
    // -----------------------------------------------------------------------

    @Test
    @Order(100)
    @DisplayName("Loading a non-existent model path throws OnnxException")
    void testLoadNonExistentModel() {
        assertThrows(OnnxException.class,
                () -> InferenceSession.create("/non/existent/path/model.onnx").close());
    }

    @Test
    @Order(101)
    @DisplayName("Loading an empty byte array throws OnnxException")
    void testLoadEmptyBytes() {
        assertThrows(OnnxException.class,
                () -> InferenceSession.create(new byte[0]).close());
    }

    @Test
    @Order(102)
    @DisplayName("OnnxException carries the ORT error code")
    void testOnnxExceptionErrorCode() {
        OnnxException ex = new OnnxException(7, "test error");
        assertEquals(7, ex.errorCode());
        assertEquals("test error", ex.getMessage());
    }

    @Test
    @Order(103)
    @DisplayName("OnnxException with cause preserves message and cause chain")
    void testOnnxExceptionWithCause() {
        Throwable cause = new RuntimeException("root cause");
        OnnxException ex = new OnnxException("wrapped", cause);
        assertEquals("wrapped", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // -----------------------------------------------------------------------
    // toString / display tests
    // -----------------------------------------------------------------------

    @Test
    @Order(110)
    @DisplayName("InferenceSession.toString() contains 'InferenceSession'")
    void testSessionToString() {
        Assumptions.assumeTrue(modelExists("squeezenet"),
                "light_squeezenet.onnx not found on classpath");
        Path model = resourcePath(LIGHT_DIR + "light_squeezenet.onnx");
        assertNotNull(model);
        try (InferenceSession session = InferenceSession.create(model.toString())) {
            String s = session.toString();
            assertNotNull(s);
            assertTrue(s.contains("InferenceSession"),
                    "toString should contain class name");
        }
    }

    @Test
    @Order(111)
    @DisplayName("ModelMetadata.toString() includes producer name and domain")
    void testModelMetadataToString() {
        ModelMetadata meta = new ModelMetadata(
                "pytorch", "main_graph", "", "ai.onnx", "", 9L, Map.of());
        String s = meta.toString();
        assertTrue(s.contains("pytorch"),  "toString should include producerName");
        assertTrue(s.contains("ai.onnx"), "toString should include domain");
    }
}

