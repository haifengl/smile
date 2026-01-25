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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.datasets;

import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.io.Paths;

/**
 * Image segmentation dataset. The instances were drawn randomly from
 * a database of 7 outdoor images. The images were hand segmented
 * to create a classification for every pixel.
 *
 * @param train training data frame.
 * @param test testing data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record ImageSegmentation(DataFrame train, DataFrame test, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public ImageSegmentation() throws IOException, ParseException {
        this(Paths.getTestData("weka/segment-challenge.arff"),
             Paths.getTestData("weka/segment-test.arff"));
    }

    /**
     * Constructor.
     * @param trainDataPath the path to training data file.
     * @param testDataPath the path to testing data file.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public ImageSegmentation(Path trainDataPath, Path testDataPath) throws IOException, ParseException {
        this(Read.arff(trainDataPath), Read.arff(testDataPath), Formula.lhs("class"));
    }

    /**
     * Returns the train sample features.
     * @return the train sample features.
     */
    public double[][] x() {
        return formula.x(train).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the train sample class labels.
     * @return the train sample class labels.
     */
    public int[] y() {
        return formula.y(train).toIntArray();
    }

    /**
     * Returns the test sample features.
     * @return the test sample features.
     */
    public double[][] testx() {
        return formula.x(test).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the test sample class labels.
     * @return the test sample class labels.
     */
    public int[] testy() {
        return formula.y(test).toIntArray();
    }
}
