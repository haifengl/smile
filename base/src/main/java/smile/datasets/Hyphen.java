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

import java.io.IOException;
import java.nio.file.Path;
import smile.data.BinarySparseSequenceDataset;
import smile.util.Paths;

/**
 * Hyphen sequence dataset.
 *
 * @param train training data.
 * @param test testing data.
 * @author Haifeng Li
 */
public record Hyphen(BinarySparseSequenceDataset train, BinarySparseSequenceDataset test) {
    /**
     * Constructor.
     * @throws IOException when fails to read the file.
     */
    public Hyphen() throws IOException {
        this(Paths.getTestData("sequence/sparse.hyphen.6.train"),
             Paths.getTestData("sequence/sparse.hyphen.6.test"));
    }

    /**
     * Constructor.
     * @param trainDataPath the path to training data file.
     * @param testDataPath the path to testing data file.
     * @throws IOException when fails to read the file.
     */
    public Hyphen(Path trainDataPath, Path testDataPath) throws IOException {
        this(BinarySparseSequenceDataset.load(trainDataPath), BinarySparseSequenceDataset.load(testDataPath));
    }
}
