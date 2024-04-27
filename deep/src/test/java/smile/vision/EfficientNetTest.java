/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.vision;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import smile.deep.Loss;
import smile.deep.Optimizer;
import smile.deep.metric.Accuracy;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import org.junit.jupiter.api.*;
import smile.math.TimeFunction;
import smile.vision.transform.Transform;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class EfficientNetTest {

    public EfficientNetTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void test() throws IOException {
        Device device = Device.preferredDevice();
        device.setDefaultDevice();

        var model = EfficientNet.V2S();
        model.eval();
        model.to(device);

        var lenna = ImageIO.read(new File("deep/src/universal/data/image/Lenna.png"));
        var panda = ImageIO.read(new File("deep/src/universal/data/image/panda.jpg"));

        try (var guard = Tensor.noGradGuard()) {
            // https://discuss.pytorch.org/t/libtorchs-cpu-inference-is-much-slower-on-windows-than-on-linux/166194/2
            // The first iteration(s) are slow due to multiple reasons:
            //
            // The very first CUDA call (it could be a tensor creation etc.)
            // is creating the CUDA context, which loads the driver etc.
            // In older CUDA versions (<11.7) all kernels for your GPU
            // architecture were also directly loaded into the context,
            // which takes time and uses memory. Since CUDA 11.7 PyTorch has
            // enabled "lazy module loading", which will only load the called
            // kernel into the context if needed. This will reduce the startup
            // time as well as the memory usage significantly.
            //
            // The first iterations of your actual workload need to allocate
            // new memory, which will then be reused through the CUDACachingAllocator.
            // However, the initial cudaMalloc calls are also "expensive" (compared
            // to just reusing the already allocated memory) and you would thus also
            // see a slow iteration time until your workload reached the peak memory
            // and is able to reuse the GPU memory. Note that new cudaMalloc calls
            // could of course still happen during the training e.g. if your input
            // size increases etc.
            //
            // If you are using conv layers and are allowing cuDNN to benchmark valid
            // kernels and select the fastest one (via torch.backends.cudnn.benchmark = True)
            // the profiling and kernel selection for each new workload (i.e. new input shape,
            // new dtype etc. to the conv layer) will also see an overhead.
            long startTime = System.nanoTime();
            var output = model.forward(panda);
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1000000;  //divide by 1000000 to get milliseconds.
            System.out.println("1st run elapsed time: " + duration + "ms");

            startTime = System.nanoTime();
            output = model.forward(lenna, panda);
            endTime = System.nanoTime();
            duration = (endTime - startTime) / 1000000;  //divide by 1000000 to get milliseconds.
            System.out.println("2nd run elapsed time: " + duration + "ms");

            var topk = output.topk(5);
            topk._2().to(Device.CPU());
            String[] images = {"Lenna", "Panda"};
            for (int i = 0; i < 2; i++) {
                System.out.println("======== " + images[i] + " ========");
                for (int j = 0; j < 5; j++) {
                    System.out.println(ImageNet.labels[topk._2().getInt(i, j)]);
                }
            }
            assertEquals(515, topk._2().getInt(0, 0));
            assertEquals(388, topk._2().getInt(1, 0));
        }
    }

    @Test
    public void train() throws IOException {
        Device device = Device.preferredDevice();
        device.setDefaultDevice();

        var model = EfficientNet.V2S();
        model.to(device);

        var transform = Transform.classification(64, 64);
        var data = new ImageDataset(32, "D:\\data\\imagenet-mini\\train", transform, ImageNet.folder2Target);
        var val = new ImageDataset(128, "D:\\data\\imagenet-mini\\val", transform, ImageNet.folder2Target);

        var schedule = TimeFunction.piecewise(new int[] { 10000 },
                TimeFunction.linear(0.00001, 10000, 0.00005),
                TimeFunction.cosine(0.00001, 5000, 0.00005));
        model.setLearningRateSchedule(schedule);
        // Use parameters from the paper, the rests are Keras default values.
        // Note that Keras has different default values from PyTorch (e.g. alpha and eps).
        Optimizer optimizer = Optimizer.RMSprop(model, 0.00001, 0.9, 1E-07, 1E-05, 0.9, false);
        model.train(20, optimizer, Loss.nll(), data, val, null, new Accuracy());
    }
}