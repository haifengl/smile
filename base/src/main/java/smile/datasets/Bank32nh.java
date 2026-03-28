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
 * Auto MPG dataset. A family of datasets synthetically generated from
 * a simulation of how bank-customers choose their banks. Tasks are
 * based on predicting the fraction of bank customers who leave the bank
 * because of full queues. The bank family of datasets are generated from
 * a simplistic simulator, which simulates the queues in a series of banks.
 * The simulator was constructed with the explicit purpose of generating
 * a family of datasets for DELVE. Customers come from several residential
 * areas, choose their preferred bank depending on distances and have tasks
 * of varying complexity, and various levels of patience. Each bank has
 * several queues, that open and close according to demand. The tellers have
 * various effectivities, and customers may change queue, if their patience
 * expires. In the rej prototasks, the object is to predict the rate of
 * rejections, i.e. the fraction of customers that are turned away from the
 * bank because all the open tellers have full queues.
 *
 * @param data data frame.
 * @param formula modeling formula.
 * @author Haifeng Li
 */
public record Bank32nh(DataFrame data, Formula formula) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public Bank32nh() throws IOException, ParseException {
        this(Paths.getTestData("weka/regression/bank32nh.arff"));
    }

    /**
     * Constructor.
     * @param path the data path.
     * @throws IOException when fails to read the file.
     * @throws ParseException when fails to parse the file.
     */
    public Bank32nh(Path path) throws IOException, ParseException {
        this(Read.arff(path), Formula.lhs("rej"));
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
