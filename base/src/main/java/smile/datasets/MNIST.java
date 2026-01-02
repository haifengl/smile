/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.datasets;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.IntStream;

import org.apache.commons.csv.CSVFormat;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.IntVector;
import smile.io.Read;
import smile.io.Paths;

/**
 * MNIST dataset. This is a large dataset of handwritten digits normalized to
 * fit into a 28x28 pixel bounding box and anti-aliased, which introduced
 * grayscale levels. The MNIST dataset contains 60,000 training images and
 * 10,000 testing images.
 *
 * @param data data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record MNIST(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public MNIST() throws IOException {
        this(Paths.getTestData("mnist/mnist2500_X.txt"),
             Paths.getTestData("mnist/mnist2500_labels.txt"));
    }

    /**
     * Constructor.
     * @param dataFilePath the path to data file.
     * @param labelFilePath the path to label file.
     * @throws IOException when fails to read the file.
     */
    public MNIST(Path dataFilePath, Path labelFilePath) throws IOException {
        this(dataFilePath.toString().endsWith(".txt") ?
                        loadText(dataFilePath, labelFilePath) :
                        loadBinary(dataFilePath, labelFilePath),
             Formula.lhs("class"));
    }

    private static DataFrame loadBinary(Path dataFilePath, Path labelFilePath) throws IOException {
        try (var dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(dataFilePath.toFile())));
             var labelInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(labelFilePath.toFile())))) {
            int magicNumber = dataInputStream.readInt();
            if (magicNumber != 2051) {
                throw new IOException("Invalid MNIST data file magic number: " + magicNumber);
            }
            int size = dataInputStream.readInt();
            int nrow = dataInputStream.readInt();
            int ncol = dataInputStream.readInt();
            int length = nrow * ncol;

            int labelMagicNumber = labelInputStream.readInt();
            if (labelMagicNumber != 2049) {
                throw new IOException("Invalid MNIST label file magic number: " + labelMagicNumber);
            }
            int labelSize = labelInputStream.readInt();
            if (labelSize != size) {
                throw new IOException("Data file and label file have different size: " + size + " vs " + labelSize);
            }

            float[][] data = new float[size][length];
            int[] y = new int[size];
            for (int i = 0; i < size; i++) {
                y[i] = labelInputStream.readUnsignedByte();
                var x = data[i];
                for (int r = 0, j = 0; r < nrow; r++) {
                    for (int c = 0; c < ncol; c++, j++) {
                        x[j] = dataInputStream.readUnsignedByte() / 255.0f;
                    }
                }
            }

            var df = DataFrame.of(data);
            return df.add(new IntVector("class", y));
        }
    }

    private static DataFrame loadText(Path dataFilePath, Path labelFilePath) throws IOException {
        StructType schema = new StructType(IntStream.range(1, 785)
                .mapToObj(i -> new StructField("V" + i, DataTypes.FloatType))
                .toList());
        CSVFormat format = CSVFormat.Builder.create().setDelimiter(' ').get();
        var data = Read.csv(dataFilePath, format, schema);
        int[] y = Read.csv(labelFilePath, format).column(0).toIntArray();
        return data.add(new IntVector("class", y));
    }

    /**
     * Returns the sample features.
     * @return the sample features.
     */
    public double[][] x() {
        return formula.x(data).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the class labels.
     * @return the class labels.
     */
    public int[] y() {
        return formula.y(data).toIntArray();
    }
}
