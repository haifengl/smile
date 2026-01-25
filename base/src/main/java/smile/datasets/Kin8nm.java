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
 * Robot arm simulation dataset. It is a realistic simulation of the forward
 * dynamics of an 8 link all-revolute robot arm, and the task is to predict
 * the distance of the end-effector from a target. The input are the angular
 * positions of the joints. Each instance represents a configuration of
 * angular positions of the joints and the resulting distance of the
 * end-effector from a target. The data is medium noisy and nonlinear.
 *
 * @param data data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record Kin8nm(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public Kin8nm() throws IOException, ParseException {
        this(Paths.getTestData("weka/regression/kin8nm.arff"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public Kin8nm(Path path) throws IOException, ParseException {
        this(Read.arff(path), Formula.lhs("y"));
    }

    /**
     * Returns the sample features.
     * @return the sample features.
     */
    public double[][] x() {
        return formula.x(data).toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Returns the target values.
     * @return the target values.
     */
    public double[] y() {
        return formula.y(data).toDoubleArray();
    }
}
