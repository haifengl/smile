/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import smile.data.Dataset;
import smile.data.Instance;
import smile.math.SparseArray;

/**
 * LIBSVM (and SVMLight) data reader. The format of libsvm file is:
 * <p>
 * &lt;label&gt; &lt;index1&gt;:&lt;value1&gt; &lt;index2&gt;:&lt;value2&gt; ...
 * <p>
 * where &lt;label&gt; is the target value of the training data.
 * For classification, it should be an integer which identifies a class
 * (multi-class classification is supported). For regression, it's any real
 * number. For one-class SVM, it's not used so can be any number.
 * &lt;index&gt; is an integer starting from 1, and &lt;value&gt;
 * is a real number. The indices must be in an ascending order. The labels in
 * the testing data file are only used to calculate accuracy or error. If they
 * are unknown, just fill this column with a number. 
 * 
 * @author Haifeng Li
 */
public class Libsvm {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Libsvm.class);

    /**
     * Constructor.
     */
    public Libsvm() {

    }

    /**
     * Reads a libsvm sparse dataset.
     * @param path the input file path.
     * @throws java.io.IOException
     */
    public Dataset<Instance<SparseArray>> read(Path path) throws IOException, ParseException {
        try (FileInputStream stream = new FileInputStream(path.toFile());
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Empty data source.");
            }

            // detect if the response variable is read or integer label.
            String token = line.trim().split("\\s+")[0];
            boolean classification = true;
            try {
                Integer.valueOf(token);
            } catch (NumberFormatException e) {
                try {
                    Double.valueOf(token);
                    classification = false;
                } catch (NumberFormatException ex) {
                    logger.error("Failed to parse {}", token, ex);
                    throw new NumberFormatException("Unrecognized response variable value: " + token);
                }
            }

            List<Instance<SparseArray>> data = new ArrayList<>();
            do {
                String[] tokens = line.trim().split("\\s+");
                String firstToken = tokens[0];

                SparseArray row = new SparseArray();
                for (int k = 1; k < tokens.length; k++) {
                    String[] pair = tokens[k].split(":");
                    if (pair.length != 2) {
                        throw new NumberFormatException("Invalid token: " + tokens[k]);
                    }

                    int j = Integer.parseInt(pair[0]) - 1;
                    double x = Double.parseDouble(pair[1]);
                    row.set(j, x);
                }

                if (classification) {
                    data.add(new Instance<SparseArray>() {
                        int y = Integer.parseInt(firstToken);
                        @Override
                        public SparseArray x() {
                            return row;
                        }

                        @Override
                        public int label() {
                            return y;
                        }
                    });
                } else {
                    data.add(new Instance<SparseArray>() {
                        double y = Double.parseDouble(firstToken);
                        @Override
                        public SparseArray x() {
                            return row;
                        }

                        @Override
                        public double y() {
                            return y;
                        }
                    });
                }

                line = reader.readLine();
            } while (line != null);

            return Dataset.of(data);
        }
    }    
}
