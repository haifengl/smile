/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.sequence;

/**
 * The dynamic programming table in CRF's Baum–Welch algorithm.
 */
class Trellis {
    public static class Cell {
        /**
         * Forward variable.
         */
        public double alpha = 1.0;
        /**
         * Backward variable.
         */
        public double beta = 1.0;
        /**
         * The residuals of conditional samples as regression tree training target.
         */
        public double[] residual;
        /**
         * Exp of potential function values F_i(x_t, s_t-1)
         */
        public double[] expf;

        /**
         * Constructor.
         * @param k the number of classes.
         */
        public Cell(int k) {
            residual = new double[k];
            expf = new double[k];
        }
    }

    /** The dynamic programming table. */
    public final Cell[][] table;

    /**
     * Constructor.
     * @param n the length of sequence.
     * @param k the number of classes.
     */
    public Trellis(int n, int k) {
        table = new Cell[n][k];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < k; j++) {
                table[i][j] = new Cell(k);
            }
        }
    }

    /**
     * Performs forward procedure on the trellis.
     */
    public void forward(double[] scaling) {
        int T = table.length; // length of sequence
        int k = table[0].length;

        Cell[] row = table[0];
        for (int i = 0; i < k; i++) {
            Cell ti = row[i];
            ti.alpha = ti.expf[0];
        }

        // Normalize alpha values since they increase quickly
        scaling[0] = 0.0;
        for (int i = 0; i < k; i++) {
            scaling[0] += row[i].alpha;
        }
        for (int i = 0; i < k; i++) {
            row[i].alpha /= scaling[0];
        }

        for (int t = 1; t < T; t++) {
            row = table[t];
            Cell[] row1 = table[t-1];
            for (int i = 0; i < k; i++) {
                Cell ti = row[i];
                ti.alpha = 0.0;
                for (int j = 0; j < k; j++) {
                    ti.alpha += ti.expf[j] * row1[j].alpha;
                }
            }

            // Normalize alpha values since they increase quickly
            scaling[t] = 0.0;
            for (int i = 0; i < k; i++) {
                scaling[t] += row[i].alpha;
            }
            for (int i = 0; i < k; i++) {
                row[i].alpha /= scaling[t];
            }
        }
    }

    /**
     * Performs backward procedure on the trellis.
     */
    public void backward() {
        int T = table.length - 1;
        int k = table[0].length;

        Cell[] row = table[T];
        for (int i = 0; i < k; i++) {
            row[i].beta = 1.0;
        }

        for (int t = T; t-- > 0;) {
            row = table[t];
            Cell[] row1 = table[t+1];
            for (int i = 0; i < k; i++) {
                Cell ti = row[i];
                ti.beta = 0.0;
                for (int j = 0; j < k; j++) {
                    ti.beta += row1[j].expf[i] * row1[j].beta;
                }
            }

            // Normalize beta values since they increase quickly
            double sum = 0.0;
            for (int i = 0; i < k; i++) {
                sum += row[i].beta;
            }
            for (int i = 0; i < k; i++) {
                row[i].beta /= sum;
            }
        }
    }

    /**
     * Calculates the gradients/residual based on results of forward-backward.
     */
    public void gradient(double[] scaling, int[] label) {
        int T = table.length;
        int k = table[0].length;

        // Finding the normalizer for our first 'column' in the matrix
        Cell[] row = table[0];
        double Z = 0.0;
        for (int i = 0; i < k; i++) {
            Z += row[i].expf[0] * row[i].beta;
        }

        for (int i = 0; i < k; i++) {
            if (label[0] == i) {
                row[i].residual[0] = 1 - row[i].expf[0] * row[i].beta / Z;
            } else {
                row[i].residual[0] = 0 - row[i].expf[0] * row[i].beta / Z;
            }
        }

        for (int t = 1; t < T; t++) {
            Z = 0.0;
            row = table[t];
            Cell[] row1 = table[t-1];
            for (int i = 0; i < k; i++) {
                Z += row[i].alpha * row[i].beta;
            }
            Z *= scaling[t];

            for (int i = 0; i < k; i++) {
                Cell ti = row[i];
                for (int j = 0; j < k; j++) {
                    if (label[t] == i && label[t - 1] == j) {
                        ti.residual[j] = 1 - ti.expf[j] * row1[j].alpha * ti.beta / Z;
                    } else {
                        ti.residual[j] = 0 - ti.expf[j] * row1[j].alpha * ti.beta / Z;
                    }
                }
            }
        }
    }
}
