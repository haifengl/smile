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
package smile.vision;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Set<LoaderState> loaders = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Runtime state for one dataset iterator loader. */
    private static final class LoaderState {
        final BlockingQueue<Object> queue = new LinkedBlockingQueue<>(100);
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final Object end = new Object();
        Thread thread;

        void cancel() {
            if (cancelled.compareAndSet(false, true) && thread != null) {
                thread.interrupt();
            }
        }
    }

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
        if (!closed.compareAndSet(false, true)) return;

        for (var loader : loaders) {
            shutdown(loader);
        }
        loaders.clear();
    }

    @Override
    public long size() {
        return samples.size();
    }

    @Override
    public Iterator<SampleBatch> iterator() {
        if (closed.get()) {
            throw new IllegalStateException("Dataset is already closed");
        }

        final LoaderState loader = new LoaderState();
        loaders.add(loader);
        final int size = samples.size();
        final int[] permutation = MathEx.permutate(size);

        final int start = Math.min(batch, size);
        final int[] index = Arrays.copyOf(permutation, start);

        try {
            // prefetch the first batch
            loader.queue.put(readImages(index));
        } catch (Exception ex) {
            logger.error("Failed to load the first batch", ex);
            loader.done.set(true);
            try {
                loader.queue.put(loader.end);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        loader.thread = Thread.ofPlatform().name("image-dataset-loader").start(() -> {
                  try {
                      for (int i = start; i < size && !loader.cancelled.get(); ) {
                          int n = Math.min(batch, size - i);
                          System.arraycopy(permutation, i, index,  0, n);
                          i += n;

                          try {
                              loader.queue.put(readImages(n == index.length ? index : Arrays.copyOf(index, n)));
                          } catch (InterruptedException ex) {
                              Thread.currentThread().interrupt();
                              break;
                          } catch (Exception ex) {
                              logger.error("Failed to load images", ex);
                              break;
                          }
                      }
                  } finally {
                      loader.done.set(true);
                      try {
                          loader.queue.put(loader.end);
                      } catch (InterruptedException ex) {
                          Thread.currentThread().interrupt();
                      }
                  }
              });

        return new Iterator<>() {
            Object next;
            final AtomicBoolean cleaned = new AtomicBoolean(false);

            private void cleanup() {
                if (cleaned.compareAndSet(false, true)) {
                    shutdown(loader);
                    loaders.remove(loader);
                }
            }

            private void load() {
                if (next != null) {
                    return;
                }

                if (loader.done.get() && loader.queue.isEmpty()) {
                    next = loader.end;
                    return;
                }

                try {
                    next = loader.queue.take();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    cleanup();
                    throw new NoSuchElementException("Image loading interrupted: " + ex.getMessage());
                }
            }

            @Override
            public boolean hasNext() {
                load();
                if (next == loader.end) {
                    cleanup();
                    return false;
                }
                return true;
            }

            @Override
            public SampleBatch next() {
                load();
                if (next == loader.end) {
                    cleanup();
                    throw new NoSuchElementException("No more batches");
                }

                Object item = next;
                next = null;
                return (SampleBatch) item;
            }
        };
    }

    private static void drainQueue(LoaderState loader) {
        Object item;
        while ((item = loader.queue.poll()) != null) {
            if (item instanceof SampleBatch batch) {
                batch.close();
            }
        }
    }

    private static void shutdown(LoaderState loader) {
        loader.cancel();
        loader.done.set(true);
        boolean enqueued = loader.queue.offer(loader.end);
        if (!enqueued) {
            logger.debug("End marker queue is full during shutdown");
        }
        drainQueue(loader);
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
            if (images[i] == null) {
                throw new IOException("Unsupported or unreadable image: " + sample.file);
            }
            target[i] = targetTransform.applyAsInt(sample.label);
        }
        Tensor data = transform.forward(images);
        try {
            Tensor labels = Tensor.of(target, images.length);
            return new SampleBatch(data, labels);
        } catch (RuntimeException ex) {
            data.close();
            throw ex;
        }
    }
}
