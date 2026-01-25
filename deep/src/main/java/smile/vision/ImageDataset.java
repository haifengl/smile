/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.vision;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.ToIntFunction;
import javax.imageio.ImageIO;
import smile.deep.Dataset;
import smile.deep.SampleBatch;
import smile.deep.tensor.Tensor;
import smile.math.MathEx;
import smile.vision.transform.Transform;

/**
 * Each of these directories should contain one subdirectory for each class
 * in the dataset. The subdirectories are named after the corresponding
 * class and contain all the images for that class. Ensure that each image
 * file is named uniquely and stored in a common format such as JPEG or PNG.
 *
 * @author Haifeng Li
 */
public class ImageDataset implements Dataset {
    record ImageFile(File file, String label) { }
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ImageDataset.class);
    private final ArrayList<ImageFile> samples = new ArrayList<>();
    private final int batch;
    private final Transform transform;
    private final ToIntFunction<String> targetTransform;

    /**
     * Constructor.
     * @param batch the mini-batch size.
     * @param root the root directory of image dataset.
     * @param transform the transformation from image to tensor.
     * @param targetTransform the transform from image label to class index.
     * @throws IOException if the root directory doesn't exist or doesn't have images.
     */
    public ImageDataset(int batch, String root, Transform transform, ToIntFunction<String> targetTransform) throws IOException {
        this.batch = batch;
        this.transform = transform;
        this.targetTransform = targetTransform;

        File dir = new File(root);
        if (!dir.exists()) {
            throw new IOException("Dataset root directory doesn't exist: " + root);
        }

        for (var child : Objects.requireNonNull(dir.listFiles())) {
            if (child.isDirectory()) {
                String label = child.getName();
                File[] images = Objects.requireNonNull(child.listFiles());
                for (var image : images) {
                    if (image.isFile()) {
                        String name = image.getName().toLowerCase();
                        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                            samples.add(new ImageFile(image, label));
                        }
                    }
                }
            }
        }

        if (samples.isEmpty()) {
            throw new IOException("No JPEG or PNG images found in " + root);
        }
    }

    @Override
    public void close() {
        // We don't hold any (external) resources.
    }

    @Override
    public long size() {
        return samples.size();
    }

    @Override
    public Iterator<SampleBatch> iterator() {
        final int size = samples.size();
        final int[] permutation = MathEx.permutate(size);
        final BlockingQueue<SampleBatch> queue = new LinkedBlockingQueue<>(100);

        final int start = Math.min(batch, size);
        final int[] index = Arrays.copyOf(permutation, start);

        try {
            // prefetch the first batch
            queue.put(readImages(index));
        } catch (Exception ex) {
            logger.error("Failed to load the first batch", ex);
        }

        final Runnable worker = () -> {
            for (int i = start; i < size; ) {
                int n = Math.min(batch, size - i);
                System.arraycopy(permutation, i, index,  0, n);
                i += n;

                try {
                    queue.put(readImages(n == index.length ? index : Arrays.copyOf(index, n)));
                } catch (Exception ex) {
                    logger.error("Failed to load images", ex);
                }
            }
        };

        Thread thread = new Thread(worker, "ImageDatasetLoader");
        thread.start();

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !queue.isEmpty() || thread.isAlive();
            }

            @Override
            public SampleBatch next() {
                try {
                    return queue.take();
                } catch (InterruptedException ex) {
                    logger.error("Failed to take next sample batch", ex);
                    return null;
                }
            }
        };
    }

    /**
     * Reads a mini-batch of image samples.
     * @param index the sample index.
     * @return the sample batch.
     * @throws IOException if fail to read the image.
     */
    private SampleBatch readImages(int[] index) throws IOException {
        int n = index.length;
        long[] target = new long[n];
        BufferedImage[] images = new BufferedImage[n];
        for (int i = 0; i < n; i++) {
            var sample = samples.get(index[i]);
            images[i] = ImageIO.read(sample.file);
            target[i] = targetTransform.applyAsInt(sample.label);
        }
        return new SampleBatch(transform.forward(images), Tensor.of(target, images.length));
    }
}
