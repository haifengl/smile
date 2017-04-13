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
package smile.data.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.Scanner;

import smile.math.matrix.SparseMatrix;

/**
 * Harwell-Boeing column-compressed sparse matrix file parser. In Harwell-Boeing
 * column-compressed sparse matrix format, nonzero values are stored in an array
 * (top-to-bottom, then left-to-right-bottom). The row indices corresponding to
 * the values are also stored. Besides, a list of pointers are indexes where
 * each column starts. This class supports two formats for Harwell-Boeing files.
 * The simple one is organized as follows:
 * <p>
 * The first line contains three integers, which are the number of rows,
 * the number of columns, and the number of nonzero entries in the matrix.
 * <p>
 * Following the first line, there are m + 1 integers that are the indices of
 * columns, where m is the number of columns. Then there are n integers that
 * are the row indices of nonzero entries, where n is the number of nonzero
 * entries. Finally, there are n float numbers that are the values of nonzero
 * entries.
 * <p>
 * The second format is more complicated and powerful, called
 * Harwell-Boeing Exchange Format. For details, see
 * <a href="http://people.sc.fsu.edu/~jburkardt/data/hb/hb.html">http://people.sc.fsu.edu/~jburkardt/data/hb/hb.html</a>.
 * Note that our implementation supports only real-valued matrix and we ignore
 * the optional right hand side vectors.
 * 
 * @author Haifeng Li
 */
public class SparseMatrixParser {
    /**
     * Constructor.
     */
    public SparseMatrixParser() {
    }

    /**
     * Parse a Harwell-Boeing column-compressed sparse matrix dataset from given URI.
     * @throws java.io.IOException
     */
    public SparseMatrix parse(URI uri) throws IOException, ParseException {
        return parse(new File(uri));
    }

    /**
     * Parse a Harwell-Boeing column-compressed sparse matrix dataset from given file.
     * @throws java.io.IOException
     */
    public SparseMatrix parse(String path) throws IOException, ParseException {
        return parse(new File(path));
    }

    /**
     * Parse a Harwell-Boeing column-compressed sparse matrix dataset from given file.
     * @throws java.io.IOException
     */
    public SparseMatrix parse(File file) throws IOException, ParseException {
        return parse(new FileInputStream(file));
    }

    /**
     * Parse a Harwell-Boeing column-compressed sparse matrix dataset from an input stream.
     * @param stream the input stream of data.
     * @throws java.io.IOException
     */
    public SparseMatrix parse(InputStream stream) throws IOException, ParseException {
        int nrows = 0, ncols = 0, n = 0;
        int[] colIndex;
        int[] rowIndex;
        double[] data;

        try (Scanner scanner = new Scanner(stream)) {
            String line = scanner.nextLine();
            String[] tokens = line.split("\\s+");
            if (tokens.length == 3) {
                try {
                    nrows = Integer.parseInt(tokens[0]);
                    ncols = Integer.parseInt(tokens[1]);
                    n = Integer.parseInt(tokens[2]);
                } catch (Exception ex) {
                }
            }
            
            if (n == 0) {
                // Harwell-Boeing Exchange Format. We ignore first two lines.
                line = scanner.nextLine().trim();
                tokens = line.split("\\s+");
                int RHSCRD = Integer.parseInt(tokens[4]);

                line = scanner.nextLine().trim();
                if (!line.startsWith("R")) {
                    throw new UnsupportedOperationException("SparseMatrixParser supports only real-valued matrix.");
                }

                tokens = line.split("\\s+");
                nrows = Integer.parseInt(tokens[1]);
                ncols = Integer.parseInt(tokens[2]);
                n = Integer.parseInt(tokens[3]);

                line = scanner.nextLine();
                if (RHSCRD > 0) {
                    line = scanner.nextLine();
                }            
            }
            
            colIndex = new int[ncols + 1];
            rowIndex = new int[n];
            data = new double[n];
            for (int i = 0; i <= ncols; i++) {
                colIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < n; i++) {
                rowIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < n; i++) {
                data[i] = scanner.nextDouble();
            }
        }
        
        SparseMatrix matrix = new SparseMatrix(nrows, ncols, data, rowIndex, colIndex);
        return matrix;
    }                    
}
