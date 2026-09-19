package smile.onnx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against {@code MemorySegment.asByteBuffer()} defaulting to
 * {@code BIG_ENDIAN}, which corrupts float tensors on little-endian hosts
 * and can make ImageNet classifiers emit near-uniform scores.
 */
public class OrtValueEndianTest {

    @Test
    void floatRoundtripPreservesNativeBits() {
        float[] data = {0.5f, -1.25f, 3.1415927f, 1e-6f, 255f / 255f};
        try (OrtValue v = OrtValue.fromFloatArray(data, new long[]{1, data.length})) {
            float[] out = v.toFloatArray();
            assertArrayEquals(data, out);
            for (int i = 0; i < data.length; i++) {
                assertEquals(Float.floatToIntBits(data[i]), Float.floatToIntBits(out[i]));
            }
        }
    }

    @Test
    void efficientNetLogitsNotCollapsed() {
        Path model = Path.of("model/efficientnetv2_rw_m_Opset17.onnx");
        if (!Files.exists(model)) {
            model = Path.of("../model/efficientnetv2_rw_m_Opset17.onnx");
        }
        Assumptions.assumeTrue(Files.exists(model), "efficientnetv2_rw_m_Opset17.onnx not found");

        try (InferenceSession session = InferenceSession.create(model.toString())) {
            long[] shape = session.inputInfos().getFirst().tensorInfo().shape().clone();
            long elems = 1;
            for (int i = 0; i < shape.length; i++) {
                if (shape[i] < 0) {
                    shape[i] = i == 0 ? 1 : 320;
                }
                elems *= shape[i];
            }
            assertEquals(4, shape.length);
            assertEquals(3, shape[1]);

            float[] mean = {0.485f, 0.456f, 0.406f};
            float[] std = {0.229f, 0.224f, 0.225f};
            float[] data = new float[(int) elems];
            int plane = (int) (shape[2] * shape[3]);
            // Strong red field after ImageNet normalization — should not yield identical logits.
            for (int c = 0; c < 3; c++) {
                float pixel = c == 0 ? 1f : 0f;
                float v = (pixel - mean[c]) / std[c];
                Arrays.fill(data, c * plane, (c + 1) * plane, v);
            }

            try (OrtValue input = OrtValue.fromFloatArray(data, shape)) {
                OrtValue[] outs = session.run(Map.of(session.inputNames().getFirst(), input));
                try {
                    float[] logits = outs[0].toFloatArray();
                    assertEquals(1000, logits.length);
                    boolean allEqual = true;
                    for (int i = 1; i < logits.length; i++) {
                        if (Float.compare(logits[i], logits[0]) != 0) {
                            allEqual = false;
                            break;
                        }
                    }
                    assertFalse(allEqual, "all logits identical — likely native endian / input corruption");

                    float[] probs = logits.clone();
                    MathEx.softmax(probs);
                    int argmax = 0;
                    for (int i = 1; i < probs.length; i++) {
                        if (probs[i] > probs[argmax]) {
                            argmax = i;
                        }
                    }
                    System.out.printf("shape=%s maxP=%.4f argmax=%d logitMax=%.3f%n",
                            Arrays.toString(shape), probs[argmax], argmax, logits[argmax]);
                } finally {
                    for (OrtValue o : outs) {
                        o.close();
                    }
                }
            }
        }
    }
}
